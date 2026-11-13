"use client";
import { useState } from "react";
import { authenticatedRequest } from "../campus/client";
import type { StrategyPlan } from "../lib/strategy";
import styles from "./LearnerExperience.module.css";
type Account = { id: string; marketRegion: string; accountKind: string };
type Order = { id: string; symbol: string; exchange: string; side: string; quantity: number; status: string; createdAt: string };
export default function OrderLinker({ strategyId, plan, onLinked }: { strategyId: string; plan?: StrategyPlan; onLinked: () => void }) {
  const [accounts, setAccounts] = useState<Account[] | null>(null), [orders, setOrders] = useState<Order[]>([]);
  const [account, setAccount] = useState(""), [order, setOrder] = useState(""), [busy, setBusy] = useState(false), [error, setError] = useState("");
  async function loadAccounts() {
    setBusy(true); setError("");
    try { setAccounts(await authenticatedRequest<Account[]>("accounts")); } catch { setError("Your accounts could not be loaded. Try again."); } finally { setBusy(false); }
  }
  async function loadOrders(id: string) {
    setAccount(id); setOrders([]); setOrder(""); setError(""); if (!id) return;
    setBusy(true);
    try { const values = await authenticatedRequest<Order[]>(`accounts/${id}/orders`); setOrders(values.filter(o => !plan?.symbol || (o.symbol === plan.symbol && o.exchange === plan.exchange))); }
    catch { setError("Orders could not be loaded. Try again."); } finally { setBusy(false); }
  }
  return <div className={styles.orderOutcome}>
    {!accounts ? <button type="button" className={styles.secondary} disabled={busy} onClick={() => void loadAccounts()}>Link an existing practice order</button>
    : <><label>Practice account<select disabled={busy} value={account} onChange={e => void loadOrders(e.target.value)}><option value="">Choose an account</option>{accounts.map((a, index) => <option key={a.id} value={a.id}>{a.marketRegion === "INDIA" ? "India" : "US"} · {a.accountKind === "SANDBOX" ? "Extra practice" : "Main practice"} · {index + 1}</option>)}</select></label>
      <label>Matching order<select disabled={busy} value={order} onChange={e => setOrder(e.target.value)}><option value="">Choose an existing order</option>{orders.map(o => <option key={o.id} value={o.id}>{o.symbol} · {o.side.toLowerCase()} {o.quantity} · {o.status.toLowerCase()} · {new Date(o.createdAt).toLocaleString()}</option>)}</select></label>
      {account && !orders.length && !busy && <p>No matching orders were found in this account.</p>}
      <button type="button" disabled={!order || busy} onClick={async () => { setBusy(true); setError(""); try { await authenticatedRequest(`reflections/${strategyId}/order`, { method: "POST", body: JSON.stringify({ orderId: order }) }); onLinked(); } catch { setError("The order could not be linked. Check that it belongs to this strategy and try again."); } finally { setBusy(false); } }}>Link selected order</button></>}
    {error && <p role="alert">{error}</p>}
  </div>;
}
