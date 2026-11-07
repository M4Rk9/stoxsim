"use client";
import { useEffect, useState } from "react";
import { authenticatedRequest } from "../campus/client";
import { safeLearningLink, type LearnerHome } from "../lib/learner";
import styles from "./LearnerExperience.module.css";

export default function HomeRecommendation({ userId }: { userId: string }) {
  const [data, setData] = useState<LearnerHome | null>(null);
  useEffect(() => {
    let active = true; setData(null);
    authenticatedRequest<LearnerHome>("learner/home").then(value => { if (active && value?.recommendation) setData(value); }).catch(() => {});
    return () => { active = false; };
  }, [userId]);
  const recommendation = data?.recommendation;
  const href = recommendation && safeLearningLink(recommendation.href);
  if (!recommendation || !href) return null;
  return <section className={styles.card} aria-label="Recommended learning action">
    <span className={styles.badge}>FOR YOUR NEXT STEP</span>
    <h2>{recommendation.title}</h2><p>{recommendation.description}</p>
    <div className={styles.actions}><a className={styles.primary} href={href} onClick={() => {
      void authenticatedRequest(`learner/recommendations/${encodeURIComponent(recommendation.id)}/used`, { method: "POST" }).catch(() => {});
    }}>{recommendation.actionLabel} →</a><a href="/settings#learning-preferences">Learning preferences</a></div>
  </section>;
}
