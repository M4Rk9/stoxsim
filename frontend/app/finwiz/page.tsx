"use client";



import { FormEvent, KeyboardEvent, useEffect, useMemo, useRef, useState } from "react";
import FinwizAnswer from "./FinwizAnswer";
import { Topic, topics } from "./FinwizReactor";
import styles from "./finwiz.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type MarketRegion = "INDIA" | "UNITED_STATES";
type ExperienceLevel = "BEGINNER" | "INTERMEDIATE";

interface StoredSession {
  accessToken: string;
  expiresInSeconds: number;
  user: {
    displayName: string;
    email: string;
  };
}

interface FinwizResponse {
  answer: string;
  provider: string;
  model: string;
  groundedInStoxSimData: boolean;
  generatedAt: string;
  dataAsOf?: string;
  suggestedQuestions: string[];
  disclaimer: string;
  finwizTier?: string;
}

interface Chat { id: string; title: string; turns: { question: string; answer: FinwizResponse }[]; }

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

async function parseError(response: Response) {
  const payload = await response.json().catch(() => null);
  return payload?.message ?? `Request failed with status ${response.status}`;
}

export default function FinwizPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [topic, setTopic] = useState<Topic>("LEARN");
  const [experienceLevel, setExperienceLevel] = useState<ExperienceLevel>("BEGINNER");
  const [marketRegion, setMarketRegion] = useState<MarketRegion>("INDIA");
  const [exchange, setExchange] = useState("NSE");
  const [symbol, setSymbol] = useState("");
  const [question, setQuestion] = useState(topics[0].prompt);
  const [submittedQuestion, setSubmittedQuestion] = useState("");
  const [chats, setChats] = useState<Chat[]>([]);
  const [activeId, setActiveId] = useState("");
  const [historyOpen, setHistoryOpen] = useState(false);
  const [storageError, setStorageError] = useState("");
  const [clearPending, setClearPending] = useState(false);
  const busy = useRef(false);
  const historyToggle = useRef<HTMLButtonElement>(null);
  const historyPanel = useRef<HTMLElement>(null);
  const end = useRef<HTMLDivElement>(null);
  const current = chats.find(chat => chat.id === activeId);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const active = readSession();
    if (!active) {
      window.location.replace("/");
      return;
    }
    setSession(active);
    try {
      const saved = JSON.parse(localStorage.getItem(`stoxsim-finwiz:${active.user.email}`) ?? "[]");
      if (Array.isArray(saved)) setChats(saved.filter(chat => typeof chat.id === "string" && typeof chat.title === "string" && Array.isArray(chat.turns) && chat.turns.every((turn: Chat["turns"][number]) => typeof turn.question === "string" && typeof turn.answer?.answer === "string" && Array.isArray(turn.answer.suggestedQuestions) && turn.answer.suggestedQuestions.every(item => typeof item === "string"))).slice(0, 30));
    } catch { /* Ignore corrupt or unavailable device storage. */ }

    const searchParams = new URLSearchParams(window.location.search);
    const requestedTopic = topics.find(item => item.id === searchParams.get("topic"));
    if (requestedTopic) { setTopic(requestedTopic.id); setQuestion(requestedTopic.prompt); }
    const requestedRegion = searchParams.get("marketRegion");
    const requestedExchange = searchParams.get("exchange");
    const requestedSymbol = searchParams.get("symbol");

    if (requestedRegion === "INDIA" || requestedRegion === "UNITED_STATES") {
      setMarketRegion(requestedRegion);
      setExchange(requestedRegion === "INDIA" ? "NSE" : "NASDAQ");
    }
    if (requestedExchange) setExchange(requestedExchange.toUpperCase());
    if (requestedSymbol) {
      const normalizedSymbol = requestedSymbol.toUpperCase();
      setSymbol(normalizedSymbol);
      setTopic("STOCK_FUNDAMENTALS");
      setQuestion(`Explain the fundamentals, valuation, cash-flow quality and key risks of ${normalizedSymbol} using the available StoxSim data.`);
    }
  }, []);

  useEffect(() => {
    const view = window.visualViewport;
    const resize = () => document.documentElement.style.setProperty("--finwiz-height", `${view?.height ?? innerHeight}px`);
    resize(); view?.addEventListener("resize", resize);
    return () => view?.removeEventListener("resize", resize);
  }, []);
  useEffect(() => { end.current?.scrollIntoView({ block: "end" }); }, [chats, activeId, loading]);
  useEffect(() => {
    if (!historyOpen) return;
    const panel = historyPanel.current, toggle = historyToggle.current;
    panel?.querySelector<HTMLElement>("a,button:not(:disabled)")?.focus();
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (!window.matchMedia("(max-width:700px)").matches) return;
      if (event.key === "Escape") { setHistoryOpen(false); toggle?.focus(); }
      if (event.key !== "Tab" || !panel || !toggle) return;
      const items = [...panel.querySelectorAll<HTMLElement>("a,button:not(:disabled)"), toggle];
      const first = items[0], last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [historyOpen]);
  function persist(next: Chat[]) {
    setChats(next);
    try { localStorage.setItem(`stoxsim-finwiz:${session?.user.email}`, JSON.stringify(next)); setStorageError(""); }
    catch { setStorageError("History could not be saved on this device."); }
  }
  function newChat() {
    setActiveId(""); setSubmittedQuestion(""); setQuestion(""); setError(""); setHistoryOpen(false);
    requestAnimationFrame(() => document.getElementById("finwiz-question")?.focus());
  }
  const selectedTopic = useMemo(
    () => topics.find((item) => item.id === topic) ?? topics[0],
    [topic],
  );

  async function authorized(path: string, init: RequestInit): Promise<Response> {
    let active = readSession();
    if (!active) throw new ApiError("Please sign in again", 401);

    let response = await fetch(`${API_URL}${path}`, {
      credentials: "include",
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${active.accessToken}`,
        ...(init.headers ?? {}),
      },
    });
    if (response.status !== 401) return response;

    const refresh = await fetch(`${API_URL}/api/v1/auth/refresh`, {
      credentials: "include",
      method: "POST",
      headers: { "Content-Type": "application/json" },
    });
    if (!refresh.ok) {
      window.sessionStorage.removeItem("stoxsim-session");
      throw new ApiError("Your session expired. Please sign in again.", 401);
    }

    const refreshed = await refresh.json() as StoredSession;
    active = refreshed;
    window.sessionStorage.setItem("stoxsim-session", JSON.stringify(refreshed));
    setSession(refreshed);

    return fetch(`${API_URL}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${active.accessToken}`,
        ...(init.headers ?? {}),
      },
    });
  }

  async function ask(event: FormEvent) {
    event.preventDefault();
    const nextQuestion = question.trim();
    if (!nextQuestion || busy.current) return;
    busy.current = true;

    setLoading(true);
    setError("");
    setSubmittedQuestion(nextQuestion);

    try {
      const stockSymbol = symbol.trim().toUpperCase();
      const response = await authorized("/api/v1/finwiz/ask", {
        method: "POST",
        body: JSON.stringify({
          question: nextQuestion,
          topic,
          experienceLevel,
          ...(stockSymbol ? {
            marketRegion,
            exchange,
            symbol: stockSymbol,
          } : {}),
        }),
      });
      if (!response.ok) throw new ApiError(await parseError(response), response.status);

      const result = await response.json() as FinwizResponse;
      const chat = current ?? { id: crypto.randomUUID(), title: nextQuestion.slice(0, 80), turns: [] };
      persist([{ ...chat, turns: [...chat.turns, { question: nextQuestion, answer: result }].slice(-50) }, ...chats.filter(item => item.id !== chat.id)].slice(0, 30));
      setActiveId(chat.id); setSubmittedQuestion(""); setQuestion(draft => draft.trim() === nextQuestion ? "" : draft);
      window.requestAnimationFrame(() => {
        document.getElementById("finwiz-response")?.scrollIntoView({
          behavior: "smooth",
          block: "start",
        });
      });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Finwiz could not answer right now.");
    } finally {
      setLoading(false); busy.current = false;
    }
  }

  function handleQuestionKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  }

  if (!session) return null;

  return <main className={styles.shell} id="main-content" tabIndex={-1}>
    <aside ref={historyPanel} id="chat-history" className={`${styles.sidebar} ${historyOpen ? styles.open : ""}`} aria-label="Chat history">
      <a href="/" className={styles.brand}>StoxSim</a>
      <button disabled={loading} onClick={newChat}>+ New chat</button><h2>Recent chats</h2>
      <div className={styles.historyList}>{!chats.length && <p>Your conversations will appear here.</p>}{chats.map(chat => <button key={chat.id} disabled={loading} aria-current={activeId === chat.id ? "page" : undefined} onClick={() => {
        setActiveId(chat.id); setSubmittedQuestion(""); setError(""); setQuestion(""); setHistoryOpen(false); requestAnimationFrame(() => document.getElementById("finwiz-question")?.focus());
      }}>{chat.title}</button>)}</div>
      <a href="/learn#learning-map">Learning map ↗</a><a href="/">← Dashboard</a>
      <p>Last 30 chats saved on this device for your account.</p>
      <button disabled={loading || !chats.length} onClick={() => { if (!clearPending) { setClearPending(true); return; } persist([]); newChat(); setClearPending(false); }}>{clearPending ? "Confirm clear history" : "Clear history"}</button>
      {clearPending && <button onClick={() => setClearPending(false)}>Cancel</button>}
    </aside><div className={styles.workspace}>
    <header className={styles.header}>
      <a className={styles.backLink} href="/" aria-label="Back to dashboard">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="m15 5-7 7 7 7" />
        </svg>
      </a>
      <h1>Finwiz AI</h1><button ref={historyToggle} aria-controls="chat-history" className={styles.historyToggle} aria-expanded={historyOpen} onClick={() => setHistoryOpen(!historyOpen)}>{historyOpen ? "Close history" : "Chat history"}</button>
    </header>

    <section className={styles.conversation} id="finwiz-response" aria-live="polite">
      {!current && !submittedQuestion && <div className={styles.welcome}><span>YOUR LEARNING COMPANION</span><h2>What would you like to understand?</h2><p>Explore markets, make sense of a company, or learn from your paper trades.</p><a href="/learn#learning-map">Find a topic in your learning map →</a></div>}
      {current?.turns.map((turn, index) => <article key={index}>
        <div className={styles.userMessage}><div>{turn.question}</div></div>
        <div className={styles.assistantMessage}><div className={styles.assistantMark}>F</div><div className={styles.assistantBody}>
          <div className={styles.answerMeta}><strong>Finwiz{turn.answer.finwizTier ? ` · ${turn.answer.finwizTier.toLowerCase()} depth` : ""}</strong><small>{turn.answer.groundedInStoxSimData ? "StoxSim data linked" : "General learning mode"}</small></div>
          <FinwizAnswer answer={turn.answer.answer} />
          <p className={styles.disclaimer}>{turn.answer.disclaimer}</p>
          {index === current.turns.length - 1 && <div className={styles.suggestions}>{turn.answer.suggestedQuestions.map(item => <button key={item} onClick={() => { setQuestion(item); document.getElementById("finwiz-question")?.focus(); }}>{item} ↗</button>)}</div>}
        </div></div>
      </article>)}
      {submittedQuestion && <div className={styles.userMessage}>
        <div>{submittedQuestion}</div>
      </div>}

      {loading && <div className={styles.assistantMessage}>
        <div className={styles.assistantMark} aria-hidden="true">F</div>
        <p className={styles.thinking} role="status">Finwiz is thinking…</p>
      </div>}

      <div ref={end} />
      {error && <div className={styles.error} role="alert">{error}</div>}
    </section>

    <div className={styles.composerDock} id="finwiz-composer">
      <form className={styles.composer} onSubmit={ask}>
        <details className={styles.contextSettings}><summary>Learning context · {selectedTopic.label}</summary><div className={styles.contextBar}>
          <span className={styles.contextTopic}>{selectedTopic.shortLabel}</span>

          <label className={styles.contextControl}>
            <span>Level</span>
            <select
              aria-label="Learning level"
              value={experienceLevel}
              onChange={(event) => setExperienceLevel(event.target.value as ExperienceLevel)}
            >
              <option value="BEGINNER">Beginner</option>
              <option value="INTERMEDIATE">Intermediate</option>
            </select>
          </label>

          <label className={styles.contextControl}>
            <span>Market</span>
            <select
              aria-label="Market"
              value={marketRegion}
              onChange={(event) => { setMarketRegion(event.target.value as MarketRegion); setExchange(event.target.value === "INDIA" ? "NSE" : "NASDAQ"); }}
            >
              <option value="INDIA">India</option>
              <option value="UNITED_STATES">United States</option>
            </select>
          </label>

          <label className={styles.contextControl}>
            <span>Exchange</span>
            <select aria-label="Exchange" value={exchange} onChange={(event) => setExchange(event.target.value)}>
              {marketRegion === "INDIA" ? <>
                <option value="NSE">NSE</option>
                <option value="BSE">BSE</option>
              </> : <>
                <option value="NASDAQ">NASDAQ</option>
                <option value="NYSE">NYSE</option>
                <option value="NYSE_ARCA">NYSE Arca</option>
                <option value="AMEX">AMEX</option>
                <option value="CBOE">Cboe</option>
              </>}
            </select>
          </label>

          <label className={styles.symbolControl}>
            <span>Symbol</span>
            <input
              aria-label="Stock symbol"
              value={symbol}
              onChange={(event) => setSymbol(event.target.value.toUpperCase())}
              placeholder={marketRegion === "INDIA" ? "RELIANCE" : "AAPL"}
              maxLength={32}
            />
          </label>
        </div>

        </details><div className={styles.inputRow}>
          <textarea
            id="finwiz-question"
            className={styles.questionInput}
            aria-label="Your question"
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={handleQuestionKeyDown}
            maxLength={2000}
            rows={3}
            placeholder="Ask Finwiz about markets, statements, valuation or risk"
          />
          <button
            className={styles.sendButton}
            type="submit"
            aria-label="Ask Finwiz"
            title="Ask Finwiz"
            disabled={loading || !question.trim()}
          >
            {loading ? <i className={styles.loader} /> : <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m12 19V5M6 11l6-6 6 6" />
            </svg>}
          </button>
        </div>

        <div className={styles.composerHint}>
          <span>Enter to send · Shift + Enter for a new line</span>
          <span>{question.length}/2000</span>
        </div>
      </form>
    </div>

    <p className={styles.disclaimer}>{storageError || "Educational explanations, not investment advice. Check important information."}</p>
    </div>
  </main>;
}
