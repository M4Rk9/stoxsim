"use client";

import styles from "./FinwizTradeFeedback.module.css";

export interface FinwizPortfolioFeedback {
  orderId: string;
  marketRegion: "INDIA" | "UNITED_STATES";
  feedbackVersion: string;
  formulaVersion: string;
  status: "AVAILABLE" | "LIMITED_DATA" | "NOT_ENOUGH_DATA";
  scoreBefore?: number;
  scoreAfter?: number;
  scoreChange?: number;
  headline: string;
  observations: string[];
  suggestedQuestions: string[];
  confidence: "HIGH" | "MEDIUM" | "LOW" | "NONE";
  generatedAt: string;
  disclaimer: string;
}

interface Props {
  feedback: FinwizPortfolioFeedback;
  onDismiss: () => void;
}

const score = (value?: number) => value == null ? "—" : value;
const change = (value?: number) => value == null ? "—" : `${value > 0 ? "+" : ""}${value}`;

export default function FinwizTradeFeedback({ feedback, onDismiss }: Props) {
  const changeTone = feedback.scoreChange == null
    ? ""
    : feedback.scoreChange > 0
      ? styles.positive
      : feedback.scoreChange < 0
        ? styles.negative
        : "";

  return <section className={`panel ${styles.card}`} aria-labelledby="finwiz-trade-feedback-title" role="status">
    <div className={styles.header}>
      <div>
        <span className="kicker">POST-TRADE LEARNING</span>
        <h2 id="finwiz-trade-feedback-title">FinWiz portfolio feedback</h2>
        <p>{feedback.headline}</p>
      </div>
      <button type="button" className={styles.dismiss} onClick={onDismiss} aria-label="Dismiss FinWiz portfolio feedback">×</button>
    </div>

    <div className={styles.body}>
      <div className={styles.scoreChange} aria-label="StoxScore change">
        <div><span>Before</span><strong>{score(feedback.scoreBefore)}</strong></div>
        <i aria-hidden="true">→</i>
        <div><span>After</span><strong>{score(feedback.scoreAfter)}</strong></div>
        <div><span>Change</span><strong className={changeTone}>{change(feedback.scoreChange)}</strong></div>
      </div>

      <div className={styles.explanation}>
        <ul>{feedback.observations.map((observation) => <li key={observation}>{observation}</li>)}</ul>
        <div className={styles.questions}>
          <span>Questions to explore</span>
          <div>{feedback.suggestedQuestions.slice(0, 2).map((question) => <span key={question}>{question}</span>)}</div>
        </div>
        <div className={styles.actions}>
          <a href="/finwiz">Explore portfolio risk with FinWiz</a>
          <span>{feedback.confidence.toLowerCase()} confidence · {feedback.formulaVersion}</span>
        </div>
      </div>
    </div>

    <footer>{feedback.disclaimer}</footer>
  </section>;
}
