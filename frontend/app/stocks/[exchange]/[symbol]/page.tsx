"use client";
import Brand from "../../../components/Brand";

import PriceHistoryChart from "../../../components/PriceHistoryChart";

import PricingNotice from "../../../components/PricingNotice";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { recordProductActivity } from "../../../lib/product-activity";
import { stockOrderHref } from "../../../lib/stock-links";
import { financialValue } from "../../../lib/financial-units";
import styles from "./stock.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
import { chartRanges, rangeLabel, chartQuery, intervalLabel, visibleCandles, type ChartRange } from "../../../lib/chart-ranges";
type FinancialPeriod = "quarterly" | "yearly";

interface StoredSession {
  accessToken: string;
  expiresInSeconds: number;
  user: unknown;
}

interface Instrument {
  provider: string;
  currency: string;
  tradingSymbol: string;
  name: string;
  exchange: string;
  instrumentType: string;
}

interface Quote {
  lastPrice: number;
  open?: number;
  high?: number;
  low?: number;
  previousClose?: number;
  volume?: number;
  pricingSource?: string;
  dataStatus: string;
  exchangeTimestamp?: string;
}

interface Candle {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume?: number;
}

interface CandleSeries {
  candles: Candle[];
}

interface CompanyProfile {
  description?: string;
  sector?: string;
  sectorMarketCapInrCrore?: number;
  sectorMarketCapInrFormatted?: string;
}

interface FundamentalRatio {
  name: string;
  companyValue?: string;
  sectorValue?: string;
}

interface FinancialHistoryPoint {
  period: string;
  value: number;
  change?: string;
}

interface FinancialMetric {
  category: string;
  history: FinancialHistoryPoint[];
}

interface FinancialPerformance {
  unitsIn?: string;
  statementType?: string;
  timePeriod?: string;
  metrics: FinancialMetric[];
}

interface StockInsights {
  provider: string;
  asOf: string;
  status: "AVAILABLE" | "PARTIAL" | "UNAVAILABLE";
  profile?: CompanyProfile;
  ratios: FundamentalRatio[];
  financials?: FinancialPerformance;
  message?: string;
}

class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

const money = (value?: number, currency: "INR" | "USD" = "INR") => value == null || !Number.isFinite(value)
    ? "—"
    : new Intl.NumberFormat(currency === "INR" ? "en-IN" : "en-US", {
        style: "currency",
        currency,
        maximumFractionDigits: 2,
      }).format(value);

const number = (value?: number) => value == null || !Number.isFinite(value)
  ? "—"
  : new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(value);

const compact = (value?: number) => value == null || !Number.isFinite(value)
  ? "—"
  : new Intl.NumberFormat("en-IN", {
      notation: "compact",
      maximumFractionDigits: 2,
    }).format(value);

const crore = (value?: number) => value == null || !Number.isFinite(value)
  ? "—"
  : `₹${number(value)} Cr`;

function readSession(): StoredSession | null {
  try {
    const value = window.sessionStorage.getItem("stoxsim-session");
    return value ? JSON.parse(value) as StoredSession : null;
  } catch {
    return null;
  }
}

function storeSession(session: StoredSession) {
  window.sessionStorage.setItem("stoxsim-session", JSON.stringify(session));
}

async function raw<T>(path: string, token?: string): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    credentials: "include",
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new ApiError(payload?.message ?? `Request failed with status ${response.status}`, response.status);
  }
  return response.json() as Promise<T>;
}

let refreshInFlight: Promise<StoredSession> | null = null;

