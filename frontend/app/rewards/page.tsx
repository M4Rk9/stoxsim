"use client";

import { useEffect, useState } from "react";
import { ApiError, authenticated } from "../campus/client";
import styles from "../campus/campus.module.css";

type Capital = { inr: number; usd: number };
type Overview = {
  enabled: boolean; canInvite: boolean; canReview: boolean; code?: string; link?: string;
  rewardInr: number; rewardUsd: number; minimumAgeHours: number; qualificationDays: number; pending: number; successful: number;
  earned: Capital;
  referrals: { id: string; status: string; createdAt: string }[];
  history: (Capital & { id: string; source: string; createdAt: string })[];
};
type Reconciliation = { unbalancedAccounts: number; unmatchedReferrals: number };
type Review = { id: string; referrerId: string; referredId: string; qualifiedAt: string };

const money = (value: number, currency: "INR" | "USD") =>
  new Intl.NumberFormat(currency === "INR" ? "en-IN" : "en-US", { style: "currency", currency, maximumFractionDigits: 2 }).format(value);
const date = (value: string) => new Date(value).toLocaleDateString();
const statuses: Record<string, string> = { PENDING: "Pending", QUALIFIED: "Qualified", REWARDED: "Rewarded", REJECTED: "Rejected" };

export default function RewardsPage() {
  const [rewards, setRewards] = useState<Overview>();
  const [queue, setQueue] = useState<Review[]>([]);
  const [reconciliation, setReconciliation] = useState<Reconciliation>();
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [signIn, setSignIn] = useState(false);

  function failure(cause: unknown) {
    if (cause instanceof ApiError && cause.status === 401) {
      setRewards(undefined);
      setQueue([]);
      setReconciliation(undefined);
      setSignIn(true);
    }
    setError(cause instanceof Error ? cause.message : "Could not load rewards.");
  }

  useEffect(() => {
    async function load() {
      const current = await authenticated<Overview>("rewards");
      setRewards(current);
      if (current.canReview) setQueue(await authenticated<Review[]>("rewards/review"));
    }
    void load().catch(failure).finally(() => setBusy(false));
  }, []);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError("");
    setNotice("");
    try { await action(); }
    catch (cause) { failure(cause); }
    finally { setBusy(false); }
  }

  function review(item: Review, decision: "approve" | "reject") {
    const message = decision === "approve" ? "Credit both reward practice accounts for this referral?" : "Reject this referral?";
    if (!window.confirm(message)) return;
    void run(async () => {
      await authenticated("rewards/review/" + item.id + "/" + decision, null);
      setQueue(current => current.filter(entry => entry.id !== item.id));
      setNotice(decision === "approve" ? "Both practice balances were credited." : "Referral rejected.");
    });
  }

  return <main className={styles.shell}>
    <nav className={styles.nav} aria-label="Rewards navigation"><a href="/">StoxSim</a><a href="/settings">Account settings</a></nav>
    <header className={styles.hero}><h1>Referral rewards</h1><p>Invite someone to practise on StoxSim and earn virtual capital when their referral is approved.</p></header>
    {busy && !rewards && <p role="status">Loading rewards…</p>}
    {error && <p role="alert" className={styles.alert}>{error} {signIn && <a href="/">Sign in</a>}</p>}
    {notice && <p role="status" className={styles.notice}>{notice}</p>}
    {rewards && <>
      <section className={styles.card}>
        <h2>Your invite link</h2>
        {!rewards.enabled ? <p>New referrals and approvals are paused.</p> : !rewards.canInvite
          ? <p>{rewards.canReview ? "Administrators can review referrals below." : "Verify your email to create an invite link."}</p>
          : rewards.link ? <>
            <p>Invite code <strong>{rewards.code}</strong></p>
            <label>Shareable link<input readOnly value={rewards.link} onFocus={event => event.currentTarget.select()} /></label>
            <div className={styles.actions}>
              <button disabled={busy} onClick={() => void run(async () => {
                await navigator.clipboard.writeText(rewards.link!);
                setNotice("Invite link copied.");
              })}>Copy link</button>
              <button className={styles.secondary} disabled={busy} onClick={() => void run(async () => {
                if (typeof navigator.share !== "function") {
                  window.location.href = "mailto:?subject=Practise%20on%20StoxSim&body=" + encodeURIComponent(rewards.link!);
                  return;
                }
                try { await navigator.share({ title: "Practise on StoxSim", url: rewards.link }); }
                catch (cause) { if (!(cause instanceof DOMException && cause.name === "AbortError")) throw cause; }
              })}>Share link</button>
            </div>
          </> : <button disabled={busy} onClick={() => void run(async () => {
            setRewards(await authenticated<Overview>("rewards/code", null));
          })}>Create invite link</button>}
        <p>A new learner must sign up with your link, verify their email, then complete an executed paper trade in a main practice account within {rewards.qualificationDays} days. {rewards.minimumAgeHours > 0 && <>Their account must also be at least {rewards.minimumAgeHours} hours old. </>}Eligible referrals go to an administrator for approval.</p>
        <p>Each approval credits {money(rewards.rewardInr, "INR")} and {money(rewards.rewardUsd, "USD")} in virtual capital to your separate referral practice accounts.</p>
      </section>
      <section className={styles.card}>
        <h2>Your referrals</h2>
        <div className={styles.metrics}>
          <div><strong>{rewards.pending}</strong>Pending or qualified</div>
          <div><strong>{rewards.successful}</strong>Rewarded</div>
          <div><strong>{money(rewards.earned.inr, "INR")}</strong>Net virtual INR credited</div>
          <div><strong>{money(rewards.earned.usd, "USD")}</strong>Net virtual USD credited</div>
        </div>
        {rewards.referrals.length === 0 ? <p>No referrals yet.</p> : rewards.referrals.map(item =>
          <div className={styles.row} key={item.id}><span>{date(item.createdAt)}</span><strong>{statuses[item.status] ?? item.status}</strong></div>)}
      </section>
      <section className={styles.card}>
        <h2>Reward history</h2>
        {rewards.history.length === 0 ? <p>No rewards have been credited.</p> : rewards.history.map(entry =>
          <div className={styles.row} key={entry.id}>
            <span>{entry.source === "ADMIN_CORRECTION" ? "Reward correction" : "Referral reward"} · {date(entry.createdAt)}</span>
            <strong>{money(entry.inr, "INR")} · {money(entry.usd, "USD")}</strong>
          </div>)}
        <p>For balances and trades, select a Reward practice account from <a href="/portfolio">Other portfolios</a>.</p>
      </section>
      {rewards.canReview && <section className={styles.card}>
        <h2>Reward reconciliation</h2>
        <button disabled={busy} onClick={() => void run(async () => {
          setReconciliation(await authenticated<Reconciliation>("rewards/reconciliation"));
        })}>Check ledger</button>
        {reconciliation && <p role="status">{reconciliation.unbalancedAccounts} account balance mismatches · {reconciliation.unmatchedReferrals} unmatched referral grants</p>}
      </section>}
      {rewards.canReview && <section className={styles.card}>
        <h2>Qualified referrals awaiting review</h2>
        {queue.length === 0 ? <p>No qualified referrals awaiting review.</p> : queue.map(item =>
          <div className={styles.row} key={item.id}>
            <div><strong>Qualified {date(item.qualifiedAt)}</strong><p>Referral {item.id}</p><p>Inviter {item.referrerId} · Learner {item.referredId}</p></div>
            <div className={styles.actions}>
              <button disabled={busy || !rewards.enabled} onClick={() => review(item, "approve")}>Approve</button>
              <button className={styles.secondary} disabled={busy} onClick={() => review(item, "reject")}>Reject</button>
            </div>
          </div>)}
      </section>}
    </>}
  </main>;
}
