"use client";

import { useEffect, useRef, useState } from "react";
import styles from "./OnboardingJourney.module.css";

export interface OnboardingState {
  introductionCompleted: boolean;
  firstOrderCompleted: boolean;
  dismissed: boolean;
  completed: boolean;
  nextStep: "INTRODUCTION" | "FIRST_TRADE" | "COMPLETE" | "DISMISSED";
  introductionCompletedAt?: string;
  firstOrderPlacedAt?: string;
  dismissedAt?: string;
}

const lessons = [
  {
    eyebrow: "WELCOME TO STOXSIM",
    title: "Two markets. Zero real-money risk.",
    body: "Practise with a separate ₹5 lakh India portfolio and $10,000 US portfolio. Cash and holdings never mix between them.",
    note: "Everything here is simulated for education.",
  },
  {
    eyebrow: "READ THE MARKET",
    title: "Know how fresh every price is.",
    body: "StoxSim labels quotes as live, closed, stale or unavailable. Check the label before interpreting a price or submitting a paper order.",
    note: "Market data is context, not investment advice.",
  },
  {
    eyebrow: "YOUR FIRST MISSION",
    title: "Find a stock and place one paper trade.",
    body: "Search for a company, inspect its quote, choose a quantity and submit the paper order ticket. We will guide both steps on the dashboard.",
    note: "No brokerage order is ever created.",
  },
] as const;

interface JourneyProps {
  state: OnboardingState | null;
  onCompleteIntroduction: () => Promise<void>;
  onDismiss: () => Promise<void>;
}

export function OnboardingJourney({
  state,
  onCompleteIntroduction,
  onDismiss,
}: JourneyProps) {
  const [step, setStep] = useState(0);
  const [working, setWorking] = useState(false);
  const primaryAction = useRef<HTMLButtonElement>(null);
  const workingRef = useRef(false);
  const open = Boolean(state && !state.introductionCompleted && !state.dismissed);

  useEffect(() => {
    workingRef.current = working;
  }, [working]);

  useEffect(() => {
    if (!open) return;
    primaryAction.current?.focus();
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !workingRef.current) {
        setWorking(true);
        void onDismiss().finally(() => setWorking(false));
      }
    };
    document.addEventListener("keydown", close);
    return () => document.removeEventListener("keydown", close);
  }, [open]);

  if (!open) return null;

  const lesson = lessons[step];
  const finalStep = step === lessons.length - 1;

  async function advance() {
    if (!finalStep) {
      setStep((current) => current + 1);
      return;
    }
    setWorking(true);
    try {
      await onCompleteIntroduction();
    } finally {
      setWorking(false);
    }
  }

  async function dismiss() {
    setWorking(true);
    try {
      await onDismiss();
    } finally {
      setWorking(false);
    }
  }

  return <div className={styles.backdrop} role="presentation">
    <section
      className={styles.dialog}
      role="dialog"
      aria-modal="true"
      aria-labelledby="onboarding-title"
      aria-describedby="onboarding-description"
    >
      <div className={styles.progress} aria-label={`Step ${step + 1} of ${lessons.length}`}>
        {lessons.map((item, index) => <span
          key={item.eyebrow}
          className={index <= step ? styles.progressActive : undefined}
        />)}
      </div>
      <span className={styles.eyebrow}>{lesson.eyebrow}</span>
      <h2 id="onboarding-title">{lesson.title}</h2>
      <p id="onboarding-description">{lesson.body}</p>
      <div className={styles.note}>{lesson.note}</div>
      <div className={styles.actions}>
        <button type="button" className={styles.skip} disabled={working} onClick={dismiss}>
          Skip for now
        </button>
        <button
          ref={primaryAction}
          type="button"
          className={styles.primary}
          disabled={working}
          onClick={advance}
        >
          {working ? "Saving…" : finalStep ? "Start first trade" : "Next"}
        </button>
      </div>
    </section>
  </div>;
}

interface CoachProps {
  step: 1 | 2;
  onDismiss: () => Promise<void>;
}

export function FirstTradeCoach({ step, onDismiss }: CoachProps) {
  return <aside className={styles.coach} aria-label={`First trade walkthrough, step ${step} of 2`}>
    <div className={styles.coachNumber}>{step}</div>
    <div>
      <span>FIRST TRADE · STEP {step} OF 2</span>
      <strong>{step === 1 ? "Search for a stock you recognise" : "Review and submit the paper order"}</strong>
      <p>{step === 1
        ? "Try a company name or ticker, then select one result to inspect its verified quote."
        : "Start with one share, check the simulated charges, then place the buy order. This uses virtual cash only."}</p>
      {step === 1 && <a href="#paper-order-ticket">After selecting, continue at the order ticket <span aria-hidden="true">↓</span></a>}
    </div>
    <button type="button" className={styles.coachDismiss} onClick={() => void onDismiss()}>
      Dismiss guide
    </button>
  </aside>;
}
