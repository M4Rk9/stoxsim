"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import styles from "./analytics.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const number = new Intl.NumberFormat("en-IN");
const statuses = ["OPEN", "EXECUTED", "CANCELLED", "REJECTED", "EXPIRED"];

interface Session { accessToken: string }
interface Overview {
  version: string;
  from: string;
  to: string;
  timeZone: string;
  generatedAt: string;
  users: { registered: number; emailVerified: number };
  cohort: { registered: number; firstTradeCompleted: number; firstTradePercent: number | null };
  signups: { date: string; registered: number }[];
  orders: { marketRegion: string; status: string; count: number }[];
}

class ApiError extends Error {
  constructor(message: string, readonly status: number) { super(message); }
}

function readSession(): Session | null {
  try {
    const raw = window.sessionStorage.getItem("stoxsim-session");
    const session = raw ? JSON.parse(raw) as Session : null;
    return session?.accessToken ? session : null;
  } catch { return null; }
}

let refreshInFlight: Promise<Session> | null = null;
function refreshSession(): Promise<Session> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    const response = await fetch(`${API_URL}/api/v1/auth/refresh`, {
      method: "POST", credentials: "include", cache: "no-store",
    });
    if (!response.ok) throw new ApiError("Please sign in to view owner analytics.", response.status);
    const session = await response.json() as Session;
    window.sessionStorage.setItem("stoxsim-session", JSON.stringify(session));
    return session;
  })().finally(() => { refreshInFlight = null; });
  return refreshInFlight;
}

async function overview(from: string, to: string, signal: AbortSignal): Promise<Overview> {
  const session = readSession() ?? await refreshSession();
  const url = `${API_URL}/api/v1/admin/analytics/overview?${new URLSearchParams({ from, to })}`;
  const request = (token: string) => fetch(url, {
    credentials: "include", cache: "no-store", signal,
    headers: { Authorization: `Bearer ${token}` },
  });
  let response = await request(session.accessToken);
  if (response.status === 401) response = await request((await refreshSession()).accessToken);
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new ApiError(payload?.message ?? "Analytics could not be loaded. Please try again.", response.status);
  }
  return response.json() as Promise<Overview>;
}

function dateOnly(value: Date) { return value.toISOString().slice(0, 10); }

