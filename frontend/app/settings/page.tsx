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
  emailVerified: boolean;
  createdAt?: string;
  accounts: Account[];
}

interface StoredSession {
  accessToken: string;
  expiresInSeconds: number;
  user: User;
}

interface ActiveSession {
  id: string;
  device: string;
  startedAt: string;
  lastUsedAt: string;
  expiresAt: string;
  current: boolean;
}

interface SecurityEvent {
  id: string;
  type: string;
  detail?: string;
  createdAt: string;
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
  if (response.status === 204 || response.status === 202) return undefined as T;
  return response.json() as Promise<T>;
}

const eventLabel = (value: string) => value
  .toLowerCase()
  .split("_")
  .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
  .join(" ");

const dateTime = (value: string) => new Intl.DateTimeFormat("en-IN", {
  dateStyle: "medium",
  timeStyle: "short",
}).format(new Date(value));

export default function SettingsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [profile, setProfile] = useState({ displayName: "", email: "" });
  const [passwords, setPasswords] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });
  const [deletePassword, setDeletePassword] = useState("");
  const [sessions, setSessions] = useState<ActiveSession[]>([]);
  const [events, setEvents] = useState<SecurityEvent[]>([]);
  const [profileWorking, setProfileWorking] = useState(false);
  const [passwordWorking, setPasswordWorking] = useState(false);
  const [dangerWorking, setDangerWorking] = useState(false);
  const [profileMessage, setProfileMessage] = useState("");
  const [passwordMessage, setPasswordMessage] = useState("");
  const [securityMessage, setSecurityMessage] = useState("");
  const [profileError, setProfileError] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [securityError, setSecurityError] = useState("");

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
    void loadSecurity();
  }, []);

  function persist(next: StoredSession) {
    window.sessionStorage.setItem("stoxsim-session", JSON.stringify(next));
    setSession(next);
  }

  async function authorized<T>(path: string, options: RequestInit = {}): Promise<T> {
    const active = readSession();
    if (!active) throw new ApiError("Please sign in again", 401);
    try {
      return await request<T>(path, options, active.accessToken);
    } catch (cause) {
      if (!(cause instanceof ApiError) || cause.status !== 401) throw cause;
      const refreshed = await request<StoredSession>("/api/v1/auth/refresh", {
        method: "POST",
      });
      persist(refreshed);
      return request<T>(path, options, refreshed.accessToken);
    }
  }

  async function loadSecurity() {
    try {
      const [activeSessions, securityEvents] = await Promise.all([
        authorized<ActiveSession[]>("/api/v1/auth/sessions"),
        authorized<SecurityEvent[]>("/api/v1/auth/events"),
      ]);
      setSessions(activeSessions);
      setEvents(securityEvents);
    } catch (cause) {
      setSecurityError(cause instanceof Error ? cause.message : "Security activity could not be loaded");
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
      setProfileMessage(
        user.emailVerified
          ? "Profile details updated."
          : "Profile updated. Check your inbox to verify this email address.",
      );
      await loadSecurity();
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
      window.sessionStorage.removeItem("stoxsim-session");
      setPasswordMessage("Password changed. All sessions were signed out.");
      window.setTimeout(() => window.location.replace("/"), 900);
    } catch (cause) {
      setPasswordError(cause instanceof Error ? cause.message : "Password could not be changed");
    } finally {
      setPasswordWorking(false);
    }
  }

  async function resendVerification() {
    setSecurityMessage("");
    setSecurityError("");
    try {
      await authorized<void>("/api/v1/auth/email-verification/resend", { method: "POST" });
      setSecurityMessage("A new verification link was sent.");
      await loadSecurity();
    } catch (cause) {
      setSecurityError(cause instanceof Error ? cause.message : "Verification email could not be sent");
    }
  }

  async function revokeSession(id: string) {
    setSecurityMessage("");
    setSecurityError("");
    try {
      await authorized<void>(`/api/v1/auth/sessions/${id}`, { method: "DELETE" });
      setSecurityMessage("Session revoked.");
      await loadSecurity();
    } catch (cause) {
      setSecurityError(cause instanceof Error ? cause.message : "Session could not be revoked");
    }
  }

  async function logoutEverywhere() {
    setDangerWorking(true);
    setSecurityError("");
    try {
      await authorized<void>("/api/v1/auth/logout-all", { method: "POST" });
      window.sessionStorage.removeItem("stoxsim-session");
      window.location.replace("/");
    } catch (cause) {
      setSecurityError(cause instanceof Error ? cause.message : "Sessions could not be revoked");
      setDangerWorking(false);
    }
  }

  async function downloadExport() {
    setSecurityMessage("");
    setSecurityError("");
    try {
      const data = await authorized<Record<string, unknown>>("/api/v1/auth/me/export", { method: "POST" });
      const url = URL.createObjectURL(new Blob(
        [JSON.stringify(data, null, 2)],
        { type: "application/json" },
      ));
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "stoxsim-account-export.json";
      anchor.click();
      URL.revokeObjectURL(url);
      setSecurityMessage("Account export downloaded.");
      await loadSecurity();
    } catch (cause) {
      setSecurityError(cause instanceof Error ? cause.message : "Account export failed");
    }
  }

  async function deleteAccount(event: FormEvent) {
    event.preventDefault();
    setDangerWorking(true);
    setSecurityError("");
    try {
      await authorized<void>("/api/v1/auth/me", {
        method: "DELETE",
        body: JSON.stringify({ password: deletePassword }),
      });
      window.sessionStorage.removeItem("stoxsim-session");
      window.location.replace("/");
    } catch (cause) {
      setSecurityError(cause instanceof Error ? cause.message : "Account could not be deleted");
      setDangerWorking(false);
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
        Manage your identity, recovery options, signed-in devices and personal data.
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
          <div className={session.user.emailVerified ? styles.verified : styles.unverified}>
            {session.user.emailVerified ? "Email verified" : "Email verification pending"}
          </div>
          {!session.user.emailVerified && <button
            className={styles.secondary}
            type="button"
            onClick={resendVerification}
          >Resend verification email</button>}
          {profileMessage && <div className={`${styles.message} ${styles.success}`}>{profileMessage}</div>}
          {profileError && <div className={`${styles.message} ${styles.error}`}>{profileError}</div>}
          <button className={styles.submit} disabled={profileWorking}>
            {profileWorking ? "Saving…" : "Save profile"}
          </button>
        </form>

        <form className={styles.card} onSubmit={savePassword}>
          <h2>Change password</h2>
          <p>Changing your password immediately signs out every device.</p>
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

        <section className={`${styles.card} ${styles.fullWidth}`}>
          <h2>Active sessions</h2>
          <p>Review browsers that can refresh access to your account.</p>
          <div className={styles.sessionList}>
            {sessions.map((item) => <div className={styles.sessionRow} key={item.id}>
              <div>
                <strong>{item.current ? "This browser" : item.device}</strong>
                <span>{item.current ? item.device : `Last used ${dateTime(item.lastUsedAt)}`}</span>
              </div>
              {item.current
                ? <span className={styles.current}>Current</span>
                : <button type="button" onClick={() => revokeSession(item.id)}>Revoke</button>}
            </div>)}
            {!sessions.length && <span className={styles.empty}>No refresh sessions are active.</span>}
          </div>
          <button className={styles.secondary} type="button" onClick={logoutEverywhere} disabled={dangerWorking}>
            Log out all devices
          </button>
        </section>

        <section className={styles.card}>
          <h2>Your data</h2>
          <p>Download a JSON copy of your profile, portfolios, orders, trades and watchlists.</p>
          <button className={styles.secondary} type="button" onClick={downloadExport}>
            Download account data
          </button>
        </section>

        <section className={styles.card}>
          <h2>Recent security activity</h2>
          <div className={styles.eventList}>
            {events.slice(0, 8).map((item) => <div key={item.id}>
              <strong>{eventLabel(item.type)}</strong>
              <span>{dateTime(item.createdAt)}</span>
            </div>)}
            {!events.length && <span className={styles.empty}>No security events recorded yet.</span>}
          </div>
        </section>

        <form className={`${styles.card} ${styles.danger} ${styles.fullWidth}`} onSubmit={deleteAccount}>
          <h2>Delete account permanently</h2>
          <p>
            This permanently removes your profile, virtual accounts, holdings, orders,
            trades and watchlists. This action cannot be undone.
          </p>
          <label>
            Confirm your password
            <input
              required
              type="password"
              value={deletePassword}
              onChange={(event) => setDeletePassword(event.target.value)}
            />
          </label>
          <button className={styles.delete} disabled={dangerWorking}>
            {dangerWorking ? "Deleting…" : "Delete my account"}
          </button>
        </form>
      </div>

      {securityMessage && <div className={`${styles.message} ${styles.success}`}>{securityMessage}</div>}
      {securityError && <div className={`${styles.message} ${styles.error}`}>{securityError}</div>}
    </section>
  </main>;
}
