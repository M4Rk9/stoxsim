"use client";
import { FormEvent, useEffect, useState } from "react";
import { authenticatedRequest, session } from "../campus/client";
import FinwizAnswer from "../finwiz/FinwizAnswer";
import { emptyPlan, strategyExchanges, strategyOrderHref, localDay, reviewDate, type StrategyPlan } from "../lib/strategy";
import { stockHref } from "../lib/stock-links";
import OrderLinker from "./OrderLinker";
import styles from "./LearnerExperience.module.css";

interface Draft { accountId: string | null; orderId: string | null; reason: string; assumptions: string; risks: string; expectedOutcome: string; reflection: string; plan?: StrategyPlan; completeReview?: boolean }
interface Reflection extends Draft { id: string; createdAt: string; updatedAt: string; reviewedAt?: string | null;
  linkedOrder?: { status: string; side: string; quantity: number; executionPrice: number | null; currency: string; exchange: string; symbol: string; executedAt: string | null } | null }
interface Assistance { answer: string; disclaimer: string; provider: string; groundedInStoxSimData: boolean; generatedAt: string; dataAsOf?: string }
const empty: Draft = { accountId: null, orderId: null, reason: "", assumptions: "", risks: "", expectedOutcome: "", reflection: "" };
const fields: Array<{ key: keyof Pick<Draft, "reason" | "assumptions" | "risks" | "expectedOutcome" | "reflection">; label: string }> = [
  { key: "reason", label: "Why I made this practice decision" }, { key: "assumptions", label: "My assumptions" },
  { key: "risks", label: "Risks I considered" }, { key: "expectedOutcome", label: "What I expected to learn" },
  { key: "reflection", label: "What I learned afterwards" },
];
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default function DecisionReflections({ journal = false }: { journal?: boolean }) {
  const [items, setItems] = useState<Reflection[]>([]), [draft, setDraft] = useState<Draft>(empty);
  const [editing, setEditing] = useState<string | null>(null), [consentFor, setConsentFor] = useState<string | null>(null);
  const [assistance, setAssistance] = useState<{ id: string; value: Assistance } | null>(null);
  const [authenticated, setAuthenticated] = useState(false), [busy, setBusy] = useState(false), [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(""), [notice, setNotice] = useState("");
  useEffect(() => {
    if (!session()) return;
    let active = true; setAuthenticated(true);
    const params = new URLSearchParams(window.location.search);
    const accountId = params.get("accountId"), orderId = params.get("orderId");
    setDraft({ ...empty, ...(journal ? { plan: { ...emptyPlan, exchange: strategyExchanges.includes(params.get("exchange") ?? "") ? params.get("exchange")! : "", symbol: /^[A-Z0-9&._-]{1,100}$/.test(params.get("symbol") ?? "") ? params.get("symbol")! : "" } } : {}), accountId: accountId && uuid.test(accountId) ? accountId : null, orderId: orderId && uuid.test(orderId) ? orderId : null });
    authenticatedRequest<Reflection[]>("reflections").then(value => {
      if (active && Array.isArray(value)) {
        setItems(value);
        const selected = value.find(note => note.id === params.get("edit"));
        if (selected) { setDraft({ ...selected, plan: selected.plan ?? { ...emptyPlan } }); setEditing(selected.id); }
      }
    })
      .catch(() => { if (active) setError("Reflections could not be loaded. Your unsaved notes stay in this page."); })
      .finally(() => { if (active) setLoaded(true); });
    return () => { active = false; };
  }, [journal]);
  async function act(action: () => Promise<void>) {
    if (busy) return; setBusy(true); setError(""); setNotice("");
    try { await action(); } catch (cause) { setError(cause instanceof Error ? cause.message : "This reflection could not be updated."); }
    finally { setBusy(false); }
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    await act(async () => {
      const payload = Object.fromEntries(Object.keys(empty).map(key => [key, draft[key as keyof Draft]]));
      if (journal) { payload.plan = draft.plan ?? emptyPlan; payload.completeReview = draft.completeReview ?? false; }
      const item = await authenticatedRequest<Reflection>(editing ? `reflections/${editing}` : "reflections", { method: editing ? "PUT" : "POST", body: JSON.stringify(payload) });
      setItems(current => [item, ...current.filter(existing => existing.id !== item.id)].slice(0, 50));
      setDraft(journal ? { ...empty, plan: { ...emptyPlan } } : empty); setEditing(null); setConsentFor(null); setAssistance(null); setNotice("Reflection saved. Nothing was sent to Finwiz.");
    });
  }
  if (!authenticated) return <section className={styles.card} id="reflections"><h2>Decision reflections</h2><p><a href="/">Sign in</a> to save an optional reflection on a practice decision.</p></section>;
  return <section className={styles.card} id="reflections" aria-labelledby="reflections-title" aria-busy={busy}>
    <h2 id="reflections-title">{journal ? "Your strategies and reviews" : "Decision reflections"}</h2>
    <p>{journal ? "Capture your thesis before practising. Set a review date, then compare your assumptions with what happened. Dates create an in-app review list; no email or push reminder is sent." : "A short, optional note about your simulated decisions."} Saved notes are private to your account. Finwiz receives a note only when you explicitly request assistance.</p>
    {error && <p role="alert" className={styles.error}>{error}</p>}{notice && <p role="status">{notice}</p>}
    <details open={journal || editing !== null || draft.orderId !== null}>
      <summary>{journal ? editing ? "Review or edit your strategy" : "Form a new strategy" : editing ? "Edit reflection" : "Write a reflection"}</summary>
      <form onSubmit={save}>
        {draft.orderId && <p className={styles.muted}>Linked to your selected practice order.</p>}
        {journal && <fieldset className={styles.planFields}><legend>Research context <small>Optional</small></legend>
          <div className={styles.fields}>
            <label>Exchange<select disabled={busy} value={draft.plan?.exchange ?? ""} onChange={e => setDraft({ ...draft, plan: { ...(draft.plan ?? emptyPlan), exchange: e.target.value } })}><option value="">Choose exchange</option>{strategyExchanges.map(exchange => <option key={exchange}>{exchange}</option>)}</select></label>
            <label>Symbol<input disabled={busy} maxLength={100} pattern="[A-Z0-9&amp;._\-]+" value={draft.plan?.symbol ?? ""} onChange={e => setDraft({ ...draft, plan: { ...(draft.plan ?? emptyPlan), symbol: e.target.value.toUpperCase() } })} placeholder="e.g. INFY" /></label>
            <label>Time horizon<input disabled={busy} maxLength={200} value={draft.plan?.horizon ?? ""} onChange={e => setDraft({ ...draft, plan: { ...(draft.plan ?? emptyPlan), horizon: e.target.value } })} placeholder="e.g. next quarterly results" /></label>
          </div>
          <div className={styles.fields}>
            <label>Target or evidence to look for<input disabled={busy} maxLength={500} value={draft.plan?.target ?? ""} onChange={e => setDraft({ ...draft, plan: { ...(draft.plan ?? emptyPlan), target: e.target.value } })} placeholder="Include currency for a price target" /></label>
            <label>Stop or invalidation condition<input disabled={busy} maxLength={500} value={draft.plan?.invalidation ?? ""} onChange={e => setDraft({ ...draft, plan: { ...(draft.plan ?? emptyPlan), invalidation: e.target.value } })} placeholder="What would change your view?" /></label>
            <label>Review date<input disabled={busy} type="date" min="1970-01-01" max="2100-12-31" value={draft.plan?.reviewOn ?? ""} onChange={e => setDraft({ ...draft, plan: { ...(draft.plan ?? emptyPlan), reviewOn: e.target.value || null } })} /></label>
          </div><p className={styles.muted}>Targets and stops are notes, not orders. No order is placed or closed automatically.</p>
        </fieldset>}
        {fields.map(field => <label key={field.key}>{journal && field.key === "reason" ? "My thesis and entry rationale" : field.label}{field.key !== "reason" && " (optional)"}
          <textarea disabled={busy} required={field.key === "reason"} maxLength={2000} value={draft[field.key]} onChange={event => setDraft({ ...draft, [field.key]: event.target.value })} />
        </label>)}
        {journal && <label className={styles.check}><input type="checkbox" disabled={busy} checked={draft.completeReview ?? false} onChange={e => setDraft({ ...draft, completeReview: e.target.checked })} />Complete this review. I have recorded what happened and what I learned.</label>}
        <div className={styles.actions}><button disabled={busy || !draft.reason.trim() || (draft.completeReview && !draft.reflection.trim())}>{journal ? "Save strategy and review" : editing ? "Save reflection changes" : "Save reflection"}</button>
          <button type="button" className={styles.secondary} disabled={busy} onClick={() => { setDraft(journal ? { ...empty, plan: { ...emptyPlan } } : empty); setEditing(null); }}>Clear draft</button></div>
      </form>
    </details>
    {!loaded && <p role="status">Loading reflections…</p>}
    {loaded && !items.length && <p>{journal ? "Your first idea starts here. You can save research without placing a trade." : "No saved reflections yet."}</p>}
    <ul className={styles.list}>{items.map(item => <li key={item.id}>
      <details><summary><span className={styles.badge}>{item.reviewedAt ? "Reviewed" : item.plan?.reviewOn && item.plan.reviewOn <= localDay() ? "Review due" : "In progress"}</span>{" · "}<span>{item.reason.length > 90 ? item.reason.slice(0, 90) + "…" : item.reason}</span></summary>
        <p className={styles.muted}>Saved {new Date(item.createdAt).toLocaleDateString()}{item.reviewedAt ? ` · Reviewed ${new Date(item.reviewedAt).toLocaleDateString()}` : item.plan?.reviewOn ? ` · ${item.plan.reviewOn <= localDay() ? "Review due" : "Review planned"} ${reviewDate(item.plan.reviewOn)}` : ""}</p>
        {item.plan && <><div className={styles.planSummary}>
          {[["Research", [item.plan.exchange, item.plan.symbol].filter(Boolean).join(" · ")], ["Horizon", item.plan.horizon], ["Target", item.plan.target], ["Invalidation", item.plan.invalidation]].map(([label, value]) => value && <div key={label}><strong>{label}</strong><p>{value}</p></div>)}
        </div>{item.plan.symbol && item.plan.exchange && <div className={styles.actions}><a href={stockHref(item.plan.exchange, item.plan.symbol)}>Reopen research</a>{!item.orderId && <a href={strategyOrderHref(item.id, item.plan)}>Practise this strategy →</a>}</div>}</>}
        {item.linkedOrder && <div className={styles.orderOutcome}><h3>Practice order outcome</h3>
          <p>{item.linkedOrder.side === "BUY" ? "Buy" : "Sell"} {item.linkedOrder.quantity} · {item.linkedOrder.exchange} · {item.linkedOrder.symbol}</p>
          <p>Status: {item.linkedOrder.status.toLowerCase()}{item.linkedOrder.executionPrice != null ? ` · Executed at ${new Intl.NumberFormat(undefined, { style: "currency", currency: item.linkedOrder.currency }).format(item.linkedOrder.executionPrice)}` : " · No executed price yet"}</p>
          <p className={styles.muted}>Execution is not a closed-trade return. Compare current holdings and charges in your <a href="/portfolio">portfolio</a>, then record whether the evidence supported your thesis.</p></div>}
        {journal && !item.orderId && <OrderLinker strategyId={item.id} plan={item.plan} onLinked={() => { void authenticatedRequest<Reflection[]>("reflections").then(setItems).catch(() => setError("The link was saved, but the journal could not refresh. Reload to see it.")); }} /> }
        {fields.map(field => item[field.key] && <div key={field.key}><h3>{field.label}</h3><p>{item[field.key]}</p></div>)}
        <div className={styles.actions}>
          <button className={styles.secondary} disabled={busy} onClick={() => { setDraft({ ...item, completeReview: false }); setEditing(item.id); setAssistance(null); setConsentFor(null); document.getElementById("reflections")?.scrollIntoView(); }}>Edit reflection</button>
          <button className={styles.secondary} disabled={busy} onClick={() => {
            if (window.confirm("Delete this reflection permanently?")) void act(async () => {
              await authenticatedRequest(`reflections/${item.id}`, { method: "DELETE" });
              setItems(current => current.filter(row => row.id !== item.id)); if (editing === item.id) { setEditing(null); setDraft(empty); }
              setAssistance(null); setConsentFor(null); setNotice("Reflection deleted.");
            });
          }}>Delete reflection</button>
        </div>
        <label className={styles.check}><input type="checkbox" checked={consentFor === item.id} onChange={event => setConsentFor(event.target.checked ? item.id : null)} />I choose to share this saved reflection with Finwiz and its AI provider for this explanation.</label>
        <button disabled={busy || consentFor !== item.id} onClick={() => void act(async () => {
          const value = await authenticatedRequest<Assistance>(`reflections/${item.id}/assistance`, { method: "POST", body: JSON.stringify({ consent: true }) });
          setAssistance({ id: item.id, value }); setConsentFor(null);
        })}>Ask Finwiz about this reflection</button>
        {assistance?.id === item.id && <div aria-live="polite"><p className={styles.muted}>{assistance.value.provider === "GEMINI" ? "AI-assisted explanation" : "Educational fallback"}{assistance.value.dataAsOf ? ` · Data as of ${new Date(assistance.value.dataAsOf).toLocaleString()}` : ""}</p><FinwizAnswer answer={assistance.value.answer} /><p>{assistance.value.disclaimer}</p></div>}
      </details>
    </li>)}</ul>
    {items.length === 50 && <p className={styles.muted}>Showing up to 50 notes, with unreviewed and scheduled ideas first.</p>}
  </section>;
}
