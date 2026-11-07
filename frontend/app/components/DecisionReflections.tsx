"use client";
import { FormEvent, useEffect, useState } from "react";
import { authenticatedRequest, session } from "../campus/client";
import FinwizAnswer from "../finwiz/FinwizAnswer";
import styles from "./LearnerExperience.module.css";

interface Draft { accountId: string | null; orderId: string | null; reason: string; assumptions: string; risks: string; expectedOutcome: string; reflection: string }
interface Reflection extends Draft { id: string; createdAt: string; updatedAt: string }
interface Assistance { answer: string; disclaimer: string; provider: string; groundedInStoxSimData: boolean; generatedAt: string; dataAsOf?: string }
const empty: Draft = { accountId: null, orderId: null, reason: "", assumptions: "", risks: "", expectedOutcome: "", reflection: "" };
const fields: Array<{ key: keyof Pick<Draft, "reason" | "assumptions" | "risks" | "expectedOutcome" | "reflection">; label: string }> = [
  { key: "reason", label: "Why I made this practice decision" }, { key: "assumptions", label: "My assumptions" },
  { key: "risks", label: "Risks I considered" }, { key: "expectedOutcome", label: "What I expected to learn" },
  { key: "reflection", label: "What I learned afterwards" },
];
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default function DecisionReflections() {
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
    setDraft({ ...empty, accountId: accountId && uuid.test(accountId) ? accountId : null, orderId: orderId && uuid.test(orderId) ? orderId : null });
    authenticatedRequest<Reflection[]>("reflections").then(value => { if (active && Array.isArray(value)) setItems(value); })
      .catch(() => { if (active) setError("Reflections could not be loaded. Your unsaved notes stay in this page."); })
      .finally(() => { if (active) setLoaded(true); });
    return () => { active = false; };
  }, []);
  async function act(action: () => Promise<void>) {
    if (busy) return; setBusy(true); setError(""); setNotice("");
    try { await action(); } catch (cause) { setError(cause instanceof Error ? cause.message : "This reflection could not be updated."); }
    finally { setBusy(false); }
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    await act(async () => {
      const payload = Object.fromEntries(Object.keys(empty).map(key => [key, draft[key as keyof Draft]]));
      const item = await authenticatedRequest<Reflection>(editing ? `reflections/${editing}` : "reflections", { method: editing ? "PUT" : "POST", body: JSON.stringify(payload) });
      setItems(current => [item, ...current.filter(existing => existing.id !== item.id)].slice(0, 50));
      setDraft(empty); setEditing(null); setConsentFor(null); setAssistance(null); setNotice("Reflection saved. Nothing was sent to Finwiz.");
    });
  }
  if (!authenticated) return <section className={styles.card} id="reflections"><h2>Decision reflections</h2><p><a href="/">Sign in</a> to save an optional reflection on a practice decision.</p></section>;
  return <section className={styles.card} id="reflections" aria-labelledby="reflections-title" aria-busy={busy}>
    <h2 id="reflections-title">Decision reflections</h2><p>A short, optional note about your simulated decisions. Saved notes are private to your account. Finwiz receives a note only when you explicitly request assistance.</p>
    {error && <p role="alert" className={styles.error}>{error}</p>}{notice && <p role="status">{notice}</p>}
    <details open={editing !== null || draft.orderId !== null}>
      <summary>{editing ? "Edit reflection" : "Write a reflection"}</summary>
      <form onSubmit={save}>
        {draft.orderId && <p className={styles.muted}>Linked to your selected practice order.</p>}
        {fields.map(field => <label key={field.key}>{field.label}{field.key !== "reason" && " (optional)"}
          <textarea disabled={busy} required={field.key === "reason"} maxLength={2000} value={draft[field.key]} onChange={event => setDraft({ ...draft, [field.key]: event.target.value })} />
        </label>)}
        <div className={styles.actions}><button disabled={busy || !draft.reason.trim()}>{editing ? "Save reflection changes" : "Save reflection"}</button>
          <button type="button" className={styles.secondary} disabled={busy} onClick={() => { setDraft(empty); setEditing(null); }}>Clear draft</button></div>
      </form>
    </details>
    {!loaded && <p role="status">Loading reflections…</p>}
    {loaded && !items.length && <p>No saved reflections yet.</p>}
    <ul className={styles.list}>{items.map(item => <li key={item.id}>
      <details><summary>{item.reason.length > 90 ? item.reason.slice(0, 90) + "…" : item.reason}</summary>
        <p className={styles.muted}>Saved {new Date(item.createdAt).toLocaleDateString()}</p>
        {fields.map(field => item[field.key] && <div key={field.key}><h3>{field.label}</h3><p>{item[field.key]}</p></div>)}
        <div className={styles.actions}>
          <button className={styles.secondary} disabled={busy} onClick={() => { setDraft(item); setEditing(item.id); setAssistance(null); setConsentFor(null); document.getElementById("reflections")?.scrollIntoView(); }}>Edit reflection</button>
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
    {items.length === 50 && <p className={styles.muted}>Showing your 50 most recent reflections.</p>}
  </section>;
}