export default function AnalyticsPage() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [today, setToday] = useState("");
  const [data, setData] = useState<Overview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [accessDenied, setAccessDenied] = useState(false);
  const [signedOut, setSignedOut] = useState(false);
  const inFlight = useRef<AbortController | null>(null);

  async function load(first: string, last: string) {
    inFlight.current?.abort();
    const controller = new AbortController();
    inFlight.current = controller;
    setLoading(true);
    setData(null);
    setError("");
    setAccessDenied(false);
    setSignedOut(false);
    try {
      const result = await overview(first, last, controller.signal);
      if (!controller.signal.aborted) setData(result);
    } catch (cause) {
      if (!controller.signal.aborted) {
        const status = cause instanceof ApiError ? cause.status : 0;
        setAccessDenied(status === 403);
        setSignedOut(status === 401);
        setError(status === 403 ? "This page is available to platform administrators only."
          : status === 401 ? "Please sign in to view owner analytics."
          : cause instanceof Error ? cause.message : "Analytics could not be loaded.");
      }
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }

  useEffect(() => {
    const end = new Date();
    const start = new Date(end);
    start.setUTCDate(start.getUTCDate() - 29);
    const first = dateOnly(start);
    const last = dateOnly(end);
    setFrom(first); setTo(last); setToday(last);
    void load(first, last);
    return () => { inFlight.current?.abort(); };
  }, []);

  function submit(event: FormEvent) {
    event.preventDefault();
    void load(from, to);
  }

  const totalOrders = data?.orders.reduce((sum, row) => sum + row.count, 0) ?? 0;
  const peak = Math.max(1, ...(data?.signups.map(day => day.registered) ?? []));

  return <main className={styles.shell} id="main-content" tabIndex={-1}>
    <header className={styles.header}>
      <a className={styles.brand} href="/">Stox<span>Sim</span></a>
      <a href="/settings">Account settings</a>
    </header>
    <div className={styles.heading}>
      <p className={styles.eyebrow}>PLATFORM OVERVIEW</p>
      <h1>Owner analytics</h1>
      <p>See who is joining and taking their first step into paper trading.</p>
    </div>

    {!accessDenied && !signedOut && <form className={styles.filters} onSubmit={submit}>
      <label>From (UTC)<input type="date" value={from} min="1970-01-01" max={to || today}
        onChange={event => setFrom(event.target.value)} required /></label>
      <label>To (UTC)<input type="date" value={to} min={from || "1970-01-01"} max={today}
        onChange={event => setTo(event.target.value)} required /></label>
      <button type="submit" disabled={loading}>{loading ? "Loading…" : "Apply dates"}</button>
      <p>Up to 90 days. Today is a partial day.</p>
    </form>}

    {loading && <p role="status" className={styles.notice}>Loading analytics…</p>}
    {error && <div role="alert" className={styles.notice}>
      <p>{error}</p>
      {signedOut ? <a href="/">Go to sign in</a> : accessDenied ? <a href="/">Back to dashboard</a>
        : <button type="button" onClick={() => void load(from, to)}>Try again</button>}
    </div>}

    {data && <>
      <p className={styles.updated}>Updated {new Date(data.generatedAt).toLocaleString("en-IN", { timeZone: "UTC" })} UTC.</p>
      <section aria-labelledby="users-title">
        <h2 id="users-title">Current learner accounts</h2>
        <div className={styles.cards}>
          <article className={styles.card}><h3>Registered learners</h3><strong>{number.format(data.users.registered)}</strong><p>All current learner accounts</p></article>
          <article className={styles.card}><h3>Verified email</h3><strong>{number.format(data.users.emailVerified)}</strong><p>Current learners with a verified email</p></article>
        </div>
      </section>

      <section aria-labelledby="cohort-title">
        <h2 id="cohort-title">Learners who joined in this period</h2>
        <p className={styles.description}>{data.from} to {data.to}, inclusive · UTC</p>
        <div className={styles.cards}>
          <article className={styles.card}><h3>New learners</h3><strong>{number.format(data.cohort.registered)}</strong><p>Registrations in the selected period</p></article>
          <article className={styles.card}><h3>Completed a first trade</h3><strong>{number.format(data.cohort.firstTradeCompleted)}</strong><p>At least one executed standard-account trade by now</p></article>
          <article className={styles.card}><h3>First-trade conversion</h3><strong>{data.cohort.firstTradePercent == null ? "—" : `${data.cohort.firstTradePercent.toFixed(1)}%`}</strong><p>First-trade learners ÷ new learners</p></article>
        </div>
        <p className={styles.description}>Conversion includes trades completed after the selected period, up to this update. Recent signup groups have had less time to trade.</p>
      </section>

      <section className={styles.panel} aria-labelledby="trend-title">
        <h2 id="trend-title">Daily registrations</h2>
        {data.cohort.registered === 0 && <p>No learner registrations in this period.</p>}
        <div className={styles.chart} role="img" aria-label={`Daily registrations from ${data.from} to ${data.to}. Exact counts are in the expandable table below.`}>
          {data.signups.map(day => <div key={day.date} className={styles.barColumn}
            title={`${day.date}: ${number.format(day.registered)} registrations`}>
            <div className={styles.bar} style={{ height: `${day.registered / peak * 100}%` }} />
          </div>)}
        </div>
        <div className={styles.axis}><span>{data.from}</span><span>{data.to}</span></div>
        <details><summary>View daily counts</summary>
          <div className={styles.tableWrap}><table><caption>Registrations by UTC date</caption>
            <thead><tr><th scope="col">Date</th><th scope="col">New learners</th></tr></thead>
            <tbody>{data.signups.map(day => <tr key={day.date}><th scope="row">{day.date}</th><td>{number.format(day.registered)}</td></tr>)}</tbody>
          </table></div>
        </details>
      </section>

      <section className={styles.panel} aria-labelledby="orders-title">
        <h2 id="orders-title">Orders submitted in this period</h2>
        <p>{number.format(totalOrders)} standard-account orders across all current learners, shown by their current status. Sandbox orders are excluded.</p>
        {totalOrders === 0 && <p>No standard-account orders in this period.</p>}
        <div className={styles.tableWrap}><table><caption>Current status of orders submitted {data.from} to {data.to} (UTC)</caption>
          <thead><tr><th scope="col">Market</th>{statuses.map(status => <th scope="col" key={status}>{status.charAt(0) + status.slice(1).toLowerCase()}</th>)}</tr></thead>
          <tbody>{["INDIA", "UNITED_STATES"].map(market => <tr key={market}>
            <th scope="row">{market === "INDIA" ? "India" : "United States"}</th>
            {statuses.map(status => <td key={status}>{number.format(data.orders.find(row => row.marketRegion === market && row.status === status)?.count ?? 0)}</td>)}
          </tr>)}</tbody>
        </table></div>
      </section>
      <aside className={styles.notice}>
        <h2>About these numbers</h2>
        <p>Administrator and deleted accounts are excluded. These are current records, so deleting an account changes past counts. India and US activity can each contribute to first-trade conversion; every learner counts once.</p>
        <p>Active users, retention and the full activation funnel will appear after activity tracking is introduced. First-trade conversion is a separate measure.</p>
      </aside>
    </>}
  </main>;
}
