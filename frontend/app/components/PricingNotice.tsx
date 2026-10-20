export function priceSourceLabel(source?: string) {
  if (source === "EXTERNAL") return "Market price";
  if (source === "SIMULATED") return "Simulated fallback price";
  if (source === "COST_BASIS") return "Average cost fallback";
  return "Price source unconfirmed";
}

export default function PricingNotice({ source }: { source?: string }) {
  if (source === "EXTERNAL") return null;
  return <p role="status" style={{ lineHeight: 1.5, overflowWrap: "anywhere" }}>
    <strong>{priceSourceLabel(source)}.</strong>{" "}
    {source === "SIMULATED"
      ? "Market data is unavailable. This virtual price is used for paper trading and is excluded from observed performance history."
      : source === "COST_BASIS"
        ? "This holding is valued at its average purchase cost because a market price is unavailable."
        : "A verified market price is unavailable. This value is excluded from observed performance history."}
  </p>;
}

export function PortfolioPricingNotice({ holdings }: { holdings: { pricingSource?: string }[] }) {
  const affected = holdings.filter(p => p.pricingSource !== "EXTERNAL");
  if (!affected.length) return null;
  return <p role="status" style={{ lineHeight: 1.5, overflowWrap: "anywhere" }}>
    <strong>Estimated portfolio value.</strong> {affected.length} holding{affected.length === 1 ? " uses" : "s use"} fallback or unconfirmed prices.
    Account value and returns are estimates. These snapshots are excluded from observed performance history.
  </p>;
}
