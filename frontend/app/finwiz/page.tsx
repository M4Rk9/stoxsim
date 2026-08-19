"use client";

import { FormEvent, KeyboardEvent, useEffect, useMemo, useState } from "react";
import FinwizAnswer from "./FinwizAnswer";
import FinwizReactor, { Topic, TopicDefinition, topics } from "./FinwizReactor";
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
  const [answer, setAnswer] = useState<FinwizResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const active = readSession();
    if (!active) {
      window.location.replace("/");
      return;
    }
    setSession(active);

    const searchParams = new URLSearchParams(window.location.search);
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
    setExchange(marketRegion === "INDIA" ? "NSE" : "NASDAQ");
  }, [marketRegion]);

  const selectedTopic = useMemo(
    () => topics.find((item) => item.id === topic) ?? topics[0],
    [topic],
  );

  function selectTopic(next: TopicDefinition) {
    setTopic(next.id);
    setQuestion(next.prompt);
    setError("");
  }

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
    if (!nextQuestion || loading) return;

    setLoading(true);
    setError("");
    setAnswer(null);
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

      setAnswer(await response.json() as FinwizResponse);
      window.requestAnimationFrame(() => {
        document.getElementById("finwiz-response")?.scrollIntoView({
          behavior: "smooth",
          block: "start",
        });
      });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Finwiz could not answer right now.");
    } finally {
      setLoading(false);
    }
  }

  function handleQuestionKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  }

  if (!session) return null;

  return <main className={styles.shell}>
    <div className={styles.gridBackdrop} aria-hidden="true" />
    <h1 className={styles.srOnly}>FINWIZ AI</h1>

    <a className={styles.backLink} href="/" aria-label="Back to dashboard">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="m15 5-7 7 7 7" />
      </svg>
    </a>

    <section className={styles.skillViewport} aria-label="Finwiz learning modules">
      <FinwizReactor selected={topic} onSelect={selectTopic} />
    </section>

    {(submittedQuestion || answer || error) && <section className={styles.conversation} id="finwiz-response" aria-live="polite">
      {submittedQuestion && <div className={styles.userMessage}>
        <div>{submittedQuestion}</div>
      </div>}

      {loading && <div className={styles.assistantMessage}>
        <div className={styles.assistantMark} aria-hidden="true">F</div>
        <div className={styles.thinking}><span /><span /><span /></div>
      </div>}

      {answer && <div className={styles.assistantMessage}>
        <div className={styles.assistantMark} aria-hidden="true">F</div>
        <div className={styles.assistantBody}>
          <div className={styles.answerMeta}>
            <span>{selectedTopic.label}</span>
            <small>{answer.groundedInStoxSimData ? "StoxSim data linked" : "General learning mode"}</small>
          </div>
          <FinwizAnswer answer={answer.answer} />

          {answer.suggestedQuestions.length > 0 && <div className={styles.suggestions}>
            {answer.suggestedQuestions.map((suggestion) => <button
              type="button"
              key={suggestion}
              onClick={() => {
                setQuestion(suggestion);
                document.getElementById("finwiz-composer")?.scrollIntoView({ behavior: "smooth", block: "end" });
              }}
            >{suggestion}<span>↗</span></button>)}
          </div>}
          <p className={styles.disclaimer}>{answer.disclaimer}</p>
        </div>
      </div>}

      {error && <div className={styles.error} role="alert">{error}</div>}
    </section>}

    <div className={styles.composerDock} id="finwiz-composer">
      <form className={styles.composer} onSubmit={ask}>
        <div className={styles.contextBar}>
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
              onChange={(event) => setMarketRegion(event.target.value as MarketRegion)}
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

        <div className={styles.inputRow}>
          <textarea
            className={styles.questionInput}
            aria-label="Your question"
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={handleQuestionKeyDown}
            maxLength={2000}
            rows={1}
            placeholder="Ask Finwiz about markets, statements, valuation or risk"
          />
          <button
            className={styles.sendButton}
            type="submit"
            aria-label="Transmit question"
            title="Transmit question"
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
  </main>;
}
