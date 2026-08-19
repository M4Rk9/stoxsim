"use client";

import { FormEvent, useEffect, useState } from "react";
import styles from "../settings/settings.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function ResetPasswordPage() {
  const [token, setToken] = useState("");
  const [passwords, setPasswords] = useState({ password: "", confirm: "" });
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    setToken(new URLSearchParams(window.location.search).get("token") ?? "");
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setMessage("");
    if (!token) {
      setError("This reset link is missing its token.");
      return;
    }
    if (passwords.password !== passwords.confirm) {
      setError("Passwords do not match.");
      return;
    }
    setWorking(true);
    try {
      const response = await fetch(`${API_URL}/api/v1/auth/password/reset`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, newPassword: passwords.password }),
      });
      if (!response.ok) {
        const payload = await response.json().catch(() => null);
        throw new Error(payload?.message ?? "This reset link is invalid or expired.");
      }
      setMessage("Password reset successfully. Every existing session has been signed out.");
      setPasswords({ password: "", confirm: "" });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Password reset failed");
    } finally {
      setWorking(false);
    }
  }

  return <main className={styles.shell}>
    <header className={styles.header}>
      <a className={styles.brand} href="/">Stox<span>Sim</span></a>
      <a className={styles.back} href="/">← Back to sign in</a>
    </header>
    <section className={styles.content}>
      <span className={styles.eyebrow}>SECURE RECOVERY</span>
      <h1>Choose a new password</h1>
      <p className={styles.lead}>The link can be used once and expires after 30 minutes.</p>
      <form className={styles.card} onSubmit={submit} style={{ marginTop: 34, maxWidth: 520 }}>
        <label>
          New password
          <input
            required
            type="password"
            minLength={8}
            maxLength={72}
            value={passwords.password}
            onChange={(event) => setPasswords({ ...passwords, password: event.target.value })}
          />
        </label>
        <label>
          Confirm password
          <input
            required
            type="password"
            minLength={8}
            maxLength={72}
            value={passwords.confirm}
            onChange={(event) => setPasswords({ ...passwords, confirm: event.target.value })}
          />
        </label>
        {message && <div className={`${styles.message} ${styles.success}`}>{message} <a href="/">Sign in</a></div>}
        {error && <div className={`${styles.message} ${styles.error}`}>{error}</div>}
        <button className={styles.submit} disabled={working}>
          {working ? "Resetting…" : "Reset password"}
        </button>
      </form>
    </section>
  </main>;
}
