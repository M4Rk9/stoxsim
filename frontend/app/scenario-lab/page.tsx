"use client";
import Image from "next/image";
import { useEffect, useState } from "react";
import { authenticated } from "../campus/client";
import { marketAccount } from "../lib/portfolio-selection";
import ScenarioLab from "../portfolio/ScenarioLab";
import styles from "./scenario.module.css";
type Account = { id: string; marketRegion: string; accountKind: string; active: boolean; sandboxSlot: number };
export default function ScenarioLabPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [region, setRegion] = useState("INDIA");
  const [error, setError] = useState("");
  const [loaded, setLoaded] = useState(false);
  useEffect(() => {
    let active = true;
    authenticated<Account[]>("accounts").then(data => {
      if (!active) return;
      setAccounts(data);
      const saved = data.find(a => a.id === sessionStorage.getItem("stoxsim-active-account"));
      if (saved) setRegion(saved.marketRegion);
    }).catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Unable to load your account."); })
      .finally(() => { if (active) setLoaded(true); });
    return () => { active = false; };
  }, []);
  const account = marketAccount(accounts, region);
  return <main className={styles.shell} id="main-content">
    <header className={styles.header}><a href="/" className={styles.brand}><Image src="/stoxsim-logo.png" alt="" width={36} height={36} />StoxSim <span>/ Lab</span></a><a href="/portfolio">Your portfolio ↗</a></header>
    <section className={styles.hero}>
      <div><span className={styles.eyebrow}>A LITTLE CURIOSITY. A WORLD OF POSSIBILITIES.</span><h1>What if<span>?</span></h1><p>Your ideas. Your market. Explore what could happen.</p></div>
      <div className={styles.marketTabs} role="group" aria-label="Scenario market">
        <button aria-pressed={region === "INDIA"} onClick={() => setRegion("INDIA")}>India · INR</button>
        <button aria-pressed={region === "UNITED_STATES"} onClick={() => setRegion("UNITED_STATES")}>US · USD</button>
      </div>
    </section>
    {error ? <p className={styles.error} role="alert">{error} <a href="/">Back to sign in</a></p> : account ? <ScenarioLab key={account.id} accountId={account.id} /> : <p role="status">{loaded ? "No active portfolio available for this market." : "Opening your laboratory…"}</p>}
  </main>;
}
