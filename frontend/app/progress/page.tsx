"use client";

import Image from "next/image";
import { useEffect, useMemo, useState } from "react";
import styles from "./progress.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface StoredSession {
  accessToken: string;
  expiresInSeconds: number;
  user: {
    id: string;
    email: string;
    displayName: string;
  };
}

interface Mission {
  code: string;
  title: string;
  description: string;
  xp: number;
  progress: number;
  target: number;
  completed: boolean;
  completedAt?: string;
}

interface Challenge {
  code: string;
  title: string;
  description: string;
  completedMissions: number;
  totalMissions: number;
  missions: Mission[];
}

interface Achievement {
  code: string;
  title: string;
  description: string;
  unlocked: boolean;
  unlockedAt?: string;
}

interface Progression {
  version: string;
  totalXp: number;
  level: number;
  levelName: string;
  levelFloorXp: number;
  nextLevelXp?: number;
  currentStreak: number;
  longestStreak: number;
  lastCheckInDate?: string;
  checkInZoneId: string;
  checkedInToday: boolean;
  challenges: Challenge[];
  achievements: Achievement[];
  disclaimer: string;
}

class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

function readSession(): StoredSession | null {
  try {
    const value = window.sessionStorage.getItem("stoxsim-session");
    return value ? JSON.parse(value) as StoredSession : null;
  } catch {
    return null;
  }
}

function storeSession(session: StoredSession) {
  window.sessionStorage.setItem("stoxsim-session", JSON.stringify(session));
}

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    credentials: "include",
    ...options,
    headers: token ? { Authorization: `Bearer ${token}`, ...options.headers } : options.headers,
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new ApiError(payload?.message ?? `Request failed with status ${response.status}`, response.status);
  }
  return response.json() as Promise<T>;
}

async function refreshSession() {
  const session = await request<StoredSession>("/api/v1/auth/refresh", { method: "POST" });
  storeSession(session);
  return session;
}

async function authorized<T>(path: string, options: RequestInit = {}): Promise<T> {
  const session = readSession() ?? await refreshSession();
  try {
    return await request<T>(path, options, session.accessToken);
  } catch (cause) {
    if (!(cause instanceof ApiError) || cause.status !== 401) throw cause;
    const refreshed = await refreshSession();
    return request<T>(path, options, refreshed.accessToken);
  }
}

function shortDate(value?: string) {
  if (!value) return "Not yet";
  return new Intl.DateTimeFormat("en-IN", { dateStyle: "medium" }).format(new Date(value));
}

