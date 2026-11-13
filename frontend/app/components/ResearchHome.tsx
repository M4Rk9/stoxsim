"use client";
import { useEffect, useState } from "react";
import { authenticatedRequest } from "../campus/client";
import { localDay, reviewDate, type StrategyPlan } from "../lib/strategy";
import styles from "./ResearchHome.module.css";
type Note = { id: string; reason: string; plan?: StrategyPlan; reviewedAt?: string | null };
export default function ResearchHome({ userId }: { userId: string }) {
  const [notes, setNotes] = useState<Note[] | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let active = true; setNotes(null); setFailed(false);
    authenticatedRequest<Note[]>("reflections").then(value => { if (active && Array.isArray(value)) setNotes(value); })
      .catch(() => { if (active) setFailed(true); });
    return () => { active = false; };
  }, [userId]);
  const due = notes?.filter(note => !note.reviewedAt && note.plan?.reviewOn && note.plan.reviewOn <= localDay()) ?? [];
  const next = due[0] ?? notes?.find(note => !note.reviewedAt);
  return <section className={styles.workspace} aria-labelledby="research-home-title">
    <div className={styles.lead}><span className="eyebrow">BUILD YOUR MARKET PERSPECTIVE</span><h2 id="research-home-title">One idea. A clearer process.</h2>
      <p>Research a company, write down your view, and test it through virtual practice. Return to what the evidence taught you.</p>
      <div className={styles.actions}><a className={styles.primary} href="/#stock-search">Research a stock or ETF →</a><a href="/journal">Open your journal</a></div>
      <ol aria-label="Research journey"><li>Discover</li><li>Form a view</li><li>Practise</li><li>Review</li></ol>
    </div>
    <aside className={styles.review}><span className="eyebrow">A REASON TO RETURN</span><h3>{due.length ? "Your review is due" : "Keep your thinking in progress"}</h3>
      {next ? <><p>{next.reason}</p>{next.plan?.reviewOn && <small>Review {reviewDate(next.plan.reviewOn)}</small>}<a href={`/journal?edit=${encodeURIComponent(next.id)}`}>Revisit this idea →</a></>
      : <><p>{failed ? "Your journal could not be loaded. Open it to try again." : notes === null ? "Loading your research…" : "Save your first thesis and choose a review date. Your private notes will be here when you return."}</p><a href="/journal">Start a strategy →</a></>}
    </aside>
    <nav className={styles.tools} aria-label="Workspace tools"><a href="/finwiz"><strong>Finwiz AI</strong><span>Explain and challenge an idea</span></a><a href="/scenario-lab"><strong>Scenario Lab</strong><span>Explore portfolio what-ifs</span></a><a href="/learn"><strong>Learn</strong><span>Build your understanding</span></a><a href="/competitions"><strong>Competitions</strong><span>Explore shared practice</span></a></nav>
  </section>;
}
