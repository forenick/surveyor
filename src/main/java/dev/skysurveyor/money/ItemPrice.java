package dev.skysurveyor.money;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

// saved in journal.jsonl, never reorder. null = none
// buyOrder = top buy order (insta-sell), sellOffer = lowest sell offer (insta-buy), npcSell from hypixels item list
@JsonAdapter(ItemPrice.Json.class)
public record ItemPrice(Double buyOrder, Double sellOffer, Double lowestBin, Double npcSell) {
	public ItemPrice(Double buyOrder, Double sellOffer, Double lowestBin) {
		this(buyOrder, sellOffer, lowestBin, null);
	}

	public boolean onBazaar() {
		return buyOrder != null || sellOffer != null;
	}

	// [buyOrder, sellOffer, lowestBin, npcSell] w/o trailing nulls, 1 per item per shaft so keep it small
	// 0.14.0 + 0.14.1 wrote {"buyOrder": ..} objects, still read
	static final class Json extends TypeAdapter<ItemPrice> {
		@Override
		public void write(JsonWriter out, ItemPrice price) throws IOException {
			if (price == null) {
				out.nullValue();
				return;
			}
			Double[] values = {price.buyOrder, price.sellOffer, price.lowestBin, price.npcSell};
			int count = values.length;
			while (count > 0 && values[count - 1] == null) count--;
			out.beginArray();
			for (int i = 0; i < count; i++) {
				Double value = values[i];
				if (value == null) {
					out.nullValue();
				} else if (value == Math.rint(value) && Math.abs(value) < 1e15) {
					out.value(value.longValue());
				} else {
					out.value(value.doubleValue());
				}
			}
			out.endArray();
		}

		@Override
		public ItemPrice read(JsonReader in) throws IOException {
			JsonToken token = in.peek();
			if (token == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			Double[] values = new Double[4];
			if (token == JsonToken.BEGIN_ARRAY) {
				in.beginArray();
				for (int i = 0; in.hasNext(); i++) {
					Double value = number(in);
					if (i < values.length) values[i] = value;
				}
				in.endArray();
			} else {
				in.beginObject();
				while (in.hasNext()) {
					int index = switch (in.nextName()) {
						case "buyOrder" -> 0;
						case "sellOffer" -> 1;
						case "lowestBin" -> 2;
						case "npcSell" -> 3;
						default -> -1;
					};
					Double value = number(in);
					if (index >= 0) values[index] = value;
				}
				in.endObject();
			}
			return new ItemPrice(values[0], values[1], values[2], values[3]);
		}

		private static Double number(JsonReader in) throws IOException {
			JsonToken token = in.peek();
			if (token == JsonToken.NUMBER || token == JsonToken.STRING) return in.nextDouble();
			in.skipValue();
			return null;
		}
	}
}
