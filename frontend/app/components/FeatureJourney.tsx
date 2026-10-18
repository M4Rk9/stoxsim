"use client";

import { useEffect, useRef, useState } from "react";
import { authenticated, session } from "../campus/client";
import type { OnboardingState } from "./OnboardingJourney";
import styles from "./FeatureJourney.module.css";

export interface JourneyState {
  firstTradeExecuted: boolean; awaitingExecution: boolean; finwizAsked: boolean;
  scenarioRun: boolean; learningViewed: boolean; competitionsViewed: boolean;
  nextStep: string; completedSteps: number; totalSteps: number; completed: boolean;
}
const features = [
  { key: "FINWIZ", title: "Finwiz AI", href: "/finwiz", description: "Turn a trading question into understanding. Explore guided lessons and ask about risk, charges or diversification.", action: "Ask and learn", done: "finwizAsked" },
  { key: "SCENARIO", title: "Scenario Lab", href: "/scenario-lab", description: "See how a market fall could affect your holdings. Run a what-if without changing your portfolio.", action: "Explore a scenario", done: "scenarioRun" },
  { key: "LEARNING", title: "Learning Path", href: "/progress", description: "Choose your next mission, review achievements and build your daily learning habit.", action: "See your missions", done: "learningViewed" },
  { key: "COMPETITIONS", title: "Competitions", href: "/competitions", description: "Explore the current season and practise with other learners. Joining is always your choice.", action: "Explore competitions", done: "competitionsViewed" },
] as const;
const steps: Record<string, { title: string; text: string; href: string; action: string }> = {
  INTRODUCTION: { title: "Get to know your home screen", text: "Your balance shows virtual cash. Market movers help you explore; search finds stocks and ETFs. Review a quote before using the practice order ticket.", href: "#stock-search", action: "Start the home tour" },
  FIRST_TRADE: { title: "Make your first practice trade", text: "Search for a stock or ETF, inspect its quote, then review quantity and simulated charges before placing your order in a standard account.", href: "#stock-search", action: "Find a stock or ETF" },
  WAITING: { title: "Your order is waiting to execute", text: "Submission is not execution. Closed markets and limit prices can keep an order open. Review its status below; your guide advances after an execution. You can explore Finwiz while you wait.", href: "#open-orders", action: "Check open orders" },
  FINWIZ: { title: "First trade executed. Understand what happened.", text: "Ask Finwiz: “How do charges and diversification affect my first trade?” Choose a topic, review the suggested question and send it to get an explanation.", href: "/finwiz", action: "Learn with Finwiz AI" },
  SCENARIO: { title: "Put your learning to the test", text: "Open Scenario Lab, select the portfolio holding your trade, choose Market sell-off and review the preview and available credits before running. Compare cash and holdings in the result.", href: "/scenario-lab", action: "Try Scenario Lab" },
  LEARNING: { title: "Choose your next learning mission", text: "Open Learning Path to review your progress and choose a mission. Your daily visit is recorded automatically.", href: "/progress", action: "Open Learning Path" },
  COMPETITIONS: { title: "Discover how to practise together", text: "Explore the current season, standings and private leagues. Read the rules before deciding whether to join; enrolment is optional.", href: "/competitions", action: "Explore Competitions" },
  COMPLETE: { title: "Your next visit, made useful", text: "Review your portfolio, ask Finwiz about something you noticed, or continue a learning mission. Revisit scenarios when your holdings change; check credits before running.", href: "/progress", action: "Continue learning" },
};
export default function FeatureJourney({ userId, onboarding, request, dismiss, resume, introduce }: {
  userId: string; onboarding: OnboardingState | null;
  request: () => Promise<JourneyState>; dismiss: () => Promise<void>; resume: () => Promise<void>; introduce: () => Promise<void>;
}) {
  const [state, setState] = useState<JourneyState | null>(null);
  const [error, setError] = useState(false);
  const requestRef = useRef(request); requestRef.current = request;
  useEffect(() => {
    let active = true, busy = false;
    setState(null);
    const refresh = async () => {
      if (document.hidden || busy) return;
      busy = true;
      try {
        const value = await requestRef.current();
        if (active && typeof value.nextStep === "string") { setState(value); setError(false); }
      } catch { if (active) setError(true); }
      finally { busy = false; }
    };
    void refresh();
    const interval = window.setInterval(refresh, 15000);
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => { active = false; clearInterval(interval); window.removeEventListener("focus", refresh); document.removeEventListener("visibilitychange", refresh); };
  }, [userId, onboarding?.introductionCompleted, onboarding?.firstOrderCompleted]);
  const step = state ? steps[state.nextStep] : undefined;
  return <section className={styles.section} aria-label="Your StoxSim learning journey">
    <div className={styles.heading}><div><span className="kicker">EXPLORE · PRACTISE · UNDERSTAND</span><h2>Your next step in StoxSim</h2></div>
      {onboarding?.dismissed ? <button type="button" onClick={() => void resume()}>Resume guide</button>
        : <button type="button" onClick={() => void dismiss()}>Hide guide</button>}
    </div>
    {!onboarding?.dismissed && step && <div className={styles.guide}>
      <div><span className={styles.progress}>{state!.completedSteps} of {state!.totalSteps} steps explored</span>
        <h3>{step.title}</h3><p>{step.text}</p>
        <ol className={styles.steps} aria-label="Journey steps">
          {["Home", "Executed trade", "Finwiz", "Scenario Lab", "Learning Path", "Competitions"].map((label, i) => {
            const done = [onboarding?.introductionCompleted, state!.firstTradeExecuted, state!.finwizAsked, state!.scenarioRun, state!.learningViewed, state!.competitionsViewed][i];
            return <li key={label}>{done ? "✓" : i + 1} · {label}</li>;
          })}
        </ol>
      </div>
      {state!.nextStep === "INTRODUCTION" ? <button type="button" onClick={() => void introduce()}>{step.action}</button>
        : <a className={styles.primary} href={step.href}>{step.action} →</a>}
    </div>}
    {error && <p role="status">Progress is temporarily unavailable. You can still explore every feature below.</p>}
    <div className={styles.grid}>{features.map(feature => <a className={styles.card} key={feature.key} href={feature.href}>
      <span className={styles.tag}>{state?.[feature.done] ? "Explored" : state?.nextStep === feature.key && !onboarding?.dismissed ? "Suggested next" : "Discover"}</span>
      <h3>{feature.title}</h3><p>{feature.description}</p><strong>{feature.action} →</strong>
    </a>)}</div>
  </section>;
}

