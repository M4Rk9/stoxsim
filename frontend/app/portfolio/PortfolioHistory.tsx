"use client";

import { useEffect, useState } from "react";
import { authenticated } from "../campus/client";
import styles from "./portfolio.module.css";

interface Point { day: string; observedAt: string; equity: number | null; quality: string; portfolioIndex: number | null; benchmarkIndex: number | null }
interface History { currency: string; tier: string; days: number; benchmark: string; status: string; missingSessions: number; cashFlowBreaks: number; returnPercent: number | null; benchmarkReturnPercent: number | null; maximumDrawdownPercent: number | null; risk: { volatilityPercent: number | null; beta: number | null; correlation: number | null }; points: Point[] }
const number = (value: number | null | undefined, suffix = "") => value == null ? "—" : `${value.toFixed(2)}${suffix}`;

export default function PortfolioHistory({ accountId }: { accountId: string }) {
  const [days, setDays] = useState(30);
  const [data, setData] = useState<History | null>(null);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setData(null); setError("");
    authenticated<History>(`accounts/${accountId}/portfolio/history?days=${days}`)
      .then(value => { if (active) setData(value); })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "History could not be loaded."); });
    return () => { active = false; };
  }, [accountId, days, reload]);
  const normalized = data?.tier !== "FREE";
  const values = data?.points.flatMap(p => [normalized ? p.portfolioIndex : p.equity, ...(normalized ? [p.benchmarkIndex] : [])].filter((n): n is number => n != null)) ?? [];
  const min = Math.min(...values), max = Math.max(...values);
  const y = (v: number) => 170 - (v - min) / Math.max(max - min, 1) * 150;
  const x = (i: number) => 20 + i / Math.max((data?.points.length ?? 1) - 1, 1) * 660;
  const line = (benchmark: boolean) => data?.points.map((p, i) => {
    const value = benchmark ? p.benchmarkIndex : normalized ? p.portfolioIndex : p.equity;
    return value == null ? "" : `${x(i)},${y(value)}`;
  }).filter(Boolean).join(" ");
  return <section className={styles.analytics} aria-labelledby="history-heading">
    <div className={styles.analyticsHeader}><div><span>PERFORMANCE OVER TIME</span><h2 id="history-heading">Portfolio history</h2></div>
      <label>History period <select aria-label="History period" value={days} onChange={e => setDays(Number(e.target.value))}>
        <option value={30}>30 days</option><option value={90}>90 days · Plus</option><option value={365}>1 year · Plus</option>
      </select></label>
      <button type="button" onClick={() => setReload(v => v + 1)}>Refresh history</button>
    </div>
    {error && <p role="alert">{error}</p>}
    {!data && !error && <p role="status">Loading portfolio history…</p>}
    {data && <>
      <p>Daily observations in {data.currency}. Collection begins after deployment; earlier performance is not reconstructed.</p>
      {data.points.length === 0 ? <p role="status">Your history is collecting. The first observation appears after the next trading session’s nightly collection.</p> : <p>{data.points.length} observations · Last observed {data.points.at(-1)?.day} · {data.status.replaceAll("_", " ").toLowerCase()}</p>}
      {(data.missingSessions > 0 || data.cashFlowBreaks > 0) && <p role="status">Performance metrics are withheld: {data.missingSessions} missing or unpriced observations and {data.cashFlowBreaks} cash adjustment breaks. No prices are filled in.</p>}
      {data.tier === "FREE" ? <p>Plus unlocks longer history, benchmark comparisons and drawdown. Pro adds volatility, beta and correlation. <a href="/settings#plan">View your plan</a>.</p> : <>
        <div className={styles.metrics}>
          <article><span>Observed return</span><strong>{number(data.returnPercent, "%")}</strong></article>
          <article><span>{data.benchmark}</span><strong>{number(data.benchmarkReturnPercent, "%")}</strong></article>
          <article><span>Maximum drawdown</span><strong>{number(data.maximumDrawdownPercent, "%")}</strong></article>
          <article><span>Annualized volatility · Pro</span><strong>{number(data.risk.volatilityPercent, "%")}</strong></article>
          <article><span>Beta · Pro</span><strong>{number(data.risk.beta)}</strong></article>
          <article><span>Correlation · Pro</span><strong>{number(data.risk.correlation)}</strong></article>
        </div>
        <p>Risk metrics require 20 consecutive valid session returns. Volatility uses 252 sessions per year. Benchmark prices exclude dividends; missing benchmark data leaves comparison metrics unavailable.</p>
      </>}
      {values.length > 0 && <figure>
        <svg viewBox="0 0 700 195" role="img" aria-label={normalized ? "Portfolio and benchmark growth, starting at 100" : `Observed portfolio value in ${data.currency}`} style={{ width: "100%", maxHeight: 250 }}>
          <line x1="20" x2="680" y1="175" y2="175" stroke="currentColor" opacity="0.25" />
          {data.missingSessions === 0 && data.cashFlowBreaks === 0 && <>
            <polyline points={line(false)} fill="none" stroke="#2684ff" strokeWidth="3" />
            {normalized && <polyline points={line(true)} fill="none" stroke="#d18c00" strokeWidth="2" strokeDasharray="5 4" />}
          </>}
          {data.points.map((p, i) => { const v = normalized ? p.portfolioIndex : p.equity; return v == null ? null : <circle key={p.day} cx={x(i)} cy={y(v)} r="3" fill="#2684ff"><title>{p.day}: {number(v)}</title></circle>; })}
        </svg>
        <figcaption>{normalized ? "Blue: portfolio · Dashed amber: benchmark · Both start at 100 on the first observation." : `Portfolio value (${data.currency}).`} Equally spaced observations; dates and exact values appear below.</figcaption>
      </figure>}
      {data.points.length > 0 && <details><summary>View daily observations</summary><div style={{ overflowX: "auto", maxHeight: 360 }}><table>
        <thead><tr><th>Date</th><th>Value ({data.currency})</th><th>Quality</th><th>Portfolio index</th><th>Benchmark index</th></tr></thead>
        <tbody>{data.points.map(p => <tr key={p.day}><td>{p.day}</td><td>{number(p.equity)}</td><td>{p.quality.replaceAll("_", " ")}</td><td>{number(p.portfolioIndex)}</td><td>{number(p.benchmarkIndex)}</td></tr>)}</tbody>
      </table></div></details>}
      <p>These are observed simulated values, not official closing NAVs or forecasts. India and US portfolios remain separate; no currency conversion is applied.</p>
    </>}
  </section>;
}
