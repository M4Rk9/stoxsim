"use client";
import { useEffect, useState } from "react";
import styles from "../../learn/learn.module.css";
import controls from "../../components/LearnerExperience.module.css";
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function UnsubscribePage() {
  const [token, setToken] = useState<string | null>(null), [busy, setBusy] = useState(false), [done, setDone] = useState(false), [error, setError] = useState("");
  useEffect(() => { const value = new URLSearchParams(window.location.search).get("token"); setToken(value && value.length <= 256 ? value : null); }, []);
  async function unsubscribe() {
    if (!token || busy) return; setBusy(true); setError("");
    try {
      const response = await fetch(`${API_URL}/api/v1/notifications/unsubscribe?token=${encodeURIComponent(token)}`, { method: "POST", credentials: "omit", cache: "no-store", referrerPolicy: "no-referrer" });
      if (!response.ok) throw new Error("This request could not be completed. Try again or update notification preferences in Account settings.");
      setDone(true); setToken(null); window.history.replaceState(null, "", "/notifications/unsubscribe");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Please try again."); } finally { setBusy(false); }
  }
  return <main className={styles.shell} id="main-content" tabIndex={-1}><section className={controls.card}>
    <h1>Optional notification emails</h1><p>Confirm below to stop optional notification emails. Security-critical messages and your separate weekly portfolio report settings are unaffected.</p>
    {done ? <p role="status">Your unsubscribe request has been processed. You can review your preferences in Account settings.</p> : token ? <button disabled={busy} onClick={() => void unsubscribe()}>{busy ? "Updating…" : "Stop optional notification emails"}</button> : <p>Use the unsubscribe link from your email, or manage preferences in Account settings.</p>}
    {error && <p role="alert">{error}</p>}<p><a href="/settings#notifications">Open notification preferences</a></p>
  </section></main>;
}
