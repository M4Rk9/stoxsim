"use client";

import { FormEvent, useState } from "react";
import styles from "../settings/settings.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setWorking(true);
    setMessage("");
    setError("");
    try {
      const response = await fetch(`${API_URL}/api/v1/auth/password/forgot`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
      if (!response.ok) throw new Error("The reset request could not be submitted.");
      setMessage("If an account exists for this email, a reset link has been sent.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The request failed");
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
      <span className={styles.eyebrow}>ACCOUNT RECOVERY</span>
      <h1>Reset your password</h1>
      <p className={styles.lead}>
        Enter your sign-in email. The response is intentionally the same whether
        or not an account exists.
      </p>
      <form className={styles.card} onSubmit={submit} style={{ marginTop: 34, maxWidth: 520 }}>
        <label>
          Email address
          <input
            required
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        {message && <div className={`${styles.message} ${styles.success}`}>{message}</div>}
        {error && <div className={`${styles.message} ${styles.error}`}>{error}</div>}
        <button className={styles.submit} disabled={working}>
          {working ? "Sending…" : "Send reset link"}
        </button>
      </form>
    </section>
  </main>;
}
