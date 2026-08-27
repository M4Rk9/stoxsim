"use client";

import styles from "./StoxScoreCard.module.css";

export interface PortfolioAnalytics {
  marketRegion: "INDIA" | "UNITED_STATES";
  formulaVersion: string;
  status: "AVAILABLE" | "LIMITED_DATA" | "NOT_ENOUGH_DATA";
  stoxScore?: number;
  structureBand: string;
  confidence: "HIGH" | "MEDIUM" | "LOW" | "NONE";
  dataCoveragePercent: number;
  holdingCount: number;
  effectiveHoldings: number;
  largestPositionWeightPercent: number;
  topThreeWeightPercent: number;
  concentrationIndex: number;
  components: Array<{
    key: string;
    label: string;
    score: number;
    weightPercent: number;
    explanation: string;
  }>;
  largestPositions: Array<{
    symbol: string;
    weightPercent: number;
  }>;
  observations: string[];
  disclaimer: string;
  valuedAt: string;
}

interface Props {
  analytics: PortfolioAnalytics | null | undefined;
}

const formatted = (value: number, digits = 1) =>
  new Intl.NumberFormat("en-IN", { maximumFractionDigits: digits }).format(value);

export default function StoxScoreCard({ analytics }: Props) {
  if (analytics === undefined) {
    return <section className={`panel ${styles.card}`} aria-busy="true">
      <div className={styles.loading}>Calculating portfolio structure…</div>
    </section>;
  }

  if (analytics === null) {
    return <section className={`panel ${styles.card}`} aria-labelledby="stoxscore-title">
      <div className={styles.header}>
        <div>
          <span className="kicker">PORTFOLIO STRUCTURE</span>
          <h2 id="stoxscore-title">StoxScore</h2>
          <p>Portfolio structure is temporarily unavailable. Your other dashboard data is unaffected.</p>
        </div>
      </div>
    </section>;
  }

  const scored = analytics.stoxScore != null;

  return <section className={`panel ${styles.card}`} aria-labelledby="stoxscore-title">
    <div className={styles.header}>
      <div>
        <span className="kicker">PORTFOLIO STRUCTURE</span>
        <h2 id="stoxscore-title">StoxScore</h2>
        <p>A transparent view of diversification and concentration—not a return forecast.</p>
      </div>
      <span className={styles.version}>{analytics.formulaVersion}</span>
    </div>

    <div className={styles.body}>
      <div className={styles.scoreBlock}>
        <div className={styles.scoreValue}>
          <strong>{scored ? analytics.stoxScore : "—"}</strong>
          <span>{scored ? "/ 100" : "NOT SCORED"}</span>
        </div>
        <h3>{analytics.structureBand}</h3>
        <p>{scored
          ? `${analytics.confidence.toLowerCase()} confidence · ${formatted(analytics.dataCoveragePercent)}% pricing coverage`
          : analytics.observations[0]}</p>
      </div>

      {scored && <>
        <div className={styles.metrics}>
          <div><span>Holdings</span><strong>{analytics.holdingCount}</strong></div>
          <div><span>Effective holdings</span><strong>{formatted(analytics.effectiveHoldings, 2)}</strong></div>
          <div><span>Largest position</span><strong>{formatted(analytics.largestPositionWeightPercent)}%</strong></div>
          <div><span>Top three</span><strong>{formatted(analytics.topThreeWeightPercent)}%</strong></div>
        </div>

        <div className={styles.components}>
          {analytics.components.map((component) => <article key={component.key}>
            <div><strong>{component.label}</strong><span>{component.score}/100 · {component.weightPercent}% weight</span></div>
            <div className={styles.track} role="meter" aria-label={component.label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={component.score}>
              <span style={{ width: `${component.score}%` }} />
            </div>
            <p>{component.explanation}</p>
          </article>)}
        </div>

        <div className={styles.concentration}>
          <span>Largest positions</span>
          <div>{analytics.largestPositions.map((position) => <span key={position.symbol}>
            <strong>{position.symbol}</strong> {formatted(position.weightPercent)}%
          </span>)}</div>
        </div>
      </>}
    </div>

    <footer className={styles.disclaimer}>{analytics.disclaimer}</footer>
  </section>;
}
