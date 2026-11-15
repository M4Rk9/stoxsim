"use client";
import { useEffect, useState } from "react";
import { loadAnalytics } from "./client";
import styles from "./analytics.module.css";
type Rate = { eligible: number; returned: number; immature: number; excluded: number; percent: number | null };
type Row = { cohort: string; registrations: number; journeyEligible: number; journeyImmature: number; journeyExcluded: number;
  researched: number; watchlisted: number; strategyCreated: number; traded: number; reviewed: number; linkedJourneyCompleted: number;
  d1: Rate; d7: Rate; days1To7: Rate; followingWeek: Rate };
type Cohorts = { activityAvailableFrom: string; journeyAvailableFrom: string; signupDimensionsStartedAt: string; rows: Row[] };
const names: Record<string,string> = { DIRECT: "Direct / no referrer", SEARCH: "Search", SOCIAL: "Social", REFERRAL: "Other referral",
  UNKNOWN: "Unknown / not captured", ANDROID_APP: "Android app", MOBILE_WEB: "Mobile web", DESKTOP_WEB: "Desktop web" };
const day = (value: string) => new Date(value).toLocaleDateString("en-GB", { timeZone: "UTC", day: "numeric", month: "short", year: "numeric" });
function RateCell({ rate }: { rate: Rate }) {
  return <td><strong>{rate.percent == null ? "—" : `${rate.percent.toFixed(1)}%`}</strong><br />
    <small>{rate.returned} / {rate.eligible} eligible<br />{rate.immature} still maturing · {rate.excluded} unobserved</small></td>;
}
export default function CohortPanel({ from, to, revision }: { from: string; to: string; revision: string }) {
  const [group, setGroup] = useState<"WEEK" | "SOURCE" | "DEVICE">("WEEK");
  const [data, setData] = useState<Cohorts | null>(null);
  const [error, setError] = useState(false); const [retry,setRetry]=useState(0);
  useEffect(() => {
    const controller = new AbortController(); setData(null); setError(false);
    loadAnalytics<Cohorts>("cohorts", from, to, controller.signal, group)
      .then(value => { if (!controller.signal.aborted) setData(value); })
      .catch(() => { if (!controller.signal.aborted) setError(true); });
    return () => controller.abort();
  }, [from,to,revision,group,retry]);
  return <section className={styles.panel} aria-labelledby="research-cohort-title">
    <h2 id="research-cohort-title">Research and return cohorts</h2>
    <p>Compare learners who signed up in the selected dates. Existing first-trade conversion and seven-day activation above keep their original definitions.</p>
    <label>Group registrations by <select value={group} onChange={e=>setGroup(e.target.value as typeof group)}>
      <option value="WEEK">Signup week (Monday, UTC)</option><option value="SOURCE">Signup source</option><option value="DEVICE">Signup device</option>
    </select></label>
    {error ? <><p role="alert">Cohorts could not be loaded. Your session or access may have changed.</p><button onClick={()=>setRetry(n=>n+1)}>Retry cohorts</button></>
    : !data ? <p role="status">Loading research cohorts…</p> : <>
      <p>Activity coverage from {day(data.activityAvailableFrom)}; strategy coverage from {day(data.journeyAvailableFrom)}.
        Signup categories began {day(data.signupDimensionsStartedAt)}. Older or unavailable attribution stays unknown. Source is a broad client-reported category; device is classified at signup, not the device used on a return visit.</p>
      {!data.rows.length ? <p>No eligible learner registrations in this date range.</p> : <>
        <div className={styles.tableWrap} tabIndex={0} role="region" aria-label="Return cohort table"><table>
          <caption>Return visits: exact D1/D7, any day 1–7, and any day 8–14. Each window must have fully ended in UTC; — means no mature denominator.</caption>
          <thead><tr><th scope="col">Signup cohort</th><th scope="col">Registrations</th><th scope="col">Exact D1</th><th scope="col">Exact D7</th><th scope="col">Days 1–7</th><th scope="col">Following week (8–14)</th></tr></thead>
          <tbody>{data.rows.map(row=><tr key={row.cohort}><th scope="row">{names[row.cohort] ?? row.cohort}</th><td>{row.registrations}</td>
            <RateCell rate={row.d1}/><RateCell rate={row.d7}/><RateCell rate={row.days1To7}/><RateCell rate={row.followingWeek}/></tr>)}</tbody>
        </table></div>
        <div className={styles.tableWrap} tabIndex={0} role="region" aria-label="Research journey cohort table"><table>
          <caption>First 14 × 24 hours after signup: distinct learners reaching each milestone, independently. The last column requires the ordered linked journey.</caption>
          <thead><tr><th scope="col">Signup cohort</th><th scope="col">Mature / observed</th><th scope="col">Researched</th><th scope="col">Watchlisted</th><th scope="col">Saved strategy</th><th scope="col">Executed trade</th><th scope="col">Wrote review</th><th scope="col">Completed linked journey</th></tr></thead>
          <tbody>{data.rows.map(row=><tr key={row.cohort}><th scope="row">{names[row.cohort] ?? row.cohort}</th>
            <td>{row.journeyEligible}<br /><small>{row.journeyImmature} still maturing · {row.journeyExcluded} unobserved</small></td>
            {[row.researched,row.watchlisted,row.strategyCreated,row.traded,row.reviewed,row.linkedJourneyCompleted].map((n,i)=><td key={i}>{row.journeyEligible ? n : "—"}</td>)}</tr>)}</tbody>
        </table></div>
      </>}
      <details><summary>How completion and exclusions work</summary>
        <p>A completed journey requires a research visit, then a saved strategy, then execution of that strategy’s linked standard-account order, then an explicit written review, all within 14 days of signup. Research visits contain no symbol, so they do not prove research of the same company. Counts reflect currently saved notes and links; deleting a note removes it from linked completion. Other milestone columns do not require this order.</p>
        <p>Administrators, controlled test accounts and deleted accounts are excluded throughout. Background order fills do not count as return visits. Missing tracking coverage is excluded, never treated as inactivity. Today’s incomplete UTC return day is not counted.</p>
      </details>
    </>}
  </section>;
}
