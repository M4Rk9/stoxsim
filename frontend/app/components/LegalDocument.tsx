import Link from "next/link";
import type { ReactNode } from "react";
import styles from "./LegalDocument.module.css";

export const LEGAL_EFFECTIVE_DATE = "19 August 2026";

export default function LegalDocument({
  title,
  summary,
  effectiveDate = LEGAL_EFFECTIVE_DATE,
  children,
}: {
  title: string;
  summary: string;
  effectiveDate?: string;
  children: ReactNode;
}) {
  return (
    <main className={styles.shell}>
      <header className={styles.header}>
        <Link className={styles.brand} href="/" aria-label="StoxSim home">
          Stox<span>Sim</span>
        </Link>
        <Link className={styles.back} href="/">Back to StoxSim</Link>
      </header>
      <article className={styles.document}>
        <p className={styles.eyebrow}>PUBLIC BETA LEGAL</p>
        <h1>{title}</h1>
        <p className={styles.summary}>{summary}</p>
        <p className={styles.updated}>Effective and last updated: {effectiveDate}</p>
        <div className={styles.content}>{children}</div>
      </article>
      <footer className={styles.footer}>
        <nav aria-label="Legal documents">
          <Link href="/terms">Terms</Link>
          <Link href="/privacy">Privacy</Link>
          <Link href="/cookies">Cookies</Link>
          <Link href="/disclaimer">Risk disclaimer</Link>
        </nav>
        <span>Educational paper trading only.</span>
      </footer>
    </main>
  );
}
