"use client";

import { useEffect, useState } from "react";
import { authenticated } from "../../campus/client";
import styles from "../../campus/campus.module.css";

interface Entry { id: string; plan: string; providerId: string | null; status: string; currentPeriodEnd: string | null; paidCount: number; benefitsEnabled: boolean; benefitStatus: string; accessUntil: string | null; cancellationStatus: string; cancelAt: string | null }
interface Overview { enabled: boolean; mode: string; keyId: string | null; entries: Entry[] }
interface CheckoutInstance { open(): void; on(event: string, callback: () => void): void }
declare global { interface Window { Razorpay?: new (options: Record<string, unknown>) => CheckoutInstance } }
let checkoutScript: Promise<void> | null = null;
function loadCheckout() {
  if (window.Razorpay) return Promise.resolve();
  if (!checkoutScript) checkoutScript = new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    const timer = window.setTimeout(() => { script.remove(); checkoutScript = null; reject(new Error("Checkout took too long to load. Try again.")); }, 15000);
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => { clearTimeout(timer); resolve(); };
    script.onerror = () => { clearTimeout(timer); script.remove(); checkoutScript = null; reject(new Error("Checkout could not load.")); };
    document.head.appendChild(script);
  });
  return checkoutScript;
}
export default function BillingTestPage() {
  const [data, setData] = useState<Overview | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [consent, setConsent] = useState(false);
  async function load() { setData(await authenticated<Overview>("billing/test")); }
  async function act(action: () => Promise<void>) {
    setBusy(true); setError(""); setNotice("");
    try { await action(); } catch (cause) {
      setData(null);
      setError(cause instanceof Error ? cause.message : "Test billing request failed.");
    } finally { setBusy(false); }
  }
  useEffect(() => { void act(load); }, []);
  async function open(item: Entry) {
    if (!data?.keyId?.startsWith("rzp_test_") || !item.providerId) throw new Error("Test checkout is not ready. Refresh its status.");
    await loadCheckout();
    if (!window.Razorpay) throw new Error("Checkout is unavailable.");
    const checkout = new window.Razorpay({
      key: data.keyId, subscription_id: item.providerId, name: "StoxSim — TEST",
      description: `${item.plan} test subscription — no real charge`,
      handler: () => { setNotice("Checkout returned successfully. Refresh status to verify it with Razorpay."); },
      modal: { ondismiss: () => { setNotice("Checkout closed. You can reopen it or refresh its status."); } }
    });
    checkout.on("payment.failed", () => setError("Test payment failed. Reopen checkout to try again."));
    checkout.open();
  }
  async function create(plan: string) {
    const storageKey = `stoxsim-test-checkout-${plan}`;
    const key = sessionStorage.getItem(storageKey) ?? crypto.randomUUID();
    sessionStorage.setItem(storageKey, key);
    const item = await authenticated<Entry>("billing/test/subscriptions", { plan, requestKey: key });
    await load();
    if (["cancelled", "completed", "expired"].includes(item.status)) {
      sessionStorage.removeItem(storageKey);
      setNotice("Previous attempt is closed. Select the plan again to start a new test.");
    } else if (item.providerId) await open(item);
    else setNotice("Creation is awaiting confirmation. Refresh the page after its webhook arrives.");
  }
  const openEntry = data?.entries.some(item => !["cancelled", "completed", "expired"].includes(item.status));
  return <main id="main-content" tabIndex={-1} className={styles.shell} aria-busy={busy}>
    <nav className={styles.nav}><a href="/settings">Account settings</a><a href="/admin/analytics">Owner analytics</a></nav>
    <header className={styles.hero}><span>ADMINISTRATOR · TEST MODE</span><h1>Test subscriptions</h1>
      <p>Try Plus and Pro with simulated payments. After a confirmed payment, you can enable test benefits for separate sandbox portfolios. Standard portfolios and competition eligibility stay unchanged.</p></header>
    {busy && <p role="status">Loading…</p>}
    {error && <div role="alert">{error}</div>}
    {notice && <p role="status">{notice}</p>}
    <button disabled={busy} onClick={() => void act(load)}>Reload billing</button>
    {data && !data.enabled && <section className={styles.card}><h2>Test checkout is disabled</h2><p>Complete the test billing setup before starting a checkout.</p></section>}
    {data?.enabled && <>
      <section className={styles.card}><h2>Start a test</h2>
        <p>Monthly plans: Plus ₹99, Pro ₹199. Test subscriptions run for up to 12 billing cycles. No real money is charged.</p>
        <p>Subscription policy: purchases are non-refundable. Cancel renewal to keep benefits until the paid period ends. An unpaid checkout can be cancelled immediately.</p>
        <label className={styles.check}><input type="checkbox" checked={consent} onChange={e => setConsent(e.target.checked)} />I understand this is a simulated checkout and will use test payment details.</label>
        <div className={styles.actions}>{["PLUS", "PRO"].map(plan => <button key={plan} disabled={busy || !consent || openEntry} onClick={() => void act(() => create(plan))}>Test {plan === "PLUS" ? "Plus" : "Pro"}</button>)}</div>
        {openEntry && <p>Finish or cancel the open test before starting another plan.</p>}
      </section>
      {data.entries.map(item => <section key={item.id} className={styles.card}>
        <h2>{item.plan} · {item.status}</h2><p>Confirmed payments: {item.paidCount}</p>
        <p>Test benefits: {item.accessUntil && Date.parse(item.accessUntil) <= Date.now() ? "EXPIRED" : item.benefitStatus ?? "OFF"}</p>
        {item.accessUntil && <p>Sandbox access until: {new Date(item.accessUntil).toLocaleString()}</p>}
        {item.benefitsEnabled && <p>Manage your test sandboxes in <a href="/settings#plan">Account settings</a>. Failed renewals allow three days of grace. Confirmed cancellation ends access at the paid-through date, without additional grace.</p>}
        {item.cancellationStatus === "CONFIRMED" && <p role="status">{item.cancelAt ? `Renewal cancelled. Paid access ends: ${new Date(item.cancelAt).toLocaleString()}.` : "Cancellation confirmed."}</p>}
        {item.cancellationStatus === "REQUESTED" && <p role="alert">Cancellation is awaiting confirmation. Renewal may still occur. Refresh status, then retry cancellation if needed; if confirmation remains unavailable, check Razorpay or contact support.</p>}
        {item.currentPeriodEnd && <p>Current period ends: {new Date(item.currentPeriodEnd).toLocaleString()}</p>}
        <p className={styles.muted}>Test reference: {item.id}</p>
        <div className={styles.actions}>
          {(item.benefitsEnabled || (item.status === "active" && item.paidCount > 0)) && <button disabled={busy} onClick={() => {
            const enable = !item.benefitsEnabled;
            if (window.confirm(enable ? "Enable simulated plan benefits on your administrator account? This provisions separate test sandboxes." : "Disable test benefits? Sandboxes will lock and pending sandbox orders will be cancelled.")) void act(async () => {
              await authenticated(`billing/test/subscriptions/${item.id}/benefits`, { enabled: enable }); await load();
            });
          }}>{item.benefitsEnabled ? "Disable test benefits" : "Enable test benefits"}</button>}
          {item.providerId && ["created", "authenticated"].includes(item.status) && <button disabled={busy || !consent} onClick={() => void act(() => open(item))}>Open test checkout</button>}
          <button disabled={busy || !item.providerId} onClick={() => void act(async () => { await authenticated(`billing/test/subscriptions/${item.id}/refresh`, null); await load(); })}>Refresh status</button>
          {item.cancellationStatus !== "CONFIRMED" && !["cancelled", "completed", "expired"].includes(item.status) && <button disabled={busy || !item.providerId} onClick={() => {
            if (window.confirm("Cancel renewal? Paid benefits remain until the paid period ends. No refund is issued. Unpaid checkouts close immediately.")) void act(async () => {
              await authenticated(`billing/test/subscriptions/${item.id}/cancel`, null);
              sessionStorage.removeItem(`stoxsim-test-checkout-${item.plan}`); await load();
            });
          }}>{item.cancellationStatus === "REQUESTED" ? "Retry cancellation" : item.paidCount > 0 ? "Cancel renewal" : "Cancel test subscription"}</button>}
        </div>
        {!item.providerId && <><p>Creation is awaiting confirmation. Find the subscription with this test reference in your Razorpay test dashboard.</p>
          <button disabled={busy} onClick={() => {
            const providerId = window.prompt("Subscription ID from Razorpay test dashboard (sub_…)");
            if (providerId) void act(async () => {
              await authenticated(`billing/test/subscriptions/${item.id}/reconcile`, { providerId: providerId.trim() }); await load();
            });
          }}>Reconcile test subscription</button></>}
      </section>)}
    </>}
  </main>;
}
