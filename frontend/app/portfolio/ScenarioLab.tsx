"use client";

import { useEffect, useRef, useState } from "react";
import { ApiError, authenticated } from "../campus/client";
import styles from "./portfolio.module.css";

type Definition = { id: string; title: string; version: number; inputType: string; shocks: number[]; lesson: string };
type Result = { currency: string; valuedAt: string; scenario: Definition; cash: number; startingEquity: number; startingInvestedValue: number; projection: { returnPercent: number | null; maximumDrawdownPercent: number; steps: { step: number; shockPercent: number; equity: number; change: number }[] }; positions: { symbol: string; exchange: string; quantity: number; priceTimestamp: string; startingValue: number; endingValue: number; change: number }[]; assumptions: string[] };
const number = (value: number | null) => value == null ? "—" : value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export default function ScenarioLab({ accountId }: { accountId: string }) {
  const [catalog, setCatalog] = useState<Definition[]>([]);
  const [selected, setSelected] = useState("selloff");
  const [shock, setShock] = useState("-10");
  const [result, setResult] = useState<Result | null>(null);
  const [error, setError] = useState("");
  const [locked, setLocked] = useState(false);
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);
  const generation = useRef(0);
  useEffect(() => {
    let active = true;
    setError("");
    authenticated<Definition[]>("scenarios").then(data => { if (active) setCatalog(data); })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Scenarios could not be loaded."); });
    return () => { active = false; generation.current++; };
  }, [accountId, reload]);
  const scenario = catalog.find(item => item.id === selected);
  const customValid = shock.trim() !== "" && Number.isFinite(Number(shock)) && Number(shock) >= -100 && Number(shock) <= 100 && /^-?\d+(\.\d{1,2})?$/.test(shock);
  async function run() {
    if (!scenario || busy) return;
    const current = ++generation.current;
    setBusy(true); setResult(null); setError(""); setLocked(false);
    try {
      const data = await authenticated<Result>(`accounts/${accountId}/scenarios`, { scenarioId: scenario.id, version: scenario.version, ...(selected === "custom" ? { customShockPercent: Number(shock) } : {}) });
      if (generation.current === current) setResult(data);
    } catch (cause) {
      if (generation.current === current) { setError(cause instanceof Error ? cause.message : "Scenario could not be run."); setLocked(cause instanceof ApiError && cause.status === 403); }
    } finally { if (generation.current === current) setBusy(false); }
  }
  const steps = result?.projection.steps ?? [];
  const min = Math.min(...steps.map(s => s.equity)), max = Math.max(...steps.map(s => s.equity));
  const line = steps.map((s, i) => `${20 + i / Math.max(steps.length - 1, 1) * 660},${170 - (s.equity - min) / Math.max(max - min, 1) * 150}`).join(" ");
  return <section className={styles.analytics} aria-labelledby="scenario-heading" id="scenario-lab">
    <div className={styles.analyticsHeader}><div><span>WHAT IF? · PRO</span><h2 id="scenario-heading">Scenario Lab</h2></div></div>
    <p>Explore how a hypothetical price move affects this portfolio. Your holdings and competition scores stay unchanged.</p>
    <p>Synthetic examples, not historical replays or forecasts. All holdings move together; cash stays fixed.</p>
    {catalog.length > 0 ? <>
      <label>Scenario <select aria-label="Scenario" disabled={busy} value={selected} onChange={e => { setSelected(e.target.value); setResult(null); setError(""); setLocked(false); }}>
        {catalog.map(item => <option key={item.id} value={item.id}>{item.title}</option>)}
      </select></label>
      {scenario && <p>{scenario.lesson} · Version {scenario.version}{selected !== "custom" && ` · Cumulative price moves: ${scenario.shocks.map(s => `${s}%`).join(" → ")}`}</p>}
      {selected === "custom" && <label>Price change (%) <input aria-label="Price change (%)" type="number" min="-100" max="100" step="0.01" value={shock} disabled={busy} onChange={e => { setShock(e.target.value); setResult(null); }} /><span> −100% to +100%, up to two decimal places.</span></label>}
      <button type="button" disabled={busy || (selected === "custom" && !customValid)} onClick={run}>{busy ? "Running scenario…" : "Run scenario"}</button>
    </> : !error && <p role="status">Loading scenarios…</p>}
    {error && <p role="alert">{error} {locked && <a href="/settings#plan">View Pro plan</a>}</p>}
    {error && catalog.length === 0 && <button type="button" onClick={() => setReload(v => v + 1)}>Retry scenarios</button>}
    {result && <div aria-live="polite">
      <h3>{result.scenario.title} · Synthetic result</h3>
      <p>Starting valuation: {new Date(result.valuedAt).toLocaleString()} · {result.currency} · Version {result.scenario.version}</p>
      <div className={styles.metrics}>
        <article><span>Starting equity</span><strong>{number(result.startingEquity)}</strong></article>
        <article><span>Hypothetical ending equity</span><strong>{number(steps.at(-1)?.equity ?? null)}</strong></article>
        <article><span>Hypothetical return</span><strong>{number(result.projection.returnPercent)}%</strong></article>
        <article><span>Maximum path drawdown</span><strong>{number(result.projection.maximumDrawdownPercent)}%</strong></article>
      </div>
      <p>Cash held fixed: {number(result.cash)} {result.currency}. The path includes the starting value at step 0.</p>
      <figure><svg viewBox="0 0 700 195" role="img" aria-label="Hypothetical portfolio value by scenario step" style={{ width: "100%", maxHeight: 250 }}><polyline points={line} fill="none" stroke="#2684ff" strokeWidth="3" /></svg><figcaption>Steps are hypothetical states with no calendar duration. Price changes are relative to starting marks.</figcaption></figure>
      <div style={{ overflowX: "auto" }}><table><caption>Scenario steps ({result.currency})</caption><thead><tr><th>Step</th><th>Price shock</th><th>Equity</th><th>Change from start</th></tr></thead><tbody>{steps.map(s => <tr key={s.step}><td>{s.step}</td><td>{number(s.shockPercent)}%</td><td>{number(s.equity)}</td><td>{number(s.change)}</td></tr>)}</tbody></table></div>
      {result.positions.length === 0 ? <p>This portfolio holds only cash, so equity does not change under these price shocks.</p> : <details><summary>Impact on each holding</summary><div style={{ overflowX: "auto" }}><table><caption>Final step impact ({result.currency})</caption><thead><tr><th>Holding</th><th>Quantity</th><th>Quote time</th><th>Starting value</th><th>Ending value</th><th>Change</th></tr></thead><tbody>{result.positions.map(p => <tr key={`${p.exchange}:${p.symbol}`}><td>{p.exchange}:{p.symbol}</td><td>{p.quantity}</td><td>{new Date(p.priceTimestamp).toLocaleString()}</td><td>{number(p.startingValue)}</td><td>{number(p.endingValue)}</td><td>{number(p.change)}</td></tr>)}</tbody></table></div></details>}
      <details open><summary>Assumptions and limitations</summary><ul>{result.assumptions.map(item => <li key={item}>{item}</li>)}</ul></details>
    </div>}
  </section>;
}
