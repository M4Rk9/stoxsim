import LearningMap from "./LearningMap";
import styles from "./learn.module.css";
import DecisionReflections from "../components/DecisionReflections";

export default function LearnPage() {
  return <main className={styles.shell} id="main-content" tabIndex={-1}>
    <header><a href="/">StoxSim</a><p>LEARN AT YOUR PACE</p><h1>One useful next step</h1>
      <p>Explore an idea, practise it, then reflect on what you learned.</p></header>
    <section className={styles.grid} aria-label="Learning tools">
      <a href="/progress"><h2>Your learning path</h2><p>Continue a mission and review your progress.</p><strong>Continue learning →</strong></a>
      <a href="/finwiz"><h2>Finwiz AI</h2><p>Ask for an educational explanation of a financial concept.</p><strong>Ask Finwiz →</strong></a>
      <a href="/scenario-lab"><h2>Scenario Lab</h2><p>Explore a what-if with your simulated portfolio.</p><strong>Explore scenarios →</strong></a>
    </section>
    <LearningMap />
    <DecisionReflections />
  </main>;
}
