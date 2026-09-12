import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";

import styles from "./MarketingPage.module.css";

type Highlight = { title: string; body: string };
type Step = { title: string; body: string };

export default function MarketingPage({
  eyebrow,
  title,
  introduction,
  highlights,
  stepsTitle,
  stepsIntroduction,
  steps,
  note,
}: {
  eyebrow: string;
  title: string;
  introduction: string;
  highlights: Highlight[];
  stepsTitle: string;
  stepsIntroduction: string;
  steps: Step[];
  note: ReactNode;
}) {
  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <Link className={styles.brand} href="/" aria-label="StoxSim home">
          <Image className={styles.logo} src="/stoxsim-logo.png" alt="" width={42} height={42} priority />
          <span>Stox<span>Sim</span></span>
        </Link>
        <nav className={styles.nav} aria-label="Product">
          <Link href="/paper-trading">Paper trading</Link>
          <Link href="/stock-market-simulator-india">India simulator</Link>
          <Link href="/learn-stock-trading">Learn</Link>
          <Link className={styles.primaryLink} href="/#get-started">Start free</Link>
        </nav>
      </header>

      <main className={styles.main} id="main-content" tabIndex={-1}>
        <section className={styles.hero}>
          <p className={styles.eyebrow}>{eyebrow}</p>
          <h1>{title}</h1>
          <p>{introduction}</p>
        </section>

        <section className={styles.highlights} aria-label="Key benefits">
          {highlights.map((highlight, index) => (
            <article className={styles.card} key={highlight.title}>
              <span>{String(index + 1).padStart(2, "0")}</span>
              <h2>{highlight.title}</h2>
              <p>{highlight.body}</p>
            </article>
          ))}
        </section>

        <section className={styles.steps}>
          <p className={styles.eyebrow}>HOW IT WORKS</p>
          <h2>{stepsTitle}</h2>
          <p className={styles.stepsIntro}>{stepsIntroduction}</p>
          <div className={styles.stepList}>
            {steps.map((step, index) => (
              <article className={styles.step} key={step.title}>
                <strong>Step {index + 1}</strong>
                <h3>{step.title}</h3>
                <p>{step.body}</p>
              </article>
            ))}
          </div>
          <div className={styles.note}>{note}</div>
        </section>

        <section className={styles.cta}>
          <div>
            <h2>Start practising with virtual money</h2>
            <p>Create a free StoxSim account. No brokerage account or real-money deposit is required.</p>
          </div>
          <Link href="/#get-started">Create free account</Link>
        </section>
      </main>

      <footer className={styles.footer}>
        <span>StoxSim · Educational paper trading only</span>
        <nav aria-label="Legal">
          <Link href="/terms">Terms</Link>
          <Link href="/privacy">Privacy</Link>
          <Link href="/cookies">Cookies</Link>
          <Link href="/disclaimer">Risk disclaimer</Link>
          <Link href="/status">Status</Link>
        </nav>
      </footer>
    </div>
  );
}

