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

type SubscriptionPlan = "FREE" | "PLUS" | "PRO";

interface Entitlements {
  standardCompetitiveCapitalInr: number;
  sandboxCapitalInr?: number;
  maximumSandboxPortfolios: number;
  finwizTier: string;
  analyticsTier: string;
  privateLeagues: boolean;
  scenarioLab: boolean;
  multiplePortfolios: boolean;
  premiumCompetitions: boolean;
}

interface SubscriptionAccount extends Account {
  accountKind: "STANDARD" | "SANDBOX";
  sandboxPlan?: SubscriptionPlan;
  sandboxSlot: number;
  accountLabel: string;
  startingCapital: number;
  active: boolean;
  leaderboardEligible: boolean;
}

interface PlanDetails {
  plan: SubscriptionPlan;
  displayName: string;
  monthlyPriceInr: number;
  entitlements: Entitlements;
}

interface SubscriptionDetails {
  version: "subscription-entitlements-v1";
  plan: SubscriptionPlan;
  status: "ACTIVE" | "PAST_DUE" | "CANCELED";
  billingEnabled: boolean;
  currentPeriodEnd?: string;
  entitlements: Entitlements;
  sandboxAccounts: SubscriptionAccount[];
  plans: PlanDetails[];
  notice: string;
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

interface WeeklyReportPreference {
  enabled: boolean;
  zoneId: string;
  schedule: string;
  verifiedEmailRequired: boolean;
  updatedAt?: string;
}

interface WeeklyMarketReport {
  marketRegion: "INDIA" | "UNITED_STATES";
  currency: "INR" | "USD";
  accountValue: number;
  accountValueChange?: number;
  totalProfitLoss: number;
  totalProfitLossChange?: number;
  totalReturnPercent: number;
  realizedProfitLoss: number;
  unrealizedProfitLoss: number;
  cashWeightPercent: number;
  investedWeightPercent: number;
  tradesExecuted: number;
  largestAllocationSymbol?: string;
  largestAllocationWeightPercent?: number;
  largestContributionSymbol?: string;
  largestContribution?: number;
  status: string;
  confidence: string;
  dataCoveragePercent: number;
}

interface WeeklyReportSnapshot {
  version: string;
  periodStart: string;
  periodEnd: string;
  markets: WeeklyMarketReport[];
  learningNotes: string[];
  disclaimer: string;
  generatedAt: string;
}

interface WeeklyPortfolioReport {
  id: string;
  deliveryStatus: "PENDING" | "SENT" | "FAILED";
  deliveryAttempts: number;
  deliveryAttemptedAt?: string;
  snapshot: WeeklyReportSnapshot;
}

class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

let refreshInFlight: Promise<StoredSession> | null = null;

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

const reportPeriod = (start: string, end: string) => `${new Intl.DateTimeFormat("en-IN", {
  dateStyle: "medium",
  timeZone: "UTC",
}).format(new Date(`${start}T00:00:00Z`))} – ${new Intl.DateTimeFormat("en-IN", {
  dateStyle: "medium",
  timeZone: "UTC",
}).format(new Date(`${end}T00:00:00Z`))}`;

const reportMoney = (value: number, currency: "INR" | "USD") => new Intl.NumberFormat(
  currency === "INR" ? "en-IN" : "en-US",
  { style: "currency", currency, maximumFractionDigits: 2 },
).format(value);

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
  const [reportPreference, setReportPreference] = useState<WeeklyReportPreference>({
    enabled: false,
    zoneId: "Asia/Kolkata",
    schedule: "Mondays after 08:00 in your selected timezone",
    verifiedEmailRequired: true,
  });
  const [reportHistory, setReportHistory] = useState<WeeklyPortfolioReport[]>([]);
  const [reportPreview, setReportPreview] = useState<WeeklyReportSnapshot | null>(null);
  const [subscription, setSubscription] = useState<SubscriptionDetails | null>(null);
  const [profileWorking, setProfileWorking] = useState(false);
  const [passwordWorking, setPasswordWorking] = useState(false);
  const [dangerWorking, setDangerWorking] = useState(false);
  const [reportWorking, setReportWorking] = useState(false);
  const [profileMessage, setProfileMessage] = useState("");
  const [passwordMessage, setPasswordMessage] = useState("");
  const [securityMessage, setSecurityMessage] = useState("");
  const [reportMessage, setReportMessage] = useState("");
  const [profileError, setProfileError] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [securityError, setSecurityError] = useState("");
  const [reportError, setReportError] = useState("");

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
    void (async () => {
      await Promise.all([loadSecurity(), loadReports(), loadSubscription()]);
    })();
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
      if (!refreshInFlight) {
        refreshInFlight = request<StoredSession>("/api/v1/auth/refresh", {
          method: "POST",
        }).finally(() => { refreshInFlight = null; });
      }
      const refreshed = await refreshInFlight;
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

