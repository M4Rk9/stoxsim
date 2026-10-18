"use client";

import { FeatureGuide } from "../components/FeatureJourney";
import Image from "next/image";
import { useEffect, useState } from "react";
import { authenticated } from "../campus/client";
import { marketAccount } from "../lib/portfolio-selection";
import ScenarioLab from "../portfolio/ScenarioLab";
import styles from "./scenario.module.css";
type Account = { id: string; marketRegion: string; accountKind: string; active: boolean; sandboxSlot: number; accountLabel?: string };
export default function ScenarioLabPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAccount, setSelectedAccount] = useState("");
  const [region, setRegion] = useState("INDIA");
  const [error, setError] = useState("");
  const [loaded, setLoaded] = useState(false);
  useEffect(() => {
    let active = true;
    authenticated<Account[]>("accounts").then(data => {
      if (!active) return;
      setAccounts(data);
      const saved = data.find(a => a.id === sessionStorage.getItem("stoxsim-active-account"));
      if (saved) { setRegion(saved.marketRegion); setSelectedAccount(saved.id); }
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Unable to load your account."); })
      .finally(() => { if (active) setLoaded(true); });
    return () => { active = false; };
  }, []);
  const account = accounts.find(a => a.id === selectedAccount && a.marketRegion === region && a.active) ?? marketAccount(accounts, region);
  return <main className={styles.shell} id="main-content">
    <header className={styles.header}><a href="/" className={styles.brand}><Image src="/stoxsim-logo.png" alt="" width={36} height={36} />StoxSim <span>/ Lab</span></a><a href="/portfolio">Your portfolio ↗</a></header>
    <FeatureGuide title="Turn a trade into a lesson">Select the portfolio holding your executed trade, choose Market sell-off and review the preview and available credits. Run the scenario, then compare projected cash and holdings. Your practice portfolio stays unchanged.</FeatureGuide>
    <section className={styles.hero}>
      <div><span className={styles.eyebrow}>A LITTLE CURIOSITY. A WORLD OF POSSIBILITIES.</span><h1>What if<span>?</span></h1><p>Your ideas. Your market. Explore what could happen.</p></div>
      <div className={styles.marketTabs} role="group" aria-label="Scenario market">
        <button aria-pressed={region === "INDIA"} onClick={() => setRegion("INDIA")}>India · INR</button>
        <button aria-pressed={region === "UNITED_STATES"} onClick={() => setRegion("UNITED_STATES")}>US · USD</button>
      </div>
    </section>
    {account && <label className={styles.accountPicker}>Portfolio to explore
      <select value={account.id} onChange={event => setSelectedAccount(event.target.value)} aria-label="Portfolio to explore">
        {accounts.filter(a => a.marketRegion === region && a.active).map(a => <option key={a.id} value={a.id}>
          {a.accountLabel || `${a.accountKind === "STANDARD" ? "Standard" : `Sandbox ${a.sandboxSlot}`} ${a.marketRegion === "INDIA" ? "India" : "US"}`}
        </option>)}
      </select>
      <small>For your first-trade walkthrough, choose the standard portfolio where your trade executed.</small>
    </label>}
    {error ? <p className={styles.error} role="alert">{error} <a href="/">Back to sign in</a></p> : account ? <ScenarioLab key={account.id} accountId={account.id} /> : <p role="status">{loaded ? "No active portfolio available for this market." : "Opening your laboratory…"}</p>}
  </main>;
}
