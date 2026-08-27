"use client";

import Image from "next/image";
import { useEffect, useMemo, useState } from "react";
import styles from "./portfolio.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type MarketRegion = "INDIA" | "UNITED_STATES";
type CurrencyCode = "INR" | "USD";

interface User {
  id: string;
  email: string;
  displayName: string;
}

interface StoredSession {
  accessToken: string;
  expiresInSeconds: number;
  user: User;
}

interface Holding {
  holdingId: string;
  symbol: string;
  name: string;
  quantity: number;
  blockedQuantity: number;
  averagePrice: number;
  currentPrice: number;
  marketValue: number;
  unrealizedProfitLoss: number;
  returnPercent: number;
  pricingStatus: string;
}

interface Portfolio {
  marketRegion: MarketRegion;
  currency: CurrencyCode;
  startingCapital: number;
  availableCash: number;
  blockedCash: number;
  investedValue: number;
  marketValue: number;
  realizedProfitLoss: number;
  unrealizedProfitLoss: number;
  totalProfitLoss: number;
  totalAccountValue: number;
  totalReturnPercent: number;
  dataStatus: string;
  valuedAt: string;
  holdings: Holding[];
}

interface PortfolioAllocation {
  exchange: string;
  symbol: string;
  name: string;
  marketValue: number;
  investedWeightPercent: number;
  accountWeightPercent: number;
  unrealizedProfitLoss: number;
  returnPercent: number;
  pricingStatus: string;
}

interface PortfolioAttribution {
  exchange: string;
  symbol: string;
  name: string;
  realizedProfitLoss: number;
  unrealizedProfitLoss: number;
  totalContribution: number;
  accountImpactPercent: number;
  contributionType: "GAIN" | "LOSS" | "FLAT";
}

interface PortfolioInsights {
  marketRegion: MarketRegion;
  currency: CurrencyCode;
  formulaVersion: string;
  status: string;
  confidence: string;
  dataCoveragePercent: number;
  cashValue: number;
  cashWeightPercent: number;
  investedWeightPercent: number;
  realizedProfitLoss: number;
  unrealizedProfitLoss: number;
  totalProfitLoss: number;
  allocations: PortfolioAllocation[];
  attributions: PortfolioAttribution[];
  observations: string[];
  disclaimer: string;
  valuedAt: string;
}

interface PortfolioBundle {
  marketRegion: MarketRegion;
  portfolio: Portfolio;
  insights: PortfolioInsights;
}

class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

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

async function request<T>(path: string, token?: string): Promise<T> {
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

async function refreshSession() {
  const response = await fetch(`${API_URL}/api/v1/auth/refresh`, {
    method: "POST",
    credentials: "include",
  });
  if (!response.ok) throw new ApiError("Please sign in again to view your portfolio.", response.status);
  const session = await response.json() as StoredSession;
  storeSession(session);
  return session;
}

async function loadPortfolios(session: StoredSession) {
  const load = async (region: MarketRegion, token: string): Promise<PortfolioBundle> => {
    const [portfolio, insights] = await Promise.all([
      request<Portfolio>(`/api/v1/portfolio?marketRegion=${region}`, token),
      request<PortfolioInsights>(`/api/v1/portfolio/insights?marketRegion=${region}`, token),
    ]);
    return { marketRegion: region, portfolio, insights };
  };
  try {
    return await Promise.all([
      load("INDIA", session.accessToken),
      load("UNITED_STATES", session.accessToken),
    ]);
  } catch (cause) {
    if (!(cause instanceof ApiError) || cause.status !== 401) throw cause;
    const refreshed = await refreshSession();
    return Promise.all([
      load("INDIA", refreshed.accessToken),
      load("UNITED_STATES", refreshed.accessToken),
    ]);
  }
}

function money(value: number, currency: CurrencyCode) {
  return new Intl.NumberFormat(currency === "INR" ? "en-IN" : "en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
  }).format(value);
}

