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

export default function GoogleSignIn({ scriptNonce }: { scriptNonce?: string }) {
  const [config, setConfig] = useState<{ clientId: string } | null>(null);
  const [ready, setReady] = useState(false);
  const [manage, setManage] = useState(false);
  const [linked, setLinked] = useState<boolean | null>(null);
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const button = useRef<HTMLDivElement>(null);
  const passwordRef = useRef("");
  passwordRef.current = password;

  useEffect(() => {
    let active = true;
    const managing = new URLSearchParams(window.location.search).get("manage") === "1";
    setManage(managing);
    void post<{ clientId: string }>("config").then(value => { if (active) setConfig(value); })
      .catch(cause => { if (active) setError(cause.message); });
    if (managing) void authenticated<{ linked: boolean }>("auth/google/link")
      .then(value => { if (active) setLinked(value.linked); })
      .catch(() => { if (active) setError("Sign in with your StoxSim password before managing Google sign-in."); });
    return () => { active = false; window.google?.accounts.id.cancel(); };
  }, []);

  async function prepare(event: FormEvent) {
    event.preventDefault();
    if (!config || !ready || !button.current || !window.google) return;
    setError(""); setMessage(""); setBusy(true);
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
      if (manage) {
        await authenticated("auth/google/link", { credential, password: passwordRef.current });
        setPassword(""); setLinked(true); setMessage("Google is linked. You can now use it to sign in to StoxSim.");
      } else {
        const session = await post("login", { credential });
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
      setPassword(""); setLinked(null); setMessage("Google disconnected. All sessions were signed out. Return to StoxSim to sign in with your password.");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not disconnect Google."); }
    finally { setBusy(false); }
  }

  return <main className="welcomeShell" id="main-content">
    <nav className="welcomeNav"><a href="/">StoxSim</a><a href="/settings">Account settings</a></nav>
    <section className="authCard" style={{ maxWidth: 480, margin: "48px auto" }}>
      <h1>{manage ? "Manage Google sign-in" : "Sign in with Google"}</h1>
      <p>{manage ? "Link your Google account by confirming your StoxSim password. Your password and recovery options stay available."
        : "Use the Google account you linked in StoxSim settings. New here? Create a StoxSim account first, then link Google."}</p>
      {config && <Script src="https://accounts.google.com/gsi/client" nonce={scriptNonce}
        onReady={() => setReady(true)} onError={() => setError("Google could not load. Use email and password instead.")} />}
      <form onSubmit={manage && linked ? unlink : prepare}>
        {manage && <label>Current StoxSim password<input type="password" autoComplete="current-password" required maxLength={1000}
          value={password} onChange={event => setPassword(event.target.value)} /></label>}
        <button className="primaryButton wide" disabled={busy || !config || (!linked && !ready) || (manage && linked === null)}>
          {busy ? "Working…" : manage && linked ? "Disconnect Google and sign out all devices" : "Continue"}
        </button>
      </form>
      <div ref={button} style={{ marginTop: 16 }} />
      {message && <p role="status">{message}</p>}
      {error && <p role="alert" className="message errorMessage">{error}</p>}
      <a href="/">Use email and password</a>
    </section>
  </main>;
}
