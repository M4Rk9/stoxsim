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

export function candlePattern(candle: PriceCandle, previous: PriceCandle | undefined, zone: string, today: string) {
  const day = candleDay(candle.timestamp, zone);
  if (day >= today) return null;
  const body = Math.abs(candle.close - candle.open);
  const range = candle.high - candle.low;
  if (range <= 0) return null;
  if (body <= range * 0.1) return "Doji";
  const lowerWick = Math.min(candle.open, candle.close) - candle.low;
  const upperWick = candle.high - Math.max(candle.open, candle.close);
  if (body <= range / 3 && lowerWick >= 2 * body && upperWick <= body / 2) return "Hammer";
  if (!previous) return null;
  const preceding = new Date(candleDay(previous.timestamp, zone) + "T00:00:00Z");
  // Skip discontinuities in the feed. A holiday also breaks the pattern rather than implying a missing bar.
  preceding.setUTCDate(preceding.getUTCDate() + (preceding.getUTCDay() === 5 ? 3 : 1));
  if (preceding.toISOString().slice(0, 10) !== day) return null;
  if (candle.close > candle.open && previous.close < previous.open
      && candle.open <= previous.close && candle.close >= previous.open) return "Bullish engulfing";
  if (candle.close < candle.open && previous.close > previous.open
      && candle.open >= previous.close && candle.close <= previous.open) return "Bearish engulfing";
  return null;
}