function dateTime(value: string) {
  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export default function PortfolioPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [portfolios, setPortfolios] = useState<PortfolioBundle[]>([]);
  const [region, setRegion] = useState<MarketRegion>("INDIA");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const current = readSession() ?? await refreshSession();
        if (!active) return;
        setSession(current);
        const values = await loadPortfolios(current);
        if (active) setPortfolios(values);
      } catch (cause) {
        if (active) setError(cause instanceof Error ? cause.message : "Portfolio could not be loaded.");
      } finally {
        if (active) setLoading(false);
      }
    };
    void load();
    return () => { active = false; };
  }, []);

  const selected = useMemo(
    () => portfolios.find((item) => item.marketRegion === region),
    [portfolios, region],
  );
  const portfolio = selected?.portfolio;
  const insights = selected?.insights;

  return <main className={styles.shell}>
    <header className={styles.header}>
      <a className={styles.brand} href="/" aria-label="StoxSim dashboard">
        <Image src="/stoxsim-logo.png" alt="" width={42} height={42} priority />
        <span>Stox<span>Sim</span></span>
      </a>
      <a className={styles.back} href="/">Back to dashboard</a>
    </header>

    <section className={styles.heading}>
      <div>
        <span>PORTFOLIO</span>
        <h1>Your portfolio</h1>
        <p>{session ? `${session.user.displayName}, review your current holdings across both simulated markets.` : "Loading your current holdings…"}</p>
      </div>
      <div className={styles.marketTabs} role="group" aria-label="Portfolio market">
        <button type="button" className={region === "INDIA" ? styles.active : ""} aria-pressed={region === "INDIA"} onClick={() => setRegion("INDIA")}>India</button>
        <button type="button" className={region === "UNITED_STATES" ? styles.active : ""} aria-pressed={region === "UNITED_STATES"} onClick={() => setRegion("UNITED_STATES")}>United States</button>
      </div>
    </section>

    {error && <div className={styles.error}>{error} <a href="/">Return to sign in</a></div>}
    {loading && <div className={styles.loading}>Loading current holdings…</div>}

    {portfolio && <>
      <section className={styles.metrics}>
        <article><span>Account value</span><strong>{money(portfolio.totalAccountValue, portfolio.currency)}</strong><small>Started with {money(portfolio.startingCapital, portfolio.currency)}</small></article>
        <article><span>Available cash</span><strong>{money(portfolio.availableCash, portfolio.currency)}</strong><small>{money(portfolio.blockedCash, portfolio.currency)} blocked</small></article>
        <article><span>Invested value</span><strong>{money(portfolio.investedValue, portfolio.currency)}</strong><small>Market value {money(portfolio.marketValue, portfolio.currency)}</small></article>
        <article><span>Total P/L</span><strong className={portfolio.totalProfitLoss >= 0 ? styles.positive : styles.negative}>{money(portfolio.totalProfitLoss, portfolio.currency)}</strong><small>{portfolio.totalReturnPercent.toFixed(2)}% total return</small></article>
      </section>

      {insights && <section className={styles.analytics} aria-labelledby="portfolio-analytics-title">
        <div className={styles.analyticsHeader}>
          <div>
            <span>PORTFOLIO ANALYTICS</span>
            <h2 id="portfolio-analytics-title">Allocation and performance</h2>
          </div>
          <small>{insights.formulaVersion} · {insights.confidence} confidence · {insights.dataCoveragePercent.toFixed(0)}% pricing coverage</small>
        </div>

        <div className={styles.analyticsGrid}>
          <article className={styles.allocationCard}>
            <div className={styles.cardHeading}>
              <div><span>ACCOUNT MIX</span><h3>Where the account sits</h3></div>
              <strong>{money(portfolio.totalAccountValue, portfolio.currency)}</strong>
            </div>
            <div className={styles.accountBar} aria-label={`${insights.investedWeightPercent.toFixed(1)}% invested and ${insights.cashWeightPercent.toFixed(1)}% cash`}>
              <span style={{ width: `${Math.max(0, Math.min(100, insights.investedWeightPercent))}%` }} />
            </div>
            <div className={styles.accountLegend}>
              <div><i className={styles.investedDot} /><span>Invested</span><strong>{insights.investedWeightPercent.toFixed(1)}%</strong></div>
              <div><i className={styles.cashDot} /><span>Cash</span><strong>{insights.cashWeightPercent.toFixed(1)}%</strong></div>
            </div>

            <div className={styles.allocationList}>
              {insights.allocations.map((allocation) => <div className={styles.allocationRow} key={`${allocation.exchange}:${allocation.symbol}`}>
                <div className={styles.allocationIdentity}>
                  <strong>{allocation.symbol}</strong>
                  <small>{allocation.name}</small>
                </div>
                <div className={styles.positionBar}><span style={{ width: `${Math.max(0, Math.min(100, allocation.investedWeightPercent))}%` }} /></div>
                <div className={styles.allocationValue}>
                  <strong>{allocation.investedWeightPercent.toFixed(1)}%</strong>
                  <small>{money(allocation.marketValue, portfolio.currency)}</small>
                </div>
              </div>)}
              {!insights.allocations.length && <p className={styles.analyticsEmpty}>Your first executed buy will create an allocation breakdown.</p>}
            </div>
          </article>

          <article className={styles.attributionCard}>
            <div className={styles.cardHeading}>
              <div><span>PERFORMANCE ATTRIBUTION</span><h3>What shaped simulated P/L</h3></div>
              <strong className={insights.totalProfitLoss >= 0 ? styles.positive : styles.negative}>{money(insights.totalProfitLoss, portfolio.currency)}</strong>
            </div>
            <div className={styles.pnlSplit}>
              <div><span>Realized</span><strong className={insights.realizedProfitLoss >= 0 ? styles.positive : styles.negative}>{money(insights.realizedProfitLoss, portfolio.currency)}</strong></div>
              <div><span>Unrealized</span><strong className={insights.unrealizedProfitLoss >= 0 ? styles.positive : styles.negative}>{money(insights.unrealizedProfitLoss, portfolio.currency)}</strong></div>
            </div>
            <div className={styles.attributionList}>
              {insights.attributions.map((attribution) => <div className={styles.attributionRow} key={`${attribution.exchange}:${attribution.symbol}`}>
                <div>
                  <strong>{attribution.symbol}</strong>
                  <small>{attribution.name}</small>
                </div>
                <div>
                  <strong className={attribution.totalContribution >= 0 ? styles.positive : styles.negative}>{money(attribution.totalContribution, portfolio.currency)}</strong>
                  <small>{attribution.accountImpactPercent >= 0 ? "+" : ""}{attribution.accountImpactPercent.toFixed(2)}% of starting capital</small>
                </div>
              </div>)}
              {!insights.attributions.length && <p className={styles.analyticsEmpty}>No gain or loss contribution has been recorded yet.</p>}
            </div>
          </article>
        </div>

        <div className={styles.analyticsNotes}>
          <ul>{insights.observations.map((observation) => <li key={observation}>{observation}</li>)}</ul>
          <p>{insights.disclaimer}</p>
        </div>
      </section>}

      <section className={styles.holdings}>
        <div className={styles.sectionHeader}>
          <div><span>{region === "INDIA" ? "INDIA" : "UNITED STATES"}</span><h2>{region === "INDIA" ? "India holdings" : "United States holdings"}</h2></div>
          <small>{portfolio.holdings.length} current {portfolio.holdings.length === 1 ? "position" : "positions"} · {portfolio.dataStatus} · {dateTime(portfolio.valuedAt)}</small>
        </div>
        <div className={styles.tableWrap}>
          <table>
            <thead><tr><th>Stock</th><th>Quantity</th><th>Average cost</th><th>Current price</th><th>Market value</th><th>Unrealized P/L</th></tr></thead>
            <tbody>
              {portfolio.holdings.map((holding) => <tr key={holding.holdingId}>
                <td><strong>{holding.symbol}</strong><small>{holding.name}</small></td>
                <td>{holding.quantity}<small>{holding.blockedQuantity ? `${holding.blockedQuantity} blocked` : "Available"}</small></td>
                <td>{money(holding.averagePrice, portfolio.currency)}</td>
                <td>{money(holding.currentPrice, portfolio.currency)}<small>{holding.pricingStatus}</small></td>
                <td>{money(holding.marketValue, portfolio.currency)}</td>
                <td className={holding.unrealizedProfitLoss >= 0 ? styles.positive : styles.negative}>{money(holding.unrealizedProfitLoss, portfolio.currency)}<small>{holding.returnPercent.toFixed(2)}%</small></td>
              </tr>)}
              {!portfolio.holdings.length && <tr><td colSpan={6} className={styles.empty}>No current holdings in this market. Your first executed buy will appear here.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </>}
  </main>;
}
