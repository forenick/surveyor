package dev.shaftjournal.money;

// saved in journal.jsonl, never rename. null = none
// buyOrder = top buy order (insta-sell), sellOffer = lowest sell offer (insta-buy), npcSell from hypixels item list
public record ItemPrice(Double buyOrder, Double sellOffer, Double lowestBin, Double npcSell) {
	public ItemPrice(Double buyOrder, Double sellOffer, Double lowestBin) {
		this(buyOrder, sellOffer, lowestBin, null);
	}

	public boolean onBazaar() {
		return buyOrder != null || sellOffer != null;
	}
}