  async function loadReports() {
    try {
      const [preference, history] = await Promise.all([
        authorized<WeeklyReportPreference>("/api/v1/reports/weekly/preferences"),
        authorized<WeeklyPortfolioReport[]>("/api/v1/reports/weekly"),
      ]);
      setReportPreference(preference);
      setReportHistory(history);
    } catch (cause) {
      setReportError(cause instanceof Error ? cause.message : "Weekly reports could not be loaded");
    }
  }

  async function loadSubscription() {
    try {
      setSubscription(await authorized<SubscriptionDetails>("/api/v1/subscription"));
    } catch (cause) {
      setSecurityError(cause instanceof Error ? cause.message : "Plan details could not be loaded");
    }
  }

  async function saveReportPreference(event: FormEvent) {
    event.preventDefault();
    setReportWorking(true);
    setReportMessage("");
    setReportError("");
    try {
      const preference = await authorized<WeeklyReportPreference>(
        "/api/v1/reports/weekly/preferences",
        {
          method: "PUT",
          body: JSON.stringify({
            enabled: reportPreference.enabled,
            zoneId: reportPreference.zoneId,
          }),
        },
      );
      setReportPreference(preference);
      setReportMessage(preference.enabled
        ? "Weekly portfolio reports are enabled."
        : "Weekly portfolio reports are disabled.");
    } catch (cause) {
      setReportError(cause instanceof Error ? cause.message : "Report preference could not be saved");
    } finally {
      setReportWorking(false);
    }
  }

