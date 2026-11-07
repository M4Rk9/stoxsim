"use client";
import { FormEvent, useEffect, useState } from "react";
import { authenticatedRequest } from "../campus/client";
import type { LearnerProfile } from "../lib/learner";
import styles from "./LearnerExperience.module.css";

export default function LearnerProfileSettings() {
  const [profile, setProfile] = useState<LearnerProfile | null>(null);
  const [busy, setBusy] = useState(false), [error, setError] = useState(""), [notice, setNotice] = useState("");
  useEffect(() => {
    let active = true;
    authenticatedRequest<LearnerProfile>("learner/profile").then(value => { if (active) setProfile(value); })
      .catch(() => { if (active) setError("Learning preferences are temporarily unavailable."); });
    return () => { active = false; };
  }, []);
  async function save(event: FormEvent) {
    event.preventDefault(); if (!profile || busy) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const { experienceLevel, learningObjective, preferredMarket } = profile;
      setProfile(await authenticatedRequest<LearnerProfile>("learner/profile", { method: "PUT", body: JSON.stringify({ experienceLevel, learningObjective, preferredMarket }) }));
      setNotice("Learning preferences saved across your devices.");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Preferences could not be saved."); }
    finally { setBusy(false); }
  }
  return <section className={styles.card} id="learning-preferences" aria-labelledby="learner-profile-title">
    <h2 id="learner-profile-title">Your learning preferences</h2>
    <p>Optional. Choose what would make your next visit useful, or leave these unset.</p>
    {error && <p role="alert" className={styles.error}>{error}</p>}
    {notice && <p role="status">{notice}</p>}
    {!profile && !error && <p role="status">Loading preferences…</p>}
    {profile && <form onSubmit={save}><div className={styles.fields}>
      <div><label htmlFor="learner-experience">Experience level</label><select id="learner-experience" value={profile.experienceLevel ?? ""} onChange={e => setProfile({ ...profile, experienceLevel: e.target.value as LearnerProfile["experienceLevel"] || null })}>
        <option value="">Prefer not to say</option><option value="BEGINNER">Beginner</option><option value="INTERMEDIATE">Intermediate</option><option value="ADVANCED">Advanced</option>
      </select></div>
      <div><label htmlFor="learner-objective">Primary learning objective</label><select id="learner-objective" value={profile.learningObjective ?? ""} onChange={e => setProfile({ ...profile, learningObjective: e.target.value as LearnerProfile["learningObjective"] || null })}>
        <option value="">No preference</option><option value="BASICS">Understand the basics</option><option value="FUNDAMENTALS">Read company fundamentals</option><option value="DIVERSIFICATION">Understand diversification</option><option value="DISCIPLINE">Build a review habit</option>
      </select></div>
      <div><label htmlFor="learner-market">Preferred market</label><select id="learner-market" value={profile.preferredMarket ?? ""} onChange={e => setProfile({ ...profile, preferredMarket: e.target.value as LearnerProfile["preferredMarket"] || null })}>
        <option value="">No preference</option><option value="INDIA">India</option><option value="UNITED_STATES">United States</option>
      </select></div>
    </div><button className={styles.preferenceSubmit} disabled={busy}>{busy ? "Saving…" : "Save learning preferences"}</button></form>}
  </section>;
}
