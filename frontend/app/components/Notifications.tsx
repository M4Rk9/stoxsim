"use client";
import { FormEvent, useEffect, useState } from "react";
import { authenticatedRequest } from "../campus/client";
import { safeLearningLink } from "../lib/learner";
import styles from "./LearnerExperience.module.css";

interface Notice { id: string; category: string; title: string; body: string; href: string; createdAt: string; readAt: string | null }
interface Inbox { items: Notice[]; unreadCount: number }
interface Preferences { learning: boolean; competitions: boolean; portfolioEducation: boolean; productAnnouncements: boolean; promotionalMessages: boolean; pushEnabled: boolean; emailDigest: boolean }
const choices: Array<{ key: keyof Preferences; label: string }> = [
  { key: "learning", label: "Learning progress and achievements" }, { key: "competitions", label: "Competition updates" },
  { key: "portfolioEducation", label: "Portfolio learning insights" }, { key: "productAnnouncements", label: "Product announcements" },
  { key: "promotionalMessages", label: "Promotional messages and offers" }, { key: "pushEnabled", label: "Android push notifications (also requires app permission)" },
  { key: "emailDigest", label: "Optional email digests" },
];

export default function Notifications({ compact = false }: { compact?: boolean }) {
  const [data, setData] = useState<Inbox | null>(null), [preferences, setPreferences] = useState<Preferences | null>(null);
  const [busy, setBusy] = useState(false), [error, setError] = useState(""), [message, setMessage] = useState("");
  async function load() {
    const inbox = await authenticatedRequest<Inbox>("notifications");
    if (Array.isArray(inbox?.items)) setData(inbox);
  }
  useEffect(() => {
    let active = true;
    authenticatedRequest<Inbox>("notifications").then(value => { if (active && Array.isArray(value?.items)) setData(value); })
      .catch(() => { if (active && !compact) setError("Notifications are temporarily unavailable."); });
    if (!compact) authenticatedRequest<Preferences>("notifications/preferences").then(value => { if (active) setPreferences(value); })
      .catch(() => { if (active) setError("Notification preferences could not be loaded."); });
    return () => { active = false; };
  }, [compact]);
  async function act(action: () => Promise<void>) {
    if (busy) return; setBusy(true); setError(""); setMessage("");
    try { await action(); } catch (cause) { setError(cause instanceof Error ? cause.message : "Notifications could not be updated."); }
    finally { setBusy(false); }
  }
  async function read(item: Notice) {
    if (item.readAt) return;
    await authenticatedRequest(`notifications/${encodeURIComponent(item.id)}/read`, { method: "POST" });
    setData(current => current && ({ items: current.items.map(value => value.id === item.id ? { ...value, readAt: new Date().toISOString() } : value), unreadCount: Math.max(0, current.unreadCount - 1) }));
  }
  async function save(event: FormEvent) {
    event.preventDefault(); if (!preferences) return;
    await act(async () => {
      const payload = Object.fromEntries(choices.map(choice => [choice.key, Boolean(preferences[choice.key])]));
      setPreferences(await authenticatedRequest<Preferences>("notifications/preferences", { method: "PUT", body: JSON.stringify(payload) }));
      setMessage("Notification preferences saved.");
    });
  }
  const visible = (data?.items ?? []).filter(item => !compact || !item.readAt).slice(0, compact ? 2 : 50);
  if (compact && !visible.length) return null;
  return <section className={styles.card} id={compact ? undefined : "notifications"} aria-label={compact ? "Important updates" : "Notifications and preferences"}>
    <h2>{compact ? "Your updates" : "Notifications"}{Boolean(data?.unreadCount) && <span className={styles.muted}> · {data!.unreadCount} unread</span>}</h2>
    {error && <p className={styles.error} role="alert">{error}</p>}{message && <p role="status">{message}</p>}
    {!compact && <div className={styles.actions}>
      <button className={styles.secondary} disabled={busy} onClick={() => void act(load)}>Refresh notifications</button>
      <button className={styles.secondary} disabled={busy || !data?.unreadCount} onClick={() => void act(async () => { await authenticatedRequest("notifications/read-all", { method: "POST" }); await load(); })}>Mark all read</button>
    </div>}
    <ul className={styles.list}>{visible.map(item => <li key={item.id}>
      <span className={styles.badge}>{item.category.toLowerCase().replaceAll("_", " ")}{!item.readAt && " · Unread"}</span>
      <h3>{item.title}</h3><p>{item.body}</p><p className={styles.muted}>{new Date(item.createdAt).toLocaleString()}</p>
      <div className={styles.actions}>{safeLearningLink(item.href) && <a href={safeLearningLink(item.href)!} onClick={() => { void read(item).catch(() => {}); }}>View update →</a>}
        {!item.readAt && <button className={styles.secondary} aria-label={`Mark read: ${item.title}`} disabled={busy} onClick={() => void act(() => read(item))}>Mark read</button>}</div>
    </li>)}</ul>
    {data && !visible.length && <p>You’re all caught up.</p>}
    {compact ? <a href="/settings#notifications">All notifications and preferences →</a> : <>
      <h3>Choose your updates</h3><p>Optional communications are your choice. Security-critical account notices remain separate from marketing. Weekly portfolio reports keep their existing settings below.</p>
      {preferences && <form onSubmit={save}>
        {choices.map(choice => <label className={styles.check} key={choice.key}><input type="checkbox" checked={Boolean(preferences[choice.key])} disabled={busy} onChange={event => setPreferences({ ...preferences, [choice.key]: event.target.checked })} />{choice.label}</label>)}
        <button disabled={busy}>{busy ? "Saving…" : "Save notification preferences"}</button>
      </form>}
    </>}
  </section>;
}
