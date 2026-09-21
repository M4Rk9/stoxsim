"use client";

import { useEffect, useState } from "react";
import { loadAnalytics } from "./client";
import styles from "./analytics.module.css";

interface Activity {
  generatedAt: string; trackingStartedAt: string; availableFrom: string;
  activeUsers: { dau: number | null; wau: number | null; mau: number | null };
  daily: { date: string; learners: number | null }[];
  activation: { eligible: number; immature: number; excluded: number; researched: number;
    watchlisted: number; activated: number; executed: number; percent: number | null; medianHours: number | null };
  retention: { day: number; eligible: number; returned: number; immature: number; excluded: number; percent: number | null }[];
}
const count = (value: number | null) => value == null ? "—" : value.toLocaleString("en-IN");
const percent = (value: number | null) => value == null ? "—" : `${value.toFixed(1)}%`;

export default function ActivityPanel({ from, to, revision }: { from: string; to: string; revision: string }) {
  const [data, setData] = useState<Activity | null>(null);
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setData(null); setError(false);
    void loadAnalytics<Activity>("activity", from, to, controller.signal)
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(() => { if (!controller.signal.aborted) setError(true); });
    return () => controller.abort();
  }, [from, to, revision, retry]);

  if (error) return <section className={styles.notice} aria-label="Activity analytics">
    <p role="alert">Activity analytics could not be loaded. Your session may have expired or access may have changed.</p>
    <button type="button" onClick={() => setRetry(value => value + 1)}>Retry activity analytics</button>
  </section>;
  if (!data) return <p role="status">Loading activity and retention…</p>;
  const funnel = data.activation;
  const stages = [["Eligible registrations", funnel.eligible], ["Researched a stock", funnel.researched],
    ["Also added to watchlist", funnel.watchlisted], ["Also submitted a valid order", funnel.activated],
    ["Also completed an execution", funnel.executed]] as const;
  return <section aria-labelledby="activity-title">
    <h2 id="activity-title">Activity and retention</h2>
    <p className={styles.description}>Tracking started {new Date(data.trackingStartedAt).toISOString()}.
      Retained coverage begins {new Date(data.availableFrom).toISOString()}. Earlier activity is unavailable.</p>
    <div className={styles.cards}>
      {([["Daily active learners", "dau", 1], ["Weekly active learners", "wau", 7],
        ["Monthly active learners", "mau", 30]] as const).map(([label, key, days]) =>
        <article className={styles.card} key={key}><h3>{label}</h3><strong>{count(data.activeUsers[key])}</strong>
          <p>Distinct learners over {days} UTC {days === 1 ? "day" : "days"} ending {to}.
            {data.activeUsers[key] == null ? " Full window not observed." : ""}</p></article>)}
    </div>
    <p className={styles.description}>Activity means a visible signed-in visit or interaction, stock research,
      a watchlist addition, or a valid standard-account order submission. Background fills do not count.
      Today is partial. Active-user windows end on the selected To date.</p>
    <details className={styles.panel}><summary>View daily active learners</summary>
      <div className={styles.tableWrap}><table><caption>Distinct active learners by UTC date; — means unobserved</caption>
        <thead><tr><th scope="col">Date</th><th scope="col">Learners</th></tr></thead>
        <tbody>{data.daily.map(day => <tr key={day.date}><th scope="row">{day.date}</th><td>{count(day.learners)}</td></tr>)}</tbody>
      </table></div>
    </details>
    <div className={styles.panel}>
      <h3>Seven-day activation</h3>
      <p>For learners registered in the selected dates, after a full seven days of observation.
        Each row requires all preceding actions within seven days of signup, in any order.</p>
      <p>{funnel.immature} learners still maturing · {funnel.excluded} registrations outside observed coverage.</p>
      <div className={styles.tableWrap}><table><caption>Activation steps for mature signup cohorts</caption>
        <thead><tr><th scope="col">Step</th><th scope="col">Learners</th></tr></thead>
        <tbody>{stages.map(([label, value]) => <tr key={label}><th scope="row">{label}</th><td>{count(value)}</td></tr>)}</tbody>
      </table></div>
      <p>Activation rate: <strong>{percent(funnel.percent)}</strong> · Median time to activation: <strong>
        {funnel.medianHours == null ? "—" : `${funnel.medianHours.toFixed(1)} hours`}</strong></p>
      {funnel.eligible === 0 && <p>No fully observed, mature activation cohort yet.</p>}
    </div>
    <div className={styles.panel}>
      <h3>Return-day retention</h3>
      <p>A return is activity on the exact UTC day 1, 7, or 30 after registration. Only completed return days count;
        immature learners are excluded from each denominator.</p>
      <div className={styles.tableWrap}><table><caption>Retention for registrations in the selected dates</caption>
        <thead><tr><th scope="col">Day</th><th scope="col">Eligible</th><th scope="col">Returned</th>
          <th scope="col">Retention</th><th scope="col">Maturing</th><th scope="col">Unobserved</th></tr></thead>
        <tbody>{data.retention.map(row => <tr key={row.day}><th scope="row">D{row.day}</th><td>{count(row.eligible)}</td>
          <td>{count(row.returned)}</td><td>{percent(row.percent)}</td><td>{count(row.immature)}</td><td>{count(row.excluded)}</td></tr>)}</tbody>
      </table></div>
      <p>— means no eligible cohort, not zero retention. Deleted learners and current administrators are excluded.
        These counts can change after account deletion or role changes.</p>
    </div>
  </section>;
}
