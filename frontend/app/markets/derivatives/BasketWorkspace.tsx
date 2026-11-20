"use client";

import { useEffect, useRef, useState } from "react";
import { authenticatedRequest } from "../../campus/client";
import styles from "./derivatives.module.css";

export interface BasketDraft {
  contract: { id: string; providerKey?: string; underlyingKey?: string; symbol: string;
    kind: "FUT" | "CE" | "PE"; expiry: string; lotSize: number; strike: number | null };
  action: "OPEN_LONG" | "OPEN_SHORT";
  lots: number;
}
interface Point { underlying: number; pnl: number }
interface Review {
  legs: { contract: BasketDraft["contract"]; action: BasketDraft["action"]; lots: number; quantity: number;
    price: number; requiredFunds: number; quoteAt: string }[];
  requiredFunds: number; cashAfter: number;
  payoff: { points: Point[]; breakevens: number[]; zeroRanges: {from: number; to: number | null}[];
    maximumLoss: number | null; unlimitedLoss: boolean; maximumProfit: number | null; unlimitedProfit: boolean };
}
const money = (n: number | null) => n == null ? "Unavailable" : new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(n);
const time = (s: string) => new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", dateStyle: "medium", timeStyle: "medium" }).format(new Date(s));

export default function BasketWorkspace({ legs, onChange, disabled, onBusy, onFilled }: {
  legs: BasketDraft[]; onChange: (legs: BasketDraft[]) => void; disabled: boolean;
  onBusy: (value: boolean) => void; onFilled: (message: string) => void;
}) {
  const [review, setReview] = useState<Review>();
  const [accepted, setAccepted] = useState(false);
  const [until, setUntil] = useState(0);
  const [now, setNow] = useState(0);
  const [error, setError] = useState("");
  const [attempted, setAttempted] = useState(false);
  const pending = useRef(false);
  const key = useRef<{signature: string; value: string} | undefined>(undefined);
  const payload = legs.map(l => ({ contractId: l.contract.id, action: l.action, lots: l.lots }));
  const signature = JSON.stringify(payload);
  useEffect(() => { setReview(undefined); setAccepted(false); setError(""); setAttempted(false); }, [signature]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const valid = review && now < until;
  async function inspect() {
    if (pending.current || disabled) return;
    pending.current = true; onBusy(true); setError(""); setReview(undefined); setAccepted(false);
    try {
      const result = await authenticatedRequest<Review>("derivatives/baskets/preview", {
        method: "POST", body: JSON.stringify({ legs: payload }),
      });
      setReview(result); setUntil(Date.now() + 30000); setNow(Date.now());
    } catch (e) { setError(e instanceof Error ? e.message : "Basket review is unavailable."); }
    finally { pending.current = false; onBusy(false); }
  }
  async function execute() {
    if (pending.current || disabled || !review || Date.now() >= until || !accepted) return;
    if (key.current?.signature !== signature) key.current = { signature, value: crypto.randomUUID() };
    pending.current = true; onBusy(true); setError("");
    try {
      const result = await authenticatedRequest<{basketId: string; fills: {id: string}[]}>("derivatives/baskets/orders", {
        method: "POST", body: JSON.stringify({ requestKey: key.current.value, legs: payload }),
      });
      key.current = undefined; onChange([]);
      onFilled(`Virtual basket filled: ${result.fills.length} legs. Basket ${result.basketId.slice(0, 8)}.`);
    } catch (e) {
      setAttempted(true);
      setError(e instanceof Error ? e.message : "Basket submission could not be confirmed.");
    } finally { pending.current = false; onBusy(false); }
  }
  return <section className={styles.card} aria-labelledby="basket-heading">
    <h2 id="basket-heading">Strategy basket</h2>
    <p>Combine 2–4 distinct opening legs on the same index and expiry. Options are buy-only. Add a contract from its order ticket.</p>
    {!legs.length && <p>No legs yet. A long call and long put can be combined to examine a long straddle or strangle.</p>}
    {legs.map((leg, i) => <div className={styles.future} key={leg.contract.id}>
      <div><strong>{leg.contract.symbol}</strong><small>{leg.action === "OPEN_LONG" ? "Long" : "Short"} · {leg.contract.expiry} · {leg.contract.lotSize} units per lot</small>
        <small>{leg.contract.providerKey ?? leg.contract.id}</small></div>
      <div className={styles.controls}>
        <label>Leg {i + 1} lots <input aria-label={`Leg ${i + 1} lots`} type="number" min={1} max={100} step={1} value={leg.lots} disabled={disabled}
          onChange={e => onChange(legs.map((l, j) => j === i ? { ...l, lots: Number(e.target.value) } : l))} /></label>
        <button type="button" disabled={disabled} onClick={() => onChange(legs.filter((_, j) => i !== j))}>Remove {leg.contract.symbol}</button>
      </div>
    </div>)}
    {legs.length > 0 && <div className={styles.controls}>
      <button type="button" disabled={disabled || legs.length < 2 || legs.some(l => !Number.isInteger(l.lots) || l.lots < 1 || l.lots > 100)} onClick={inspect}>Review basket risk</button>
      <button type="button" disabled={disabled} onClick={() => onChange([])}>Clear basket</button>
    </div>}
    {review && <section aria-label="Combined basket risk review" className={styles.preview}>
      <h3>Combined expiry outcome</h3>
      <p>Underlying: {review.legs[0].contract.underlyingKey?.replace("NSE_INDEX|", "")} · NSE · Expiry {review.legs[0].contract.expiry}</p>
      {review.legs.map(l => <p key={l.contract.id}><strong>{l.contract.symbol}</strong> · {l.action === "OPEN_LONG" ? "Long" : "Short"} · {l.quantity} units ·
        Estimated price {money(l.price)} · Required {money(l.requiredFunds)}<br />Quote: {time(l.quoteAt)} IST</p>)}
      <dl className={styles.totals}>
        <div><dt>Total premium and margin</dt><dd>{money(review.requiredFunds)}</dd></div>
        <div><dt>Cash after fill</dt><dd>{money(review.cashAfter)}</dd></div>
        <div><dt>Maximum expiry loss</dt><dd>{review.payoff.unlimitedLoss ? "Unbounded" : money(review.payoff.maximumLoss)}</dd></div>
        <div><dt>Maximum expiry profit</dt><dd>{review.payoff.unlimitedProfit ? "Unbounded" : money(review.payoff.maximumProfit)}</dd></div>
        <div><dt>Approximate breakeven levels</dt><dd>{review.payoff.breakevens.length ? review.payoff.breakevens.map(money).join(", ") : "None"}</dd></div>
      </dl>
      {review.payoff.zeroRanges.map((r, i) => <p key={i}>Zero P&amp;L from {money(r.from)} {r.to == null ? "upwards" : `to ${money(r.to)}`}.</p>)}
      <PayoffChart points={review.payoff.points} />
      <p>This is a calculated expiry payoff, not a price forecast. It covers only these new legs, assumes a nonnegative index and holds every leg to the same expiry.
        The chart shows a limited index range; loss and profit bounds also account for the tail beyond it.</p>
      <p>Simulated fees: ₹0. Taxes and additional slippage are excluded. Futures reserve 20% margin per leg with no hedge discount, daily cash mark-to-market or forced liquidation.
        Losses can exceed reserved margin. Missing verified index-close data delays cash settlement; settlement is processed per position, not as one basket.</p>
      <p>All legs commit together or none do. Quotes can change after review and may have different observation times.
        Execution rechecks every leg and combined funds. This is not a locked price or a live broker basket. Existing positions merge by contract; close them individually.</p>
      <label className={styles.acknowledge}><input type="checkbox" checked={accepted} disabled={disabled || !valid} onChange={e => setAccepted(e.target.checked)} />
        I understand the combined risk and simulation assumptions.</label>
      {!valid && <p role="status">Basket review expired. Review the basket again.</p>}
      <div className={styles.controls}><button type="button" disabled={disabled || !valid || !accepted} onClick={execute}>Place virtual basket</button></div>
    </section>}
    {attempted && <p>Check your fills before changing this basket. Retrying the unchanged basket uses the same request key and will not duplicate a completed submission.</p>}
    {error && <p role="alert">{error}</p>}
    <p>Drafts stay in this page only. Reloading discards unsubmitted legs. No automatic submissions or retries.</p>
  </section>;
}

function PayoffChart({ points }: {points: Point[]}) {
  const maxX = Math.max(1, ...points.map(p => p.underlying));
  const minY = Math.min(0, ...points.map(p => p.pnl));
  const maxY = Math.max(1, ...points.map(p => p.pnl));
  const x = (v: number) => 55 + v / maxX * 630;
  const y = (v: number) => 225 - (v - minY) / (maxY - minY || 1) * 195;
  return <figure className={styles.payoff}>
    <svg viewBox="0 0 740 280" role="img" aria-label="Calculated basket expiry profit and loss across index levels">
      <line x1="55" x2="685" y1={y(0)} y2={y(0)} stroke="currentColor" strokeDasharray="4 4" />
      <polyline points={points.map(p => `${x(p.underlying)},${y(p.pnl)}`).join(" ")} fill="none" stroke="var(--green)" strokeWidth="3" />
      <text x="55" y="18" fill="currentColor">P&amp;L (INR)</text>
      <text x="55" y="249" fill="currentColor">0</text><text x="685" y="249" textAnchor="end" fill="currentColor">{maxX.toLocaleString("en-IN")}</text>
      <text x="370" y="274" textAnchor="middle" fill="currentColor">Underlying index at expiry</text>
    </svg>
    <figcaption>Calculated scenarios, not historical observations. Use the table for exact index/P&amp;L values.</figcaption>
    <details><summary>Payoff values</summary><div className={styles.table}><table><thead><tr><th>Index at expiry</th><th>Basket P&amp;L</th></tr></thead>
      <tbody>{points.map((p, i) => <tr key={i}><td>{money(p.underlying)}</td><td>{money(p.pnl)}</td></tr>)}</tbody></table></div></details>
  </figure>;
}
