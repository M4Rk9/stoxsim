export interface PriceCandle {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume?: number | null;
}

const dayFormatters = new Map<string, Intl.DateTimeFormat>();
export function candleDay(timestamp: string | Date, zone: string) {
  let formatter = dayFormatters.get(zone);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat("sv-SE", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" });
    dayFormatters.set(zone, formatter);
  }
  return formatter.format(new Date(timestamp));
}

export function orderedCandles(candles: PriceCandle[]) {
  const byTime = new Map<number, PriceCandle>();
  for (const candle of candles) {
    const time = Date.parse(candle.timestamp);
    const { open, high, low, close } = candle;
    if (Number.isFinite(time) && [open, high, low, close].every(value => Number.isFinite(value) && value > 0)
        && high >= Math.max(open, close) && low <= Math.min(open, close)) byTime.set(time, candle);
  }
  return [...byTime].sort(([left], [right]) => left - right).map(([, candle]) => candle);
}
