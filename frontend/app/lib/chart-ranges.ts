import { candleDay, orderedCandles, type PriceCandle } from "./candle-patterns";
export const chartRanges = ["1D", "1W", 1, 6, 12, 36, 60] as const;
export type ChartRange = typeof chartRanges[number];
export const rangeLabel = (range: ChartRange) => typeof range === "string" ? range : range >= 12 ? `${range / 12}Y` : `${range}M`;
export const chartInterval = (range: ChartRange) => range === "1D" ? "ONE_MINUTE" : range === "1W" ? "FIFTEEN_MINUTES" : range >= 36 ? "ONE_WEEK" : "ONE_DAY";
export const intervalLabel = (range: ChartRange) => range === "1D" ? "1-minute" : range === "1W" ? "15-minute" : range >= 36 ? "Weekly" : "Daily";
export const exchangeZone = (exchange: string) => ["NSE", "BSE"].includes(exchange) ? "Asia/Kolkata" : "America/New_York";
export function chartQuery(range: ChartRange, exchange: string, now = new Date()) {
  const to = candleDay(now, exchangeZone(exchange));
  const from = new Date(`${to}T00:00:00Z`);
  if (typeof range === "string") from.setUTCDate(from.getUTCDate() - (range === "1D" ? 10 : 7));
  else {
    const day = from.getUTCDate();
    from.setUTCDate(1); from.setUTCMonth(from.getUTCMonth() - range);
    const lastDay = new Date(Date.UTC(from.getUTCFullYear(), from.getUTCMonth() + 1, 0)).getUTCDate();
    from.setUTCDate(Math.min(day, lastDay));
  }
  return new URLSearchParams({ interval: chartInterval(range), from: from.toISOString().slice(0, 10), to }).toString();
}
// 1D is the latest available exchange session. Never synthesize absent observations.
export function visibleCandles<T extends PriceCandle>(candles: T[], range: ChartRange, exchange: string): T[] {
  const bars = orderedCandles(candles) as T[];
  if (range !== "1D" || !bars.length) return bars;
  const zone = exchangeZone(exchange), latest = candleDay(bars[bars.length - 1].timestamp, zone);
  return bars.filter(bar => candleDay(bar.timestamp, zone) === latest);
}