async function requestSessionRefresh(): Promise<StoredSession> {
  const response = await fetch(`${API_URL}/api/v1/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
  });
  if (!response.ok) {
    throw new ApiError("Please sign in again", response.status);
  }
  const session = await response.json() as StoredSession;
  storeSession(session);
  return session;
}

function refreshSession(): Promise<StoredSession> {
  if (refreshInFlight) return refreshInFlight;

  const request = () => requestSessionRefresh();
  const coordinate = async (): Promise<StoredSession> => {
    if (typeof navigator !== "undefined" && "locks" in navigator) {
      return await navigator.locks.request(
        "stoxsim-session-refresh",
        { mode: "exclusive" },
        request,
      );
    }
    return request();
  };
  const pending = coordinate();

  const tracked = pending.finally(() => {
    refreshInFlight = null;
  });
  refreshInFlight = tracked;
  return tracked;
}

export default function StockPage() {
  const params = useParams<{ exchange: string; symbol: string }>();
  const exchange = decodeURIComponent(params.exchange).toUpperCase();
  const symbol = decodeURIComponent(params.symbol).toUpperCase();
  const marketRegion = exchange === "NSE" || exchange === "BSE" ? "INDIA" : "UNITED_STATES";
  const [session, setSession] = useState<StoredSession | null>(null);
  const [instrument, setInstrument] = useState<Instrument | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [candles, setCandles] = useState<Candle[]>([]);
  const [insights, setInsights] = useState<StockInsights | null>(null);
  const [range, setRange] = useState<ChartRange>(12);
  const [period, setPeriod] = useState<FinancialPeriod>("quarterly");
  const [loading, setLoading] = useState(true);
  const [chartLoading, setChartLoading] = useState(true);
  const [insightsLoading, setInsightsLoading] = useState(true);
  const [error, setError] = useState("");
  const [chartError, setChartError] = useState("");

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      try {
        const active = readSession() ?? await refreshSession();
        if (mounted) setSession(active);
      } catch {
        if (mounted) window.location.replace("/");
      }
    };
    void load();
    return () => {
      mounted = false;
    };
  }, []);

  async function authorized<T>(path: string): Promise<T> {
    const active = readSession();
    if (!active) throw new ApiError("Please sign in again", 401);
    try {
      return await raw<T>(path, active.accessToken);
    } catch (cause) {
      if (!(cause instanceof ApiError) || cause.status !== 401) throw cause;
      const next = await refreshSession().catch(() => {
        throw cause;
      });
      setSession(next);
      return raw<T>(path, next.accessToken);
    }
  }

  useEffect(() => {
    if (!session) return;
    let active = true;
    setLoading(true);
    setError("");
    Promise.all([
      authorized<Instrument>(`/api/v1/instruments/${marketRegion}/${exchange}/${encodeURIComponent(symbol)}`),
      authorized<Quote>(`/api/v1/instruments/${marketRegion}/${exchange}/${encodeURIComponent(symbol)}/quote`),
    ]).then(([nextInstrument, nextQuote]) => {
      if (!active) return;
      setInstrument(nextInstrument);
      setQuote(nextQuote);
      recordProductActivity("STOCK_OPENED");
    }).catch((cause) => {
      if (active) setError(cause instanceof Error ? cause.message : "Stock could not be loaded");
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
    };
    // The route identifiers and authenticated session are the data dependencies.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.accessToken, exchange, symbol]);

  useEffect(() => {
    if (!session) return;
    let active = true;
    setChartLoading(true);
    setChartError("");
    authorized<CandleSeries>(
      `/api/v1/instruments/${marketRegion}/${exchange}/${encodeURIComponent(symbol)}/candles?${chartQuery(range, exchange)}`,
    ).then((series) => {
      if (active) setCandles(visibleCandles(series.candles ?? [], range, exchange));
    }).catch((cause) => {
      if (!active) return;
      setCandles([]);
      setChartError(cause instanceof Error ? cause.message : "Historical prices are unavailable");
    }).finally(() => {
      if (active) setChartLoading(false);
    });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.accessToken, exchange, symbol, range]);

  useEffect(() => {
    if (!session) return;
    let active = true;
    setInsightsLoading(true);
    authorized<StockInsights>(
      `/api/v1/instruments/${marketRegion}/${exchange}/${encodeURIComponent(symbol)}/insights?timePeriod=${period}`,
    ).then((next) => {
      if (active) setInsights(next);
    }).catch(() => {
      if (active) setInsights(null);
    }).finally(() => {
      if (active) setInsightsLoading(false);
    });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.accessToken, exchange, symbol, period]);

  if (!session || loading) {
    return <main id="main-content" tabIndex={-1} className={styles.shell}><div className={styles.loading} role="status" aria-live="polite">Loading verified market data…</div></main>;
  }

  if (error || !instrument || !quote) {
    return <main id="main-content" tabIndex={-1} className={styles.shell}>
      <header className={styles.header}>
        <Brand />
        <div className={styles.headerActions}><a href="/">← Dashboard</a></div>
      </header>
      <div className={styles.error} role="alert">{error || "This stock is unavailable."}</div>
    </main>;
  }

  const change = quote.previousClose == null ? undefined : quote.lastPrice - quote.previousClose;
  const changePercent = change == null || !quote.previousClose
    ? undefined
    : (change / quote.previousClose) * 100;
  const rising = (change ?? 0) >= 0;
  const currency = instrument.currency === "USD" ? "USD" : "INR";

  return <main id="main-content" tabIndex={-1} className={styles.shell}>
    <header className={styles.header}>
      <Brand />
      <div className={styles.headerActions}>
        <a href="/settings">Account settings</a>
        <a href="/">← Dashboard</a>
      </div>
    </header>

    <div className={styles.main}>
      <section className={styles.hero}>
        <div className={styles.identity}>
          <span className={styles.monogram}>{symbol.slice(0, 2)}</span>
          <div>
            <span className={styles.kicker}>{exchange} · {instrument.instrumentType}</span>
            <h1>{symbol}</h1>
            <p>{instrument.name}</p>
          </div>
        </div>
        <div className={styles.priceBlock}>
          <strong>{money(quote.lastPrice, currency)}</strong>
          {change != null && <div className={`${styles.move} ${rising ? styles.positive : styles.negative}`}>
            {rising ? "+" : ""}{money(change, currency)} ({rising ? "+" : ""}{number(changePercent)}%)
          </div>}
          <span className={styles.status}>{quote.dataStatus}</span>
        </div>
      </section>

      <PricingNotice source={quote.pricingSource} />
      <section className={styles.grid}>
        <div className={styles.stack}>
          <article className={styles.card}>
            <div className={styles.cardHeader}>
              <div><h2>Price history</h2><p>{`${intervalLabel(range)} prices · inspect the chart to explore`}</p></div>
              <div className={styles.rangeTabs}>
                {chartRanges.map((months) => <button
                  type="button"
                  key={months}
                  className={range === months ? styles.active : ""}
                  onClick={() => setRange(months)}
                >{rangeLabel(months)}</button>)}
              </div>
            </div>
            <PriceHistoryChart interval={intervalLabel(range)} key={`${exchange}/${symbol}`} candles={candles} loading={chartLoading} error={chartError} currency={currency} exchange={exchange} provider={instrument.provider === "ALPACA" ? "Alpaca" : "Upstox"} />
          </article>

          <article className={styles.card}>
            <div className={styles.cardHeader}>
              <div><h2>Company fundamentals</h2><p>Business profile and valuation context</p></div>
              {insights && <span className={styles.status}>{insights.status}</span>}
            </div>
            {insightsLoading && !insights && <div className={styles.empty} role="status" aria-live="polite">Loading company fundamentals…</div>}
            {!insightsLoading && !insights && <div className={styles.empty}>Company fundamentals are unavailable.</div>}
            {insights && <>
              {insights.message && <p className={styles.notice}>{insights.message}</p>}
              <div className={styles.profile}>
                <div className={styles.profileGrid}>
                  <div><span>Sector</span><strong>{insights.profile?.sector ?? "—"}</strong></div>
                  <div><span>Sector market cap</span><strong>{insights.profile?.sectorMarketCapInrFormatted ?? crore(insights.profile?.sectorMarketCapInrCrore)}</strong></div>
                </div>
                {insights.profile?.description && <p className={styles.description}>{insights.profile.description}</p>}
              </div>
              <div className={styles.ratios}>
                {insights.ratios.map((ratio) => <div className={styles.ratio} key={ratio.name}>
                  <span>{ratio.name}</span>
                  <strong>{ratio.companyValue ?? "—"}</strong>
                  <small>Sector {ratio.sectorValue ?? "—"}</small>
                </div>)}
                {!insights.ratios.length && <div className={styles.empty}>Valuation ratios are unavailable.</div>}
              </div>
            </>}
          </article>

          <article className={styles.card}>
            <div className={styles.cardHeader}>
              <div><h2>Financial performance</h2><p>{insights?.financials?.unitsIn ? `Figures in ${insights.financials.unitsIn}` : "Financial units unavailable"}{insights?.financials?.statementType ? ` · ${insights.financials.statementType}` : ""}</p></div>
              <div className={styles.rangeTabs}>
                <button type="button" className={period === "quarterly" ? styles.active : ""} onClick={() => setPeriod("quarterly")}>Quarterly</button>
                <button type="button" className={period === "yearly" ? styles.active : ""} onClick={() => setPeriod("yearly")}>Yearly</button>
              </div>
            </div>
            <FinancialTable financials={insights?.financials} loading={insightsLoading} />
          </article>
        </div>

        <aside className={styles.stack}>
          <article className={styles.card}>
            <div className={styles.cardHeader}><div><h2>Market snapshot</h2><p>Latest verified trading values</p></div></div>
            <div className={styles.stats}>
              <div><span>Open</span><strong>{money(quote.open, currency)}</strong></div>
              <div><span>Previous close</span><strong>{money(quote.previousClose, currency)}</strong></div>
              <div><span>Day high</span><strong>{money(quote.high, currency)}</strong></div>
              <div><span>Day low</span><strong>{money(quote.low, currency)}</strong></div>
              <div><span>Volume</span><strong>{compact(quote.volume)}</strong></div>
              <div><span>Exchange</span><strong>{exchange}</strong></div>
            </div>
          </article>

          <article className={styles.card}>
            <div className={styles.cardHeader}><div><h2>Research note</h2><p>Use this page before placing a simulated order</p></div></div>
            <div className={styles.profile}>
              <p className={styles.description}>
                Compare price trend, valuation ratios and financial performance. StoxSim is an educational simulator; this information is not investment advice.
              </p>
            </div>
          </article>
          <article className={styles.card}>
            <div className={styles.cardHeader}><div><h2>Practise a trade</h2><p>Open a ticket for this exact listing</p></div></div>
      <div className={styles.tradeActions} aria-label="Virtual trading">
        {["EQUITY", "ETF"].includes(instrument.instrumentType) ? <>
          <a href={`/journal?${new URLSearchParams({ exchange: instrument.exchange, symbol: instrument.tradingSymbol })}`}>Plan a strategy</a>
          <a href={stockOrderHref(instrument.exchange, instrument.tradingSymbol, "BUY")}>Virtual Buy</a>
          <a href={stockOrderHref(instrument.exchange, instrument.tradingSymbol, "SELL")}>Virtual Sell</a>
          <span>Virtual orders only</span>
        </> : <span>This listing is unavailable for equity practice orders.</span>}
      </div>
          </article>
        </aside>
      </section>
    </div>
  </main>;
}

function FinancialTable({ financials, loading }: {
  financials?: FinancialPerformance;
  loading: boolean;
}) {
  if (loading && !financials) return <div className={styles.empty}>Loading financial statements…</div>;
  if (!financials?.metrics.length) return <div className={styles.empty}>Financial history is unavailable for this stock.</div>;

  const metrics = financials.metrics.slice(0, 8);
  const periods = metrics.reduce<string[]>((current, metric) => {
    for (const point of metric.history) {
      if (!current.includes(point.period)) current.push(point.period);
    }
    return current;
  }, []).slice(0, 6);

  return <div className={styles.financials}>
    <table>
      <thead><tr><th>Metric</th>{periods.map((value) => <th key={value}>{value}</th>)}</tr></thead>
      <tbody>{metrics.map((metric) => {
        const byPeriod = new Map(metric.history.map((point) => [point.period, point]));
        return <tr key={metric.category}>
          <td>{metric.category.replaceAll("_", " ")}</td>
          {periods.map((value) => <td key={value}>{financialValue(byPeriod.get(value)?.value, financials?.unitsIn)}</td>)}
        </tr>;
      })}</tbody>
    </table>
  </div>;
}