export function FeatureGuide({ title, children }: { title: string; children: React.ReactNode }) {
  const [progress, setProgress] = useState<JourneyState | null>(null);
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    let active = true, busy = false;
    const refresh = async () => {
      if (document.hidden || !session() || busy) return;
      busy = true;
      try {
        const preference = await authenticated<OnboardingState>("onboarding");
        if (active) setVisible(!preference.dismissed);
        if (!preference.dismissed) {
          const value = await authenticated<JourneyState>("onboarding/journey");
          if (active) setProgress(value);
        }
      } catch { /* The feature remains usable if guidance is unavailable. */ }
      finally { busy = false; }
    };
    void refresh();
    const interval = window.setInterval(refresh, 15000);
    window.addEventListener("focus", refresh);
    return () => { active = false; clearInterval(interval); window.removeEventListener("focus", refresh); };
  }, []);
  if (!visible) return null;
  const next = progress ? steps[progress.nextStep] : undefined;
  const onward = next && next.href.startsWith("/") && next.href !== window.location.pathname;
  return <aside className={styles.featureTip} aria-label="Feature walkthrough"><strong>{title}</strong><p>{children}</p>{onward && <p><a href={next.href}>Next: {next.action} →</a></p>}<a href="/">Back to your journey →</a></aside>;
}
