export function stockHref(exchange: string, symbol: string) {
  return `/stocks/${encodeURIComponent(exchange)}/${encodeURIComponent(symbol)}`;
}

export function stockOrderHref(exchange: string, symbol: string, side: "BUY" | "SELL") {
  return `/?${new URLSearchParams({ exchange, symbol, side })}#paper-order-ticket`;
}

export function readStockOrder(search: string) {
  const query = new URLSearchParams(search);
  const exchange = query.get("exchange"), symbol = query.get("symbol"), side = query.get("side");
  if (!exchange || !symbol || !/^[A-Z0-9&._-]{1,100}$/.test(symbol) || (side !== "BUY" && side !== "SELL")) return null;
  const marketRegion = ["NSE", "BSE"].includes(exchange) ? "INDIA"
    : ["NASDAQ", "NYSE", "NYSE_ARCA", "AMEX", "CBOE"].includes(exchange) ? "UNITED_STATES" : null;
  return marketRegion ? { exchange, symbol, side, marketRegion } as const : null;
}
