"use client";

import { useEffect, useState } from "react";
import styles from "../settings/settings.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function VerifyEmailPage() {
  const [status, setStatus] = useState<"working" | "success" | "error">("working");
  const [message, setMessage] = useState("Verifying your email address…");

  useEffect(() => {
    const token = new URLSearchParams(window.location.search).get("token");
    if (!token) {
      setStatus("error");
      setMessage("This verification link is missing its token.");
      return;
    }
    void fetch(`${API_URL}/api/v1/auth/email-verification/confirm`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token }),
    }).then(async (response) => {
      if (!response.ok) {
        const payload = await response.json().catch(() => null);
        throw new Error(payload?.message ?? "This verification link is invalid or expired.");
      }
      setStatus("success");
      setMessage("Your email address is verified.");
    }).catch((cause) => {
      setStatus("error");
      setMessage(cause instanceof Error ? cause.message : "Email verification failed.");
    });
  }, []);

  return <main className={styles.shell}>
    <header className={styles.header}>
      <a className={styles.brand} href="/">Stox<span>Sim</span></a>
      <a className={styles.back} href="/">← Back to StoxSim</a>
    </header>
    <section className={styles.content}>
      <span className={styles.eyebrow}>EMAIL VERIFICATION</span>
      <h1>{status === "success" ? "Email verified" : status === "error" ? "Link not accepted" : "One moment"}</h1>
      <div className={`${styles.message} ${status === "error" ? styles.error : styles.success}`}>
        {message}
      </div>
      {status !== "working" && <a className={styles.back} href="/settings">Open account settings →</a>}
    </section>
  </main>;
}
