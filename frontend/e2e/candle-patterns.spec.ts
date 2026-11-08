import { expect, test } from "@playwright/test";
import { orderedCandles } from "../app/lib/candle-patterns";
import { chartQuery, chartRanges, rangeLabel, visibleCandles } from "../app/lib/chart-ranges";
const bar = (timestamp: string, close = 100) => ({ timestamp, open:100, high:110, low:90, close });
test("chart data rejects invalid bars and deduplicates without filling gaps", () => {
  const first = bar("2026-11-05T04:00:00Z"), last = bar("2026-11-09T04:00:00Z");
  expect(orderedCandles([last, first, first, bar("invalid"), bar(last.timestamp, NaN), {...last, high:95}])).toEqual([first,last]);
});
test("ranges use bounded provider intervals and exchange dates", () => {
  const now = new Date("2026-11-05T23:00:00Z");
  expect(chartRanges.map(rangeLabel)).toEqual(["1D","1W","1M","6M","1Y","3Y","5Y"]);
  expect(chartQuery("1D","BSE",now)).toBe("interval=ONE_MINUTE&from=2026-10-27&to=2026-11-06");
  expect(chartQuery("1W","NSE",now)).toBe("interval=FIFTEEN_MINUTES&from=2026-10-30&to=2026-11-06");
  expect(chartQuery(60,"NSE",now)).toContain("interval=ONE_WEEK&from=2021-11-06");
  expect(chartQuery(1,"NSE",new Date("2024-03-31T12:00:00Z"))).toContain("from=2024-02-29");
  expect(chartQuery("1D","NASDAQ",now)).toContain("to=2026-11-05");
});
test("one day keeps only the latest available exchange session", () => {
  const before = bar("2026-11-05T18:00:00Z"), after = bar("2026-11-05T18:45:00Z");
  expect(visibleCandles([after,before],"1D","BSE")).toEqual([after]);
  expect(visibleCandles([],"1D","BSE")).toEqual([]);
});
