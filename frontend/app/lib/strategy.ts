import { stockOrderHref } from "./stock-links";
export interface StrategyPlan { exchange: string; symbol: string; horizon: string; target: string; invalidation: string; reviewOn: string | null }
export const emptyPlan: StrategyPlan = { exchange: "", symbol: "", horizon: "", target: "", invalidation: "", reviewOn: null };
export const strategyExchanges = ["NSE", "BSE", "NASDAQ", "NYSE", "NYSE_ARCA", "AMEX", "CBOE"];
export function strategyOrderHref(id: string, plan: StrategyPlan) {
  return stockOrderHref(plan.exchange, plan.symbol, "BUY").replace("#", `&strategy=${encodeURIComponent(id)}#`);
}
export function localDay() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}
export function reviewDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(new Date(`${value}T12:00:00`));
}
