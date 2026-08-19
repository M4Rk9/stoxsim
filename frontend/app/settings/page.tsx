"use client";

import { FormEvent, useEffect, useState } from "react";
import styles from "./settings.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface Account {
  id: string;
  marketRegion: string;
  currency: string;
  availableCash: number;
  blockedCash: number;
  realizedProfitLoss: number;
}

interface User {
  id: string;
  email: string;
  displayName: string;
  createdAt?: string;
  accounts: Account[];
}

interface StoredSession {
  accessToken: string;
  expiresInSeconds: number;
  user: User;
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

async function request<T>(
  path: string,
  options: RequestInit,
  token?: string,
): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    credentials: "include",
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new ApiError(payload?.message ?? `Request failed with status ${response.status}`, response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export default function SettingsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [profile, setProfile] = useState({ displayName: "", email: "" });
  const [passwords, setPasswords] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });
  const [profileWorking, setProfileWorking] = useState(false);
  const [passwordWorking, setPasswordWorking] = useState(false);
  const [profileMessage, setProfileMessage] = useState("");
  const [passwordMessage, setPasswordMessage] = useState("");
  const [profileError, setProfileError] = useState("");
  const [passwordError, setPasswordError] = useState("");

  useEffect(() => {
    const active = readSession();
    if (!active) {
      window.location.replace("/");
      return;
    }
    setSession(active);
    setProfile({
      displayName: active.user.displayName,
      email: active.user.email,
    });
  }, []);

  function persist(next: StoredSession) {
    window.sessionStorage.setItem("stoxsim-session", JSON.stringify(next));
    setSession(next);
  }

  async function authorized<T>(path: string, options: RequestInit): Promise<T> {
    const active = readSession();
    if (!active) throw new ApiError("Please sign in again", 401);
    try {
      return await request<T>(path, options, active.accessToken);
    } catch (cause) {
      if (!(cause instanceof ApiError) || cause.status !== 401) throw cause;
      const refreshed = await request<StoredSession>("/api/v1/auth/refresh", {
        method: "POST",
        credentials: "include",
      });
      persist(refreshed);
      return request<T>(path, options, refreshed.accessToken);
    }
  }

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    setProfileWorking(true);
    setProfileMessage("");
    setProfileError("");
    try {
      const user = await authorized<User>("/api/v1/auth/me", {
        method: "PATCH",
        body: JSON.stringify(profile),
      });
      const active = readSession();
      if (active) persist({ ...active, user });
      setProfile({ displayName: user.displayName, email: user.email });
      setProfileMessage("Profile details updated.");
    } catch (cause) {
      setProfileError(cause instanceof Error ? cause.message : "Profile could not be updated");
    } finally {
      setProfileWorking(false);
    }
  }

  async function savePassword(event: FormEvent) {
    event.preventDefault();
    setPasswordMessage("");
    setPasswordError("");
    if (passwords.newPassword !== passwords.confirmPassword) {
      setPasswordError("New passwords do not match.");
      return;
    }
    setPasswordWorking(true);
    try {
      await authorized<void>("/api/v1/auth/me/password", {
        method: "PATCH",
        body: JSON.stringify({
          currentPassword: passwords.currentPassword,
          newPassword: passwords.newPassword,
        }),
      });
      setPasswords({ currentPassword: "", newPassword: "", confirmPassword: "" });
      setPasswordMessage("Password changed successfully.");
    } catch (cause) {
      setPasswordError(cause instanceof Error ? cause.message : "Password could not be changed");
    } finally {
      setPasswordWorking(false);
    }
  }

  if (!session) {
    return <main className={styles.loading}>Opening account settings…</main>;
  }

  return <main className={styles.shell}>
    <header className={styles.header}>
      <a className={styles.brand} href="/">Stox<span>Sim</span></a>
      <a className={styles.back} href="/">← Back to dashboard</a>
    </header>

    <section className={styles.content}>
      <span className={styles.eyebrow}>ACCOUNT SETTINGS</span>
      <h1>Profile &amp; security</h1>
      <p className={styles.lead}>
        Keep your identity current and protect access to your virtual portfolios.
        Changing these details does not reset balances, holdings or trading history.
      </p>

      <div className={styles.grid}>
        <form className={styles.card} onSubmit={saveProfile}>
          <h2>Profile details</h2>
          <p>Update the name shown throughout StoxSim and the email used to sign in.</p>
          <label>
            Display name
            <input
              required
              minLength={2}
              maxLength={100}
              value={profile.displayName}
              onChange={(event) => setProfile({ ...profile, displayName: event.target.value })}
            />
          </label>
          <label>
            Email address
            <input
              required
              type="email"
              maxLength={320}
              value={profile.email}
              onChange={(event) => setProfile({ ...profile, email: event.target.value })}
            />
          </label>
          {profileMessage && <div className={`${styles.message} ${styles.success}`}>{profileMessage}</div>}
          {profileError && <div className={`${styles.message} ${styles.error}`}>{profileError}</div>}
          <button className={styles.submit} disabled={profileWorking}>
            {profileWorking ? "Saving…" : "Save profile"}
          </button>
        </form>

        <form className={styles.card} onSubmit={savePassword}>
          <h2>Change password</h2>
          <p>Confirm your current password before setting a new one.</p>
          <label>
            Current password
            <input
              required
              type="password"
              value={passwords.currentPassword}
              onChange={(event) => setPasswords({ ...passwords, currentPassword: event.target.value })}
            />
          </label>
          <label>
            New password
            <input
              required
              type="password"
              minLength={8}
              maxLength={72}
              value={passwords.newPassword}
              onChange={(event) => setPasswords({ ...passwords, newPassword: event.target.value })}
            />
          </label>
          <label>
            Confirm new password
            <input
              required
              type="password"
              minLength={8}
              maxLength={72}
              value={passwords.confirmPassword}
              onChange={(event) => setPasswords({ ...passwords, confirmPassword: event.target.value })}
            />
          </label>
          {passwordMessage && <div className={`${styles.message} ${styles.success}`}>{passwordMessage}</div>}
          {passwordError && <div className={`${styles.message} ${styles.error}`}>{passwordError}</div>}
          <button className={styles.submit} disabled={passwordWorking}>
            {passwordWorking ? "Updating…" : "Change password"}
          </button>
        </form>
      </div>
    </section>
  </main>;
}