  async function previewWeeklyReport() {
    setReportWorking(true);
    setReportMessage("");
    setReportError("");
    try {
      const preview = await authorized<WeeklyReportSnapshot>(
        `/api/v1/reports/weekly/preview?zoneId=${encodeURIComponent(reportPreference.zoneId)}`,
      );
      setReportPreview(preview);
      setReportMessage("Current educational preview generated. It was not emailed or saved.");
    } catch (cause) {
      setReportError(cause instanceof Error ? cause.message : "Report preview could not be generated");
    } finally {
      setReportWorking(false);
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

        {subscription && <section className={`${styles.card} ${styles.fullWidth}`} aria-labelledby="plan-title">
          <div className={styles.planHeading}>
            <div>
              <span className={styles.reportEyebrow}>PLAN &amp; SANDBOXES</span>
              <h2 id="plan-title">Your {subscription.plan.toLowerCase()} plan</h2>
            </div>
            <span className={styles.currentPlan}>{eventLabel(subscription.status)}</span>
          </div>
          <p>
            Your competitive India portfolio always starts at ₹5 lakh. Paid-plan capital is
            provisioned only in separate sandboxes and can never enter the standard leaderboard.
          </p>
          <div className={styles.integrityNote}>
            <strong>Leaderboard integrity protected</strong>
            <span>Standard capital: ₹5,00,000 · Sandbox balances are excluded by the API and database model.</span>
          </div>
          <div className={styles.planGrid}>
            {subscription.plans.map((item) => <article
              className={item.plan === subscription.plan ? styles.planCardCurrent : styles.planCard}
              key={item.plan}
            >
              <span>{item.plan}</span>
              <h3>{item.displayName}</h3>
              <strong>{item.monthlyPriceInr === 0
                ? "Free"
                : `₹${item.monthlyPriceInr.toFixed(0)}/month`}</strong>
              <ul>
                <li>{item.entitlements.maximumSandboxPortfolios === 0
                  ? "Standard ₹5 lakh portfolio"
                  : `${item.entitlements.maximumSandboxPortfolios} sandbox ${item.entitlements.maximumSandboxPortfolios === 1 ? "portfolio" : "portfolios"} up to ${reportMoney(item.entitlements.sandboxCapitalInr ?? 0, "INR")}`}</li>
                <li>{eventLabel(item.entitlements.finwizTier)} FinWiz</li>
                <li>{eventLabel(item.entitlements.analyticsTier)} analytics</li>
                {item.entitlements.scenarioLab && <li>Scenario Lab access</li>}
              </ul>
              <button type="button" disabled>
                {item.plan === subscription.plan ? "Current plan" : "Billing not available yet"}
              </button>
            </article>)}
          </div>
          {subscription.sandboxAccounts.length > 0 && <div className={styles.sandboxList}>
            <h3>Provisioned sandboxes</h3>
            {subscription.sandboxAccounts.map((account) => <div key={account.id}>
              <span><strong>{account.accountLabel}</strong><small>{account.active ? "Active" : "Locked"}</small></span>
              <strong>{reportMoney(account.startingCapital, "INR")}</strong>
            </div>)}
          </div>}
          <p className={styles.planNotice}>{subscription.notice}</p>
        </section>}

        <form className={`${styles.card} ${styles.fullWidth}`} onSubmit={saveReportPreference}>
          <div className={styles.reportHeading}>
            <div>
              <span className={styles.reportEyebrow}>WEEKLY LEARNING REVIEW</span>
              <h2>Portfolio reports</h2>
            </div>
            <span className={reportPreference.enabled ? styles.reportEnabled : styles.reportDisabled}>
              {reportPreference.enabled ? "Enabled" : "Disabled"}
            </span>
          </div>
          <p>
            Receive an educational summary of allocation, simulated performance and paper-trading
            activity. Reports never include recommendations or forecasts.
          </p>
          <label className={styles.toggleRow}>
            <input
              type="checkbox"
              checked={reportPreference.enabled}
              disabled={!session.user.emailVerified}
              onChange={(event) => setReportPreference({
                ...reportPreference,
                enabled: event.target.checked,
              })}
            />
            <span>
              Email my weekly portfolio report
              <small>{session.user.emailVerified
                ? reportPreference.schedule
                : "Verify your email address before enabling delivery."}</small>
            </span>
          </label>
          <label>
            Report timezone
            <select
              value={reportPreference.zoneId}
              onChange={(event) => setReportPreference({
                ...reportPreference,
                zoneId: event.target.value,
              })}
            >
              <option value="Asia/Kolkata">India — Asia/Kolkata</option>
              <option value="America/New_York">United States — America/New_York</option>
              <option value="UTC">UTC</option>
            </select>
          </label>
          <div className={styles.reportActions}>
            <button className={styles.submit} disabled={reportWorking}>
              {reportWorking ? "Saving…" : "Save report preference"}
            </button>
            <button
              className={styles.secondary}
              type="button"
              disabled={reportWorking}
              onClick={previewWeeklyReport}
            >Preview current report</button>
          </div>
          {reportMessage && <div className={`${styles.message} ${styles.success}`}>{reportMessage}</div>}
          {reportError && <div className={`${styles.message} ${styles.error}`}>{reportError}</div>}

          {reportPreview && <section className={styles.reportPreview} aria-labelledby="report-preview-title">
            <div>
              <span>PREVIEW · {reportPreview.version}</span>
              <h3 id="report-preview-title">{reportPeriod(reportPreview.periodStart, reportPreview.periodEnd)}</h3>
            </div>
            <div className={styles.marketReportGrid}>
              {reportPreview.markets.map((market) => <article key={market.marketRegion}>
                <span>{market.marketRegion === "INDIA" ? "INDIA" : "UNITED STATES"}</span>
                <strong>{reportMoney(market.accountValue, market.currency)}</strong>
                <small className={market.totalProfitLoss >= 0 ? styles.reportPositive : styles.reportNegative}>
                  {reportMoney(market.totalProfitLoss, market.currency)} simulated P/L
                </small>
                <small>{market.tradesExecuted} paper {market.tradesExecuted === 1 ? "trade" : "trades"} this period</small>
                <small>{market.cashWeightPercent.toFixed(1)}% cash · {market.confidence} confidence</small>
              </article>)}
            </div>
            <ul>{reportPreview.learningNotes.map((note) => <li key={note}>{note}</li>)}</ul>
            <p>{reportPreview.disclaimer}</p>
          </section>}

          <section className={styles.reportHistory} aria-labelledby="report-history-title">
            <div className={styles.reportHistoryHeading}>
              <h3 id="report-history-title">Recent reports</h3>
              <span>{reportHistory.length} saved</span>
            </div>
            {reportHistory.slice(0, 6).map((report) => <div className={styles.reportHistoryRow} key={report.id}>
              <div>
                <strong>{reportPeriod(report.snapshot.periodStart, report.snapshot.periodEnd)}</strong>
                <small>{report.snapshot.version}</small>
              </div>
              <span className={report.deliveryStatus === "SENT" ? styles.reportSent : styles.reportPending}>
                {eventLabel(report.deliveryStatus)}
              </span>
            </div>)}
            {!reportHistory.length && <span className={styles.empty}>
              No saved reports yet. The first one is generated after the next delivery window.
            </span>}
          </section>
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
