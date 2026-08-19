"use client";

import { useCallback, useEffect, useState } from "react";

import styles from "./status.module.css";

const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(/\/$/, "");

type ApiState = {
  state: "checking" | "operational" | "degraded";
  checkedAt: Date | null;
  requestId: string | null;
};

export default function StatusPage() {
  const [api, setApi] = useState<ApiState>({
    state: "checking",
    checkedAt: null,
    requestId: null,
  });

  const checkApi = useCallback(async () => {
    setApi((current) => ({ ...current, state: "checking" }));
    try {
      const response = await fetch(`${API_URL}/actuator/health/readiness`, {
        cache: "no-store",
      });
      const body = await response.json();
      setApi({
        state: response.ok && body.status === "UP" ? "operational" : "degraded",
        checkedAt: new Date(),
        requestId: response.headers.get("x-request-id"),
      });
    } catch {
      setApi({
        state: "degraded",
        checkedAt: new Date(),
        requestId: null,
      });
    }
  }, []);

  useEffect(() => {
    void checkApi();
    const timer = window.setInterval(() => void checkApi(), 60_000);
    return () => window.clearInterval(timer);
  }, [checkApi]);

  const overall = api.state === "operational" ? "All systems operational" : api.state === "checking" ? "Checking systems…" : "Service disruption detected";

  return (
    <main className={styles.page}>
      <section className={styles.card}>
        <a className={styles.back} href="/">← Back to StoxSim</a>
        <p className={styles.eyebrow}>Service status</p>
        <h1>{overall}</h1>
        <p className={styles.summary}>
          This page checks the public API directly. Automated external probes and production alerts run separately.
        </p>

        <div className={styles.services}>
          <div className={styles.service}>
            <div>
              <strong>Web application</strong>
              <span>stoxsim.com</span>
            </div>
            <span className={styles.operational}>Operational</span>
          </div>
          <div className={styles.service}>
            <div>
              <strong>API and account services</strong>
              <span>api.stoxsim.com</span>
            </div>
            <span className={api.state === "degraded" ? styles.degraded : styles.operational}>
              {api.state === "checking" ? "Checking" : api.state === "operational" ? "Operational" : "Degraded"}
            </span>
          </div>
        </div>

        <div className={styles.meta}>
          <span>Last checked: {api.checkedAt ? api.checkedAt.toLocaleString() : "In progress"}</span>
          {api.requestId && <span>Request ID: {api.requestId}</span>}
        </div>

        <button type="button" onClick={() => void checkApi()} disabled={api.state === "checking"}>
          {api.state === "checking" ? "Checking…" : "Check again"}
        </button>
        <p className={styles.support}>
          If a problem persists, contact <a href="mailto:support.stoxsim@gmail.com">support.stoxsim@gmail.com</a> and include the request ID.
        </p>
      </section>
    </main>
  );
}
