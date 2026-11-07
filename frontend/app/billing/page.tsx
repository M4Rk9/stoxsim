"use client";
import { useEffect, useState } from "react";
import { authenticatedRequest, session } from "../campus/client";
import shell from "../learn/learn.module.css";
import styles from "../components/LearnerExperience.module.css";

type Plan = "FREE" | "PLUS" | "PRO";
interface Catalog { plan: Plan; plans: Array<{ plan: Plan; displayName: string; monthlyPriceInr: number; entitlements: { finwizDescription?: string; finwizTier: string; analyticsTier: string; scenarioCredits: number } }> }
interface Entry { id: string; provider: "RAZORPAY" | "GOOGLE_PLAY"; plan: Plan; status: string; currentPeriodEnd: string | null; accessUntil: string | null; cancellationStatus: string; providerReference: string | null; managementUrl: string | null }
interface Overview { enabled: boolean; razorpayEnabled: boolean; googlePlayEnabled: boolean; keyId: string | null; entries: Entry[] }
let scriptPromise: Promise<void> | null = null;
function loadCheckout() {
  if (window.Razorpay) return Promise.resolve();
  if (!scriptPromise) scriptPromise = new Promise<void>((resolve, reject) => {
    const script = document.createElement("script"); script.src = "https://checkout.razorpay.com/v1/checkout.js";
    const fail = () => { clearTimeout(timer); script.remove(); scriptPromise = null; reject(new Error("Checkout could not load. Your reservation is preserved; try reopening it.")); };
    const timer = window.setTimeout(fail, 15000);
    script.onload = () => { clearTimeout(timer); if (window.Razorpay) resolve(); else fail(); };
    script.onerror = fail; document.head.appendChild(script);
  });
  return scriptPromise;
}
function playManagement(value: string | null) {
  try { const url = new URL(value ?? ""); return url.origin === "https://play.google.com" && url.pathname === "/store/account/subscriptions" && !url.username && !url.password ? url.href : null; } catch { return null; }
}
const terminal = new Set(["CANCELLED", "COMPLETED", "EXPIRED", "REVOKED", "REFUNDED"]);
const label = (value: string) => value.toLowerCase().replaceAll("_", " ");

