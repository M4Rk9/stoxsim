"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import styles from "./stock.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
type ChartRange = 1 | 3 | 6 | 12 | 36 | 60;
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

function monthsAgo(months: number) {
  const date = new Date();
  date.setUTCMonth(date.getUTCMonth() - months);
  return date.toISOString().slice(0, 10);
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
    const active = readSession();
    if (!active) {
      window.location.replace("/");
      return;
    }
    setSession(active);
  }, []);

  async function authorized<T>(path: string): Promise<T> {
    const active = readSession();
    if (!active) throw new ApiError("Please sign in again", 401);
    try {
      return await raw<T>(path, active.accessToken);
    } catch (cause) {
      if (!(cause instanceof ApiError) || cause.status !== 401) throw cause;
      const refreshed = await fetch(`${API_URL}/api/v1/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
      });
      if (!refreshed.ok) throw cause;
      const next = await refreshed.json() as StoredSession;
      window.sessionStorage.setItem("stoxsim-session", JSON.stringify(next));
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
    const to = new Date().toISOString().slice(0, 10);
    authorized<CandleSeries>(
      `/api/v1/instruments/${marketRegion}/${exchange}/${encodeURIComponent(symbol)}/candles?interval=ONE_DAY&from=${monthsAgo(range)}&to=${to}`,
    ).then((series) => {
      if (active) setCandles(series.candles ?? []);
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
    return <main className={styles.shell}><div className={styles.loading}>Loading verified market data…</div></main>;
  }

  if (error || !instrument || !quote) {
    return <main className={styles.shell}>
      <header className={styles.header}>
        <a className={styles.brand} href="/">Stox<span>Sim</span></a>
        <div className={styles.headerActions}><a href="/">← Dashboard</a></div>
      </header>
      <div className={styles.error}>{error || "This stock is unavailable."}</div>
    </main>;
  }

  const change = quote.previousClose == null ? undefined : quote.lastPrice - quote.previousClose;
  const changePercent = change == null || !quote.previousClose
    ? undefined
    : (change / quote.previousClose) * 100;
  const rising = (change ?? 0) >= 0;
  const currency = instrument.currency === "USD" ? "USD" : "INR";

  return <main className={styles.shell}>
    <header className={styles.header}>
      <a className={styles.brand} href="/">Stox<span>Sim</span></a>
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
          <span className={styles.status}>{quote.dataStatus} DATA</span>
        </div>
      </section>

      <section className={styles.grid}>
        <div className={styles.stack}>
          <article className={styles.card}>
            <div className={styles.cardHeader}>
              <div><h2>Price history</h2><p>{instrument.provider === "ALPACA" ? "Daily OHLC candles supplied by Alpaca" : "Daily OHLC candles supplied by Upstox"}</p></div>
              <div className={styles.rangeTabs}>
                {([1, 3, 6, 12, 36, 60] as const).map((months) => <button
                  type="button"
                  key={months}
                  className={range === months ? styles.active : ""}
                  onClick={() => setRange(months)}
                >{months >= 12 ? `${months / 12}Y` : `${months}M`}</button>)}
              </div>
            </div>
            <PriceChart candles={candles} loading={chartLoading} error={chartError} currency={currency} />
          </article>

          <article className={styles.card}>
            <div className={styles.cardHeader}>
              <div><h2>Company fundamentals</h2><p>Business profile and valuation context</p></div>
              {insights && <span className={styles.status}>{insights.status}</span>}
            </div>
            {insightsLoading && !insights && <div className={styles.empty}>Loading company fundamentals…</div>}
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
              <div><h2>Financial performance</h2><p>{marketRegion === "INDIA" ? "Consolidated figures in INR crore" : "US fundamentals provider integration is the next research milestone"}</p></div>
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
        </aside>
      </section>
    </div>
  </main>;
}

function PriceChart({ candles, loading, error, currency }: {
  candles: Candle[];
  loading: boolean;
  error: string;
  currency: "INR" | "USD";
}) {
  const ordered = useMemo(() => [...candles]
    .filter((candle) => Number.isFinite(candle.close))
    .sort((left, right) => new Date(left.timestamp).getTime() - new Date(right.timestamp).getTime()), [candles]);

  if (loading) return <div className={styles.empty}>Loading historical prices…</div>;
  if (!ordered.length) return <div className={styles.empty}>{error || "Historical prices are unavailable."}</div>;

  const width = 820;
  const height = 300;
  const left = 20;
  const right = 20;
  const top = 20;
  const bottom = 34;
  const minimum = Math.min(...ordered.map((candle) => candle.close));
  const maximum = Math.max(...ordered.map((candle) => candle.close));
  const spread = Math.max(maximum - minimum, maximum * 0.005, 1);
  const points = ordered.map((candle, index) => ({
    candle,
    x: left + (index / Math.max(ordered.length - 1, 1)) * (width - left - right),
    y: top + ((maximum - candle.close) / spread) * (height - top - bottom),
  }));
  const path = points.map((point, index) => `${index ? "L" : "M"}${point.x.toFixed(2)},${point.y.toFixed(2)}`).join(" ");
  const area = `${path} L${points[points.length - 1].x},${height - bottom} L${points[0].x},${height - bottom} Z`;
  const latest = ordered[ordered.length - 1];

  return <div className={styles.chart}>
    <div className={styles.chartReadout}>
      <div><strong>{money(latest.close, currency)}</strong><span>Latest historical close</span></div>
      <div>High {money(latest.high, currency)} · Low {money(latest.low, currency)}</div>
    </div>
    <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`${ordered.length} daily closing prices`}>
      {[0.25, 0.5, 0.75].map((ratio) => <line
        key={ratio}
        className={styles.gridLine}
        x1={left}
        x2={width - right}
        y1={top + ratio * (height - top - bottom)}
        y2={top + ratio * (height - top - bottom)}
      />)}
      <path d={area} className={styles.area} />
      <path d={path} className={styles.line} />
      <text className={styles.axis} x={left} y={height - 8}>
        {new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", year: "2-digit" }).format(new Date(ordered[0].timestamp))}
      </text>
      <text className={styles.axis} x={width - right} y={height - 8} textAnchor="end">
        {new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", year: "2-digit" }).format(new Date(latest.timestamp))}
      </text>
    </svg>
  </div>;
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
          {periods.map((value) => <td key={value}>{crore(byPeriod.get(value)?.value)}</td>)}
        </tr>;
      })}</tbody>
    </table>
  </div>;
}
