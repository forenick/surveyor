package dev.shaftjournal.money;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shaftjournal.util.SafeWrite;

// io thread only (LiveMoney), only reads public price apis, sends nothing about the player
final class PriceFeed {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");
	// hypixel caches bazaar 60-300s, no point asking more often
	static final long BAZAAR_MAX_AGE = 5 * 60_000L;
	// display choice, rare drops + campfire only
	static final long BINS_MAX_AGE = 30 * 60_000L;
	static final long NAMES_MAX_AGE = 7 * 24 * 60 * 60_000L;
	static final long RETRY_AFTER = 60_000L;
	private static final URI BAZAAR = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
	// skyhanni + firmament use elite, hysky = skyblockers (research/03)
	private static final List<URI> BINS = List.of(
			URI.create("https://api.eliteskyblock.com/resources/auctions/neu?mode=smooth"),
			URI.create("https://hysky.de/api/auctions/lowestbins"));
	private static final URI ITEMS = URI.create("https://api.hypixel.net/v2/resources/skyblock/items");

	private final Path pricesFile;
	private final Path itemsFile;
	private final String userAgent;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL).build();

	private volatile PriceBook book = PriceBook.EMPTY;
	private volatile Map<String, String> names = Map.of();
	private long namesAt;
	private long failedAt = Long.MIN_VALUE / 2;

	PriceFeed(Path folder, String version) {
		this.pricesFile = folder.resolve("prices.json");
		this.itemsFile = folder.resolve("items.json");
		this.userAgent = "ShaftJournal/" + version;
	}

	PriceBook book() {
		return book;
	}

	Map<String, String> names() {
		return names;
	}

	void loadCache() {
		JsonObject prices = readJson(pricesFile);
		if (prices != null) {
			try {
				book = PriceBook.fromJson(prices);
			} catch (RuntimeException e) {
				LOGGER.warn("Ignoring broken {}: {}", pricesFile, e.toString());
			}
		}
		JsonObject cached = readJson(itemsFile);
		if (cached != null && cached.has("names") && cached.has("npc")) {
			try {
				Map<String, String> loaded = new HashMap<>();
				for (Map.Entry<String, JsonElement> e : cached.getAsJsonObject("names").entrySet()) {
					loaded.put(e.getKey(), e.getValue().getAsString());
				}
				book = book.withNpc(PriceBook.parseBins(cached.getAsJsonObject("npc")));
				names = Map.copyOf(loaded);
				namesAt = cached.has("at") ? cached.get("at").getAsLong() : 0;
			} catch (RuntimeException e) {
				LOGGER.warn("Ignoring broken {}: {}", itemsFile, e.toString());
			}
		}
	}

	boolean stale(long now) {
		if (now - failedAt < RETRY_AFTER) return false;
		return now - book.bazaarAt() > BAZAAR_MAX_AGE || now - book.binsAt() > BINS_MAX_AGE || now - namesAt > NAMES_MAX_AGE;
	}

	void fetchStale(long now) {
		boolean changed = false;
		if (now - book.bazaarAt() > BAZAAR_MAX_AGE) {
			try {
				book = book.withBazaar(nonEmpty(PriceBook.parseBazaar(get(BAZAAR)), "Bazaar"), System.currentTimeMillis());
				changed = true;
			} catch (IOException | RuntimeException e) {
				failed("Bazaar", e);
			}
		}
		if (now - book.binsAt() > BINS_MAX_AGE) {
			Exception last = null;
			for (URI uri : BINS) {
				try {
					book = book.withBins(nonEmpty(PriceBook.parseBins(get(uri)), uri.getHost()), System.currentTimeMillis());
					changed = true;
					last = null;
					break;
				} catch (IOException | RuntimeException e) {
					LOGGER.warn("Could not get lowest BIN prices from {}: {}", uri.getHost(), e.toString());
					last = e;
					if (e instanceof Interrupted) break;
				}
			}
			// only when every source failed, a working fallback shouldnt pause refreshes
			if (last != null) failed("lowest BIN", last);
		}
		if (changed) writeJson(pricesFile, book.toJson());
		if (now - namesAt > NAMES_MAX_AGE) {
			try {
				JsonObject items = get(ITEMS);
				Map<String, Double> npc = ItemIds.parseNpcPrices(items);
				Map<String, String> parsed = nonEmpty(ItemIds.parseHypixelItems(items), "item list");
				names = Map.copyOf(parsed);
				book = book.withNpc(npc);
				namesAt = System.currentTimeMillis();
				JsonObject json = new JsonObject();
				json.addProperty("at", namesAt);
				JsonObject nameMap = new JsonObject();
				names.forEach(nameMap::addProperty);
				json.add("names", nameMap);
				JsonObject npcMap = new JsonObject();
				npc.forEach(npcMap::addProperty);
				json.add("npc", npcMap);
				writeJson(itemsFile, json);
			} catch (IOException | RuntimeException e) {
				failed("item names and NPC", e);
			}
		}
	}

	// 200 w/ an error body parses to nothing, never let it wipe the cache
	private static <T extends Map<?, ?>> T nonEmpty(T parsed, String what) throws IOException {
		if (parsed.isEmpty()) throw new IOException(what + " sent no prices");
		return parsed;
	}

	private void failed(String what, Exception e) {
		failedAt = System.currentTimeMillis();
		if (e instanceof Interrupted) Thread.currentThread().interrupt();
		LOGGER.warn("Could not get {} prices: {}", what, e.toString());
	}

	private JsonObject get(URI uri) throws IOException {
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
				.header("User-Agent", userAgent).header("Accept-Encoding", "gzip").GET().build();
		HttpResponse<InputStream> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException e) {
			throw new Interrupted(e);
		}
		try (InputStream body = response.body()) {
			if (response.statusCode() != 200) throw new IOException(uri.getHost() + " answered " + response.statusCode());
			boolean gzip = response.headers().firstValue("Content-Encoding").filter("gzip"::equalsIgnoreCase).isPresent();
			InputStream in = gzip ? new GZIPInputStream(body) : body;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (JsonParseException | IllegalStateException e) {
			throw new IOException(uri.getHost() + " sent unreadable json", e);
		}
	}

	private static JsonObject readJson(Path file) {
		if (!Files.exists(file)) return null;
		try {
			return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not read {}: {}", file, e.toString());
			return null;
		}
	}

	private static void writeJson(Path file, JsonObject json) {
		try {
			SafeWrite.write(file, json.toString());
		} catch (IOException e) {
			LOGGER.warn("Could not write {}: {}", file, e.toString());
		}
	}

	private static final class Interrupted extends IOException {
		Interrupted(InterruptedException cause) {
			super("interrupted", cause);
		}
	}
}