export default function BillingPage() {
  const [data, setData] = useState<Overview | null>(null), [catalog, setCatalog] = useState<Catalog | null>(null);
  const [busy, setBusy] = useState(true), [consent, setConsent] = useState(false), [error, setError] = useState(""), [notice, setNotice] = useState("");
  async function load() {
    const [billing, plans] = await Promise.all([authenticatedRequest<Overview>("billing"), authenticatedRequest<Catalog>("subscription")]);
    if (!Array.isArray(billing?.entries) || !Array.isArray(plans?.plans)) throw new Error("Billing information is temporarily unavailable.");
    setData(billing); setCatalog(plans);
  }
  async function act(action: () => Promise<void>) {
    setBusy(true); setError(""); setNotice("");
    try { await action(); } catch (cause) { setError(cause instanceof Error ? cause.message : "Billing could not be updated."); }
    finally { setBusy(false); }
  }
  useEffect(() => { void act(load); }, []);
  const ready = data?.enabled && data.razorpayEnabled && Boolean(data.keyId);
  const openEntry = data?.entries.some(item => !terminal.has(item.status.toUpperCase()));
  const testMode = data?.keyId?.startsWith("rzp_test_");
  function storageKey(plan: Plan) {
    const owner = session()?.user.id;
    if (!owner) throw new Error("Sign in again before starting checkout.");
    return `stoxsim-checkout:${owner}:${plan}`;
  }
  async function open(item: Entry) {
    if (!ready || !data?.keyId || !/^rzp_(test|live)_/.test(data.keyId) || item.provider !== "RAZORPAY" || !item.providerReference || !consent) throw new Error("Checkout is unavailable. Reload billing before retrying.");
    await loadCheckout();
    if (!window.Razorpay) throw new Error("Checkout is unavailable.");
    const checkout = new window.Razorpay({ key: data.keyId, subscription_id: item.providerReference, name: "StoxSim", description: `${item.plan} monthly subscription${testMode ? " — test mode" : ""}`,
      handler: () => {
        setNotice("Checkout returned. Verifying your subscription with the provider…");
        void act(async () => { await authenticatedRequest(`billing/subscriptions/${item.id}/refresh`, { method: "POST" }); await load(); setNotice("Provider status refreshed. Paid access follows the verified subscription status below."); });
      }, modal: { ondismiss: () => setNotice("Checkout closed. Your attempt is preserved; refresh status or reopen it when ready.") } });
    checkout.on("payment.failed", () => setError("The payment was not completed. Refresh the provider status before retrying.")); checkout.open();
  }
  async function create(plan: Plan) {
    if (!ready || !consent || plan === "FREE") return;
    const key = storageKey(plan), existing = sessionStorage.getItem(key);
    const requestKey = existing && /^[0-9a-f-]{36}$/i.test(existing) ? existing : crypto.randomUUID();
    sessionStorage.setItem(key, requestKey);
    const item = await authenticatedRequest<Entry>("billing/subscriptions", { method: "POST", body: JSON.stringify({ plan, requestKey }) });
    await load();
    if (terminal.has(item.status.toUpperCase())) { sessionStorage.removeItem(key); setNotice("Your previous attempt is closed. Review the plans before starting a new checkout."); }
    else if (item.providerReference) await open(item);
    else setNotice("Checkout creation is awaiting provider verification. Keep this attempt; do not start another subscription.");
  }
  return <main className={shell.shell} id="main-content" tabIndex={-1} aria-busy={busy}>
    <header><a href="/settings#plan">Account settings</a><h1>Plans and billing</h1><p>Choose the learning tools you need. Your practice balances are virtual money.</p></header>
    {error && <p role="alert" className={styles.error}>{error}</p>}{notice && <p role="status">{notice}</p>}
    {busy && <p role="status">Loading billing…</p>}
    <div className={styles.card}><button className={styles.secondary} disabled={busy} onClick={() => void act(load)}>Reload billing</button>
      {data && !ready && <p role="status">Website subscriptions are not available yet. Existing subscription access remains managed by the server.</p>}
      {ready && <><p>Plans renew monthly until cancelled. Review the final amount, applicable taxes and payment schedule in checkout. Manage renewal below, and read the <a href="/terms">Terms</a> before purchasing.</p>
        {testMode && <p role="status">Test mode — use test payment details. No real charge.</p>}
        <label className={styles.check}><input type="checkbox" checked={consent} onChange={e => setConsent(e.target.checked)} />I have reviewed the monthly renewal terms and want to continue to provider checkout.</label></>}
      {openEntry && <p>You already have an open subscription or checkout. Manage it below before starting another.</p>}
    </div>
    {catalog && <section className={shell.grid} aria-label="Compare plans">{catalog.plans.map(plan => <article className={`${styles.card} ${styles.planCard}`} key={plan.plan}>
      <h2>{plan.displayName}</h2><strong>{plan.monthlyPriceInr === 0 ? "Free" : `₹${plan.monthlyPriceInr}/month`}</strong>
      <ul><li>{plan.entitlements.finwizDescription ?? `${label(plan.entitlements.finwizTier)} Finwiz explanations`}</li><li>{label(plan.entitlements.analyticsTier)} analytics</li><li>{plan.entitlements.scenarioCredits} Scenario Lab credits{plan.plan !== "FREE" ? " per renewal" : ""}</li></ul>
      {plan.plan === catalog.plan ? <p className={styles.badge}>Current plan</p> : plan.plan !== "FREE" && <button disabled={busy || !ready || !consent || openEntry} onClick={() => void act(() => create(plan.plan))}>Choose {plan.displayName}</button>}
    </article>)}</section>}
    <section className={styles.card} aria-label="Billing history"><h2>Subscription history</h2>
      {data && !data.entries.length && <p>No subscription records yet.</p>}
      <ul className={styles.list}>{data?.entries.map(item => <li key={item.id}>
        <h3>{item.plan} · {label(item.status)}</h3><p>{item.provider === "GOOGLE_PLAY" ? "Google Play" : "Razorpay"}</p>
        {item.accessUntil && <p>Verified access until: {new Date(item.accessUntil).toLocaleString()}</p>}
        {item.currentPeriodEnd && <p>Current period ends: {new Date(item.currentPeriodEnd).toLocaleString()}</p>}
        {item.cancellationStatus === "REQUESTED" && <p role="alert">Cancellation is awaiting provider confirmation. Renewal may still occur. Refresh status or retry cancellation.</p>}
        {item.cancellationStatus === "CONFIRMED" && <p role="status">Renewal cancellation confirmed. Access follows the verified end date above.</p>}
        {!item.providerReference && !terminal.has(item.status.toUpperCase()) && <p>Awaiting provider verification. Your checkout attempt is preserved.</p>}
        <div className={styles.actions}><button className={styles.secondary} disabled={busy} onClick={() => void act(async () => { await authenticatedRequest(`billing/subscriptions/${item.id}/refresh`, { method: "POST" }); await load(); })}>Refresh status<span className={styles.srOnly}> for {item.plan}</span></button>
          {item.provider === "RAZORPAY" && item.providerReference && ["CREATED", "AUTHENTICATED"].includes(item.status.toUpperCase()) && <button disabled={busy || !ready || !consent} onClick={() => void act(() => open(item))}>Reopen checkout</button>}
          {item.provider === "RAZORPAY" && item.providerReference && item.cancellationStatus !== "CONFIRMED" && !terminal.has(item.status.toUpperCase()) && <button className={styles.secondary} disabled={busy} onClick={() => {
            if (window.confirm("Cancel renewal? Review the provider confirmation and access end date after this request.")) void act(async () => { await authenticatedRequest(`billing/subscriptions/${item.id}/cancel`, { method: "POST" }); await load(); });
          }}>{item.cancellationStatus === "REQUESTED" ? "Retry cancellation" : "Cancel renewal"}</button>}
          {item.provider === "GOOGLE_PLAY" && playManagement(item.managementUrl) && <a href={playManagement(item.managementUrl)!} target="_blank" rel="noopener noreferrer">Manage in Google Play ↗</a>}
        </div>
      </li>)}</ul>
      <p>For invoice, refund or billing questions, contact <a href="mailto:support.stoxsim@gmail.com">StoxSim support</a>. Subscription history reflects provider-verified status.</p>
    </section>
  </main>;
}
