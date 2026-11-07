import { expect, test } from "@playwright/test";
import { candlePattern, orderedCandles, type PriceCandle } from "../app/lib/candle-patterns";

const bar = (day: string, open: number, high: number, low: number, close: number): PriceCandle =>
  ({ timestamp: day + "T05:00:00Z", open, high, low, close });
const zone = "Asia/Kolkata";
const today = "2026-11-07";

test("educational markers require valid completed daily candles and adjacent sessions", () => {
  const red = bar("2026-11-04", 110, 111, 99, 100);
  const green = bar("2026-11-05", 98, 112, 97, 111);
  const bearish = bar("2026-11-06", 112, 114, 95, 97);
  expect(candlePattern(green, red, zone, today)).toBe("Bullish engulfing");
  expect(candlePattern(bearish, green, zone, today)).toBe("Bearish engulfing");
  expect(candlePattern(bearish, red, zone, today)).toBeNull();
  expect(candlePattern(green, red, zone, "2026-11-05")).toBeNull();
  expect(candlePattern(bar("2026-11-06", 100, 111, 90, 101), undefined, zone, today)).toBe("Doji");
  expect(candlePattern(bar("2026-11-06", 100, 102.5, 93, 102), undefined, zone, today)).toBe("Hammer");
  expect(orderedCandles([green, red, red, bar("2026-11-06", 10, 9, 3, 7)])).toEqual([red, green]);
});
