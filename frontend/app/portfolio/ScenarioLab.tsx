"use client";
import { useEffect, useRef, useState } from "react";
import { authenticated } from "../campus/client";
import styles from "../scenario-lab/scenario.module.css";
type Definition = { id: string; title: string; version: number; inputType: string; shocks: number[]; lesson: string };
type Credits = { plan: string; allowance: number; remaining: number; renewsAt: string | null };
type Result = { currency: string; valuedAt: string; scenario: Definition; cash: number; startingEquity: number; projection: { returnPercent: number | null; maximumDrawdownPercent: number; steps: { step: number; shockPercent: number; equity: number; change: number }[] }; positions: { symbol: string; exchange: string; startingValue: number; endingValue: number; change: number }[]; assumptions: string[] };
const number = (n: number | null) => n == null ? "—" : n.toLocaleString("en-IN", { maximumFractionDigits: 2, minimumFractionDigits: 2 });
const icons: Record<string, string> = { selloff: "↘", recovery: "↗", whipsaw: "⌁", custom: "+" };
export default function ScenarioLab({ accountId }: { accountId: string }) {
  const [catalog, setCatalog] = useState<Definition[]>([]);
  const [credits, setCredits] = useState<Credits | null>(null);
  const [selected, setSelected] = useState("recovery");
  const [title, setTitle] = useState("My next big idea");
  const [shocks, setShocks] = useState(["10", "25", "50"]);
  const [result, setResult] = useState<Result | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);
  const generation = useRef(0);
  const submission = useRef<{ signature: string; id: string } | null>(null);
  const inFlight = useRef(false);
  useEffect(() => {
    let active = true;
    Promise.all([authenticated<Definition[]>("scenarios"), authenticated<Credits>("scenarios/credits")]).then(([items, allowance]) => {
      if (!active) return;
      setCatalog(items); setCredits(allowance); setError("");
      setSelected(items.some(i => i.id === "recovery") ? "recovery" : items[0]?.id ?? "custom");
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Unable to open the lab."); });
    return () => { active = false; generation.current++; };
  }, [accountId, reload]);
  const scenario = catalog.find(s => s.id === selected);
  const valid = selected !== "custom" || (title.trim().length > 0 && title.length <= 80 && shocks.every(s => /^-?\d+(\.\d{1,2})?$/.test(s) && Number(s) >= -100 && Number(s) <= 100));
  function changed() { setResult(null); setError(""); submission.current = null; }
  async function run() {
    if (!scenario || !valid || inFlight.current || !credits || credits.remaining <= 0) return;
    const body = { scenarioId: selected, version: scenario.version, ...(selected === "custom" ? { title: title.trim(), customShocks: shocks.map(Number) } : {}) };
    const signature = JSON.stringify(body);
    if (submission.current?.signature !== signature) submission.current = { signature, id: crypto.randomUUID() };
    const requestId = submission.current.id;
    const current = ++generation.current;
    inFlight.current = true; setBusy(true); setError("");
    try {
      const data = await authenticated<Result>(`accounts/${accountId}/scenarios`, { ...body, requestId });
      if (current !== generation.current) return;
      setResult(data); submission.current = null;
      // Update immediately, then reconcile across tabs against the server balance.
      setCredits(c => c ? { ...c, remaining: Math.max(0, c.remaining - 1) } : c);
      const balance = await authenticated<Credits>("scenarios/credits");
      if (current === generation.current) setCredits(balance);
    } catch (cause) {
      if (current === generation.current) setError(cause instanceof Error ? cause.message : "Unable to run the scenario. Try again.");
    } finally { inFlight.current = false; if (current === generation.current) setBusy(false); }
  }
  const steps = result?.projection.steps ?? [];
  const values = steps.map(s => s.equity), min = Math.min(...values), max = Math.max(...values);
  const line = steps.map((s, i) => `${24 + i / Math.max(steps.length - 1, 1) * 652},${max === min ? 110 : 195 - (s.equity - min) / (max - min) * 165}`).join(" ");
  const currency = result?.currency === "USD" ? "$" : "₹";
  return <section aria-labelledby="scenario-heading" id="scenario-lab">
    <div className={styles.labHeading}><div><h2 id="scenario-heading">Scenario Lab</h2><p>Start with an idea, or create your own.</p></div><div className={styles.credits}>{credits ? <><strong>{credits.remaining}</strong> / {credits.allowance} credits <small>{credits.plan === "FREE" ? "Starter allowance" : "Refills on paid renewal"}</small></> : "Loading credits…"}</div></div>
    <div className={styles.workspace}>
      <div className={styles.builder}>
        <div className={styles.presetGrid} role="group" aria-label="Choose a scenario">
          {catalog.map(item => <button className={selected === item.id ? styles.selected : ""} type="button" key={item.id} disabled={busy} aria-pressed={selected === item.id} onClick={() => { setSelected(item.id); changed(); }}><span>{icons[item.id] ?? "↗"}</span><strong>{item.title}</strong><small>{item.id === "custom" ? "Make it yours" : `${item.shocks.at(-1)}% final move`}</small></button>)}
        </div>
        {selected === "custom" ? <div className={styles.custom}>
          <label>Scenario name<input maxLength={80} value={title} disabled={busy} onChange={e => { setTitle(e.target.value); changed(); }} /></label>
          <div className={styles.pathHeader}><h3>Build your market path</h3><span>{shocks.length} / 12 steps</span></div>
          <p>Each move is relative to today’s price.</p>
          {shocks.map((shock, index) => <div className={styles.step} key={index}><span>{String(index + 1).padStart(2, "0")}</span><input aria-label={`Step ${index + 1} price change`} type="number" min="-100" max="100" step="0.01" value={shock} disabled={busy} onChange={e => { setShocks(values => values.map((v, i) => i === index ? e.target.value : v)); changed(); }} /><span>%</span><button aria-label={`Remove step ${index + 1}`} disabled={busy || shocks.length === 1} onClick={() => { setShocks(values => values.filter((_, i) => i !== index)); changed(); }}>×</button></div>)}
          <button className={styles.addStep} disabled={busy || shocks.length >= 12} onClick={() => { setShocks(s => [...s, "0"]); changed(); }}>+ Add a step</button>
          {!valid && <p role="status">Name your scenario and use moves between −100% and +100%, with up to two decimals.</p>}
        </div> : scenario && <div className={styles.presetDescription}><h3>{scenario.title}</h3><p>{scenario.lesson}</p><div className={styles.path}>{scenario.shocks.map((s, i) => <span key={i}>{s > 0 ? "+" : ""}{s}%</span>)}</div></div>}
        <button className={styles.run} disabled={busy || !scenario || !valid || !credits || credits.remaining === 0} onClick={run}>{busy ? "Exploring your scenario…" : "Run scenario"}<span>1 credit →</span></button>
        {credits?.remaining === 0 && <p>No credits left. <a href="/settings#plan">View your plan</a></p>}
        {error && <p className={styles.error} role="alert">{error}</p>}
        {!catalog.length && error && <button onClick={() => setReload(n => n + 1)}>Retry</button>}
      </div>
      <div className={styles.results} aria-live="polite" aria-busy={busy}>
        {!result ? <div className={styles.empty}><div className={styles.orbit}>↗</div><span>THE POSSIBILITIES START HERE</span><h3>Give your ideas<br />a little room to grow.</h3><p>Choose a scenario to see its impact on your portfolio.</p></div> : <>
          <div className={styles.resultHeading}><span>YOUR WHAT-IF RESULT</span><h3>{result.scenario.title}</h3></div>
          <div className={styles.endValue}>{currency}{number(steps.at(-1)?.equity ?? null)}</div>
          <p className={(result.projection.returnPercent ?? 0) >= 0 ? styles.positive : styles.negative}>{number(result.projection.returnPercent)}% portfolio change</p>
          <svg className={styles.chart} viewBox="0 0 700 220" role="img" aria-label="Hypothetical portfolio value by scenario step"><line x1="24" x2="676" y1="200" y2="200" stroke="currentColor" opacity=".12" /><polyline points={line} fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinejoin="round" />{steps.map((s, i) => <circle key={s.step} cx={24 + i / Math.max(steps.length - 1, 1) * 652} cy={max === min ? 110 : 195 - (s.equity - min) / (max - min) * 165} r="4" fill="currentColor"><title>Step {s.step}: {currency}{number(s.equity)}</title></circle>)}</svg>
          <div className={styles.resultStats}><div><span>Starting value</span><strong>{currency}{number(result.startingEquity)}</strong></div><div><span>Largest drawdown</span><strong>{number(result.projection.maximumDrawdownPercent)}%</strong></div><div><span>Cash held</span><strong>{currency}{number(result.cash)}</strong></div></div>
          {result.positions.length === 0 && <p className={styles.cashOnly}>Your portfolio holds cash only. Add holdings to explore price changes.</p>}
          <details><summary>Step-by-step results</summary><div className={styles.tableWrap}><table><thead><tr><th>Step</th><th>Price move</th><th>Portfolio value</th></tr></thead><tbody>{steps.map(s => <tr key={s.step}><td>{s.step}</td><td>{s.shockPercent}%</td><td>{currency}{number(s.equity)}</td></tr>)}</tbody></table></div></details>
          {result.positions.length > 0 && <details><summary>Impact on each holding</summary><div className={styles.tableWrap}><table><thead><tr><th>Holding</th><th>Starting value</th><th>Ending value</th><th>Change</th></tr></thead><tbody>{result.positions.map(p => <tr key={`${p.exchange}:${p.symbol}`}><td>{p.exchange}:{p.symbol}</td><td>{number(p.startingValue)}</td><td>{number(p.endingValue)}</td><td>{number(p.change)}</td></tr>)}</tbody></table></div></details>}
          <details><summary>How this is calculated</summary><ul>{result.assumptions.map(a => <li key={a}>{a}</li>)}</ul></details>
        </>}
      </div>
    </div>
  </section>;
}