export default function ProgressPage() {
  const [progression, setProgression] = useState<Progression | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [checkingIn, setCheckingIn] = useState(false);

  useEffect(() => {
    let active = true;
    void authorized<Progression>("/api/v1/progression")
      .then((value) => { if (active) setProgression(value); })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : "Progression could not be loaded.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const levelPercent = useMemo(() => {
    if (!progression || progression.nextLevelXp == null) return 100;
    const span = progression.nextLevelXp - progression.levelFloorXp;
    return Math.max(0, Math.min(100, ((progression.totalXp - progression.levelFloorXp) / span) * 100));
  }, [progression]);

  async function checkIn() {
    setCheckingIn(true);
    setError("");
    try {
      setProgression(await authorized<Progression>("/api/v1/progression/check-in", { method: "POST" }));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Today's check-in could not be recorded.");
    } finally {
      setCheckingIn(false);
    }
  }

  return <main className={styles.shell}>
    <header className={styles.header}>
      <a className={styles.brand} href="/" aria-label="StoxSim dashboard">
        <Image src="/stoxsim-logo.png" alt="" width={42} height={42} priority />
        <span>Stox<span>Sim</span></span>
      </a>
      <a className={styles.back} href="/">Back to dashboard</a>
    </header>

    <section className={styles.heading}>
      <span>LEARN BY PRACTISING</span>
      <h1>Learning path</h1>
      <p>Complete educational milestones, build a consistent review habit and track progress without tying rewards to profit or trade volume.</p>
    </section>

    {loading && <div className={styles.loading}>Loading your learning path…</div>}
    {error && <div className={styles.error}>{error} <a href="/">Return to dashboard</a></div>}

    {progression && <>
      <section className={styles.overview} aria-label="Level progress">
        <article className={styles.levelCard}>
          <div className={styles.levelBadge} aria-hidden="true">{progression.level}</div>
          <div className={styles.levelCopy}>
            <span>LEVEL {progression.level}</span>
            <h2>{progression.levelName}</h2>
            <p><strong>{progression.totalXp} XP</strong>{progression.nextLevelXp == null ? " · Highest current level" : ` · ${progression.nextLevelXp - progression.totalXp} XP to next level`}</p>
            <div className={styles.levelTrack} role="progressbar" aria-label="XP toward next level" aria-valuemin={progression.levelFloorXp} aria-valuemax={progression.nextLevelXp ?? progression.totalXp} aria-valuenow={progression.totalXp}>
              <span style={{ width: `${levelPercent}%` }} />
            </div>
          </div>
        </article>

        <article className={styles.checkInCard}>
          <div>
            <span>LEARNING STREAK</span>
            <strong>{progression.currentStreak} {progression.currentStreak === 1 ? "day" : "days"}</strong>
            <small>Longest: {progression.longestStreak} · {progression.checkInZoneId}</small>
          </div>
          <button type="button" onClick={checkIn} disabled={checkingIn || progression.checkedInToday}>
            {progression.checkedInToday ? "Checked in today" : checkingIn ? "Recording…" : "Record today’s check-in"}
          </button>
          <small>Last check-in: {progression.lastCheckInDate ?? "Not yet"}</small>
        </article>
      </section>

      <section className={styles.challengeList} aria-labelledby="challenges-title">
        <div className={styles.sectionHeading}>
          <div><span>CHALLENGES & MISSIONS</span><h2 id="challenges-title">Your next learning steps</h2></div>
          <small>{progression.version}</small>
        </div>
        {progression.challenges.map((challenge) => <article className={styles.challenge} key={challenge.code}>
          <header>
            <div><h3>{challenge.title}</h3><p>{challenge.description}</p></div>
            <strong>{challenge.completedMissions} / {challenge.totalMissions}</strong>
          </header>
          <div className={styles.missions}>
            {challenge.missions.map((mission) => <div className={mission.completed ? styles.missionComplete : styles.mission} key={mission.code}>
              <div className={styles.missionStatus} aria-hidden="true">{mission.completed ? "✓" : mission.progress}</div>
              <div>
                <div className={styles.missionTitle}><h4>{mission.title}</h4><span>+{mission.xp} XP</span></div>
                <p>{mission.description}</p>
                <small>{mission.completed ? `Completed ${shortDate(mission.completedAt)}` : `${mission.progress} of ${mission.target}`}</small>
              </div>
            </div>)}
          </div>
        </article>)}
      </section>

      <section className={styles.achievements} aria-labelledby="achievements-title">
        <div className={styles.sectionHeading}>
          <div><span>ACHIEVEMENTS</span><h2 id="achievements-title">Learning milestones</h2></div>
        </div>
        <div className={styles.achievementGrid}>
          {progression.achievements.map((achievement) => <article className={achievement.unlocked ? styles.achievementUnlocked : styles.achievementLocked} key={achievement.code}>
            <span aria-hidden="true">{achievement.unlocked ? "◆" : "◇"}</span>
            <h3>{achievement.title}</h3>
            <p>{achievement.description}</p>
            <small>{achievement.unlocked ? `Unlocked ${shortDate(achievement.unlockedAt)}` : "Locked"}</small>
          </article>)}
        </div>
      </section>

      <p className={styles.disclaimer}>{progression.disclaimer}</p>
    </>}
  </main>;
}
