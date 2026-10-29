"use client";

import Script from "next/script";
import { FormEvent, useEffect, useRef, useState } from "react";
import { authenticated } from "../campus/client";

const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
type GoogleApi = { accounts: { id: {
  initialize(config: { client_id: string; nonce: string; auto_select: boolean; use_fedcm_for_prompt: boolean; callback: (response: { credential: string }) => void }): void;
  renderButton(element: HTMLElement, options: { theme: string; size: string; text: string }): void;
  cancel(): void;
} } };
declare global { interface Window { google?: GoogleApi } }

async function post<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(`${API}/api/v1/auth/google/${path}`, {
    method: "POST", credentials: "include", cache: "no-store",
    headers: { "Content-Type": "application/json" }, body: JSON.stringify(body ?? {}),
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.message ?? problem?.detail ?? "Google sign-in is unavailable. Use email and password to sign in.");
  }
  return response.json() as Promise<T>;
}

export default function GoogleSignIn({ scriptNonce, manage = false, register = false }: { scriptNonce?: string; manage?: boolean; register?: boolean }) {
  const [config, setConfig] = useState<{ clientId: string } | null>(null);
  const [ready, setReady] = useState(false);
  const [linked, setLinked] = useState<boolean | null>(null);
  const [password, setPassword] = useState("");
  const [passwordEnabled, setPasswordEnabled] = useState(true);
  const [displayName, setDisplayName] = useState("");
  const [accepted, setAccepted] = useState(false);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const button = useRef<HTMLDivElement>(null);
  const passwordRef = useRef("");
  passwordRef.current = password;
  const details = useRef({ displayName, accepted, newPassword, deleting, confirmed });
  details.current = { displayName, accepted, newPassword, deleting, confirmed };
  const [finished, setFinished] = useState(false);

  useEffect(() => {
    let active = true;
    void post<{ clientId: string }>("config").then(value => { if (active) setConfig(value); })
      .catch(cause => { if (active) setError(cause.message); });
    if (manage) void Promise.all([authenticated<{ linked: boolean }>("auth/google/link"),
      authenticated<{ passwordLoginEnabled?: boolean }>("auth/me")])
      .then(([value, user]) => { if (active) { setLinked(value.linked); setPasswordEnabled(user.passwordLoginEnabled !== false); } })
      .catch(() => { if (active) setError("Sign in to StoxSim before managing Google sign-in."); });
    return () => { active = false; window.google?.accounts.id.cancel(); };
  }, [manage]);

  async function prepare(event: FormEvent) {
    event.preventDefault();
    if (!config || !ready || !button.current || !window.google) return;
    setError(""); setMessage("");
    if (manage && !passwordEnabled && !deleting && (newPassword !== confirmPassword || new TextEncoder().encode(newPassword).length > 72)) {
      setError("Passwords must match and contain at most 72 UTF-8 bytes."); return;
    }
    setBusy(true);
    try {
      const challenge = await post<{ nonce: string }>("challenge");
      window.google.accounts.id.initialize({
        client_id: config.clientId, nonce: challenge.nonce, auto_select: false, use_fedcm_for_prompt: true,
        callback: response => { void complete(response.credential); },
      });
      button.current.replaceChildren();
      window.google.accounts.id.renderButton(button.current, { theme: "outline", size: "large", text: "continue_with" });
      setMessage("Choose your Google account below. This request expires in five minutes.");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not start Google sign-in."); }
    finally { setBusy(false); }
  }

  async function complete(credential: string) {
    setBusy(true); setError(""); setMessage("");
    button.current?.replaceChildren();
    try {
      if (manage && !passwordEnabled) {
        const value = details.current;
        await authenticated(value.deleting ? "auth/google/delete" : "auth/google/password", value.deleting
          ? { credential, confirmed: value.confirmed } : { credential, newPassword: value.newPassword });
        window.sessionStorage.removeItem("stoxsim-session");
        setNewPassword(""); setConfirmPassword(""); setFinished(true);
        setMessage(value.deleting ? "Your account has been deleted." : "Password added. All sessions were signed out. Sign in again with Google or your new password.");
      } else if (manage) {
        await authenticated("auth/google/link", { credential, password: passwordRef.current });
        setPassword(""); setLinked(true); setMessage("Google is linked. You can now use it to sign in to StoxSim.");
      } else {
        const value = details.current;
        const session = register
          ? await post("register", { credential, displayName: value.displayName.trim(), termsAccepted: value.accepted })
          : await post("login", { credential });
        window.sessionStorage.setItem("stoxsim-session", JSON.stringify(session));
        window.location.assign("/");
      }
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Google sign-in failed. Start a new request to try again."); }
    finally { setBusy(false); }
  }

  async function unlink(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError("");
    try {
      await authenticated("auth/google/unlink", { password });
      window.sessionStorage.removeItem("stoxsim-session");
      setPassword(""); setLinked(null); setFinished(true); setMessage("Google disconnected. All sessions were signed out. Return to StoxSim to sign in with your password.");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not disconnect Google."); }
    finally { setBusy(false); }
  }

  return <main className="welcomeShell" id="main-content">
    <nav className="welcomeNav"><a href="/">StoxSim</a><a href="/settings">Account settings</a></nav>
    <section className="authCard" style={{ maxWidth: 480, margin: "48px auto" }}>
      <h1>{manage ? "Manage Google sign-in" : register ? "Create your account with Google" : "Sign in with Google"}</h1>
      <p>{manage ? passwordEnabled
        ? "Link or disconnect Google using your StoxSim password."
        : "You signed up with Google. Add a password before disconnecting Google or changing your email. Confirm your Google account to add a password or delete your account."
        : register ? "Choose a display name and accept our Terms and Privacy Notice. No StoxSim password is required."
        : "Use the Google account associated with your StoxSim account."}</p>
      {config && <Script src="https://accounts.google.com/gsi/client" nonce={scriptNonce}
        onReady={() => setReady(true)} onError={() => setError("Google could not load. Use email and password instead.")} />}
      {!finished && <form onSubmit={manage && linked && passwordEnabled ? unlink : prepare}>
        {register && <>
          <label>Display name<input required minLength={2} maxLength={100} value={displayName} disabled={busy} onChange={event => setDisplayName(event.target.value)} /></label>
          <label className="legalConsent"><input type="checkbox" required checked={accepted} disabled={busy} onChange={event => setAccepted(event.target.checked)} />
            <span>I agree to the <a href="/terms" target="_blank" rel="noreferrer">Terms of Use</a> and consent to the processing described in the <a href="/privacy" target="_blank" rel="noreferrer">Privacy Notice</a>.</span></label>
        </>}
        {manage && !passwordEnabled && <>
          <label className="legalConsent"><input type="checkbox" checked={deleting} disabled={busy} onChange={event => { setDeleting(event.target.checked); button.current?.replaceChildren(); setMessage(""); setConfirmed(false); }} /><span>Permanently delete my account instead</span></label>
          {deleting ? <label className="legalConsent"><input type="checkbox" required checked={confirmed} onChange={event => setConfirmed(event.target.checked)} /><span>I understand that my account and simulation data will be permanently deleted.</span></label>
            : <><label>New password<input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={newPassword} onChange={event => setNewPassword(event.target.value)} /></label>
              <label>Confirm new password<input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)} /></label></>}
        </>}
        {manage && passwordEnabled && <label>Current StoxSim password<input type="password" autoComplete="current-password" required maxLength={1000}
          value={password} onChange={event => setPassword(event.target.value)} /></label>}
        <button className="primaryButton wide" disabled={busy || !config || ((!linked || !passwordEnabled) && !ready) || (manage && linked === null)}>
          {busy ? "Working…" : manage && linked && passwordEnabled ? "Disconnect Google and sign out all devices" : "Continue"}
        </button>
      </form>}
      <div ref={button} style={{ marginTop: 16 }} />
      {message && <p role="status">{message}</p>}
      {error && <p role="alert" className="message errorMessage">{error}</p>}
      {!manage && <a href={register ? "/google-sign-in" : "/google-sign-in/register"}>{register ? "Already registered? Sign in with Google" : "New here? Create an account with Google"}</a>}
      <p><a href="/">Return to StoxSim</a></p>
      {!manage && <p>Already have a password account? Sign in with it and link Google in Settings.</p>}
    </section>
  </main>;
}
