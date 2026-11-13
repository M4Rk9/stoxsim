import Brand from "../components/Brand";
import DecisionReflections from "../components/DecisionReflections";
import styles from "./journal.module.css";
export default function JournalPage() {
  return <main className={styles.shell} id="main-content" tabIndex={-1}>
    <header className={styles.header}><Brand /><a href="/#stock-search">Explore markets →</a></header>
    <div className={styles.intro}><span className="eyebrow">YOUR THINKING, OVER TIME</span>
      <h1>Strategy journal</h1><p>Write the view. Test it with virtual money. Return to the evidence.</p>
    </div>
    <nav className={styles.tools} aria-label="Research and practice tools"><a href="/finwiz">Examine an idea with Finwiz</a><a href="/portfolio">Review portfolio</a><a href="/scenario-lab">Scenario Lab</a><a href="/competitions">Competitions</a></nav>
    <DecisionReflections journal />
    <p className={styles.note}>A profitable result does not prove a sound decision. Review your evidence, assumptions and risk choices separately from the outcome.</p>
  </main>;
}
