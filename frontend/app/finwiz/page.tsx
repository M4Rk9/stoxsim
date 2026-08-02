"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import FinwizAnswer from "./FinwizAnswer";
import FinwizReactor, { Topic, TopicDefinition, topics } from "./FinwizReactor";
import styles from "./finwiz.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type MarketRegion = "INDIA" | "UNITED_STATES";
type ExperienceLevel = "BEGINNER" | "INTERMEDIATE";

interface StoredSession {
  accessToken: string;
  refreshToken: string;
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
    const value = window.localStorage.getItem("stoxsim-session");
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
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${active.accessToken}`,
        ...(init.headers ?? {}),
      },
    });
    if (response.status !== 401) return response;

    const refresh = await fetch(`${API_URL}/api/v1/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: active.refreshToken }),
    });
    if (!refresh.ok) {
      window.localStorage.removeItem("stoxsim-session");
      throw new ApiError("Your session expired. Please sign in again.", 401);
    }
    const refreshed = await refresh.json() as StoredSession;
    active = refreshed;
    window.localStorage.setItem("stoxsim-session", JSON.stringify(refreshed));
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
    if (!question.trim()) return;
    setLoading(true);
    setError("");
    try {
      const stockSymbol = symbol.trim().toUpperCase();
      const response = await authorized("/api/v1/finwiz/ask", {
        method: "POST",
        body: JSON.stringify({
          question: question.trim(),
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
        document.getElementById("finwiz-response")?.scrollIntoView({ behavior: "smooth", block: "start" });
      });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Finwiz could not answer right now.");
    } finally {
      setLoading(false);
    }
  }

  if (!session) return null;

  return <main className={styles.shell}>
    <div className={styles.gridBackdrop} aria-hidden="true" />
    <header className={styles.header}>
      <a className={styles.brand} href="/">Stox<span>Sim</span></a>
      <div className={styles.headerStatus}>
        <span><i /> FINWIZ CORE ONLINE</span>
        <a href="/">Back to dashboard</a>
      </div>
    </header>

    <section className={styles.hero}>
      <div>
        <span className={styles.eyebrow}>AI-POWERED MARKET EDUCATION</span>
        <h1>FINWIZ <em>AI</em></h1>
        <p>Explore the market through an interactive learning core. Select a module, add a stock when relevant and receive a clear explanation grounded in available StoxSim data.</p>
      </div>
      <div className={styles.heroTelemetry}>
        <div><span>LEARNER</span><strong>{session.user.displayName}</strong></div>
        <div><span>MODE</span><strong>{experienceLevel}</strong></div>
        <div><span>MARKET</span><strong>{marketRegion === "INDIA" ? "INDIA" : "USA"}</strong></div>
      </div>
    </section>

    <section className={styles.reactorWorkspace}>
      <FinwizReactor selected={topic} onSelect={selectTopic} />

      <aside className={styles.moduleConsole}>
        <div className={styles.consoleLabel}>SELECTED LEARNING MODULE</div>
        <div className={styles.moduleNumber}>{String(topics.findIndex((item) => item.id === topic) + 1).padStart(2, "0")}</div>
        <h2>{selectedTopic.label}</h2>
        <p>{selectedTopic.description}</p>

        <div className={styles.moduleSignals}>
          <div><span>DEPTH</span><strong>{experienceLevel}</strong></div>
          <div><span>DATA LINK</span><strong>{symbol ? `${exchange}:${symbol}` : "GENERAL"}</strong></div>
          <div><span>OUTPUT</span><strong>CLEAN FORMAT</strong></div>
        </div>

        <button type="button" className={styles.loadPrompt} onClick={() => setQuestion(selectedTopic.prompt)}>
          Load recommended question <span>→</span>
        </button>

        <div className={styles.guardrail}>
          <span>EDUCATION GUARDRAIL</span>
          <strong>Analysis, not investment instructions.</strong>
          <p>Finwiz explains evidence, assumptions and limitations. It does not issue buy, sell, hold or target-price calls.</p>
        </div>
      </aside>
    </section>

    <section className={styles.commandConsole}>
      <div className={styles.consoleHeader}>
        <div>
          <span>QUERY TERMINAL</span>
          <h2>Ask Finwiz</h2>
        </div>
        <div className={styles.consoleState}><i /> READY FOR INPUT</div>
      </div>

      <form onSubmit={ask}>
        <div className={styles.formTop}>
          <label>
            Learning level
            <select value={experienceLevel} onChange={(event) => setExperienceLevel(event.target.value as ExperienceLevel)}>
              <option value="BEGINNER">Beginner</option>
              <option value="INTERMEDIATE">Intermediate</option>
            </select>
          </label>
          <label>
            Market
            <select value={marketRegion} onChange={(event) => setMarketRegion(event.target.value as MarketRegion)}>
              <option value="INDIA">India</option>
              <option value="UNITED_STATES">United States</option>
            </select>
          </label>
          <label>
            Exchange
            <select value={exchange} onChange={(event) => setExchange(event.target.value)}>
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
          <label>
            Stock symbol <small>Optional</small>
            <input
              value={symbol}
              onChange={(event) => setSymbol(event.target.value.toUpperCase())}
              placeholder={marketRegion === "INDIA" ? "RELIANCE" : "AAPL"}
              maxLength={32}
            />
          </label>
        </div>

        <label className={styles.questionLabel}>
          Your question
          <textarea
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            maxLength={2000}
            rows={5}
            placeholder="Ask about a financial statement, ratio, indicator, market condition or stock…"
          />
          <span>{question.length}/2000</span>
        </label>

        <div className={styles.formFooter}>
          <small>Stock-specific explanations use available StoxSim data. Missing values remain explicitly unavailable.</small>
          <button type="submit" disabled={loading || !question.trim()}>
            {loading ? <><i className={styles.loader} /> Analysing</> : <>Transmit question <span>↗</span></>}
          </button>
        </div>
      </form>
      {error && <div className={styles.error}>{error}</div>}
    </section>

    {answer ? <article className={styles.answer} id="finwiz-response">
      <div className={styles.answerHeader}>
        <div>
          <span>FINWIZ INTELLIGENCE REPORT</span>
          <h2>{selectedTopic.label}</h2>
        </div>
        <div className={styles.answerMeta}>
          <span>{answer.groundedInStoxSimData ? "STOXSIM DATA LINKED" : "GENERAL LEARNING MODE"}</span>
          <small>{answer.provider === "GEMINI" ? `Gemini · ${answer.model}` : "Built-in learning fallback"}</small>
        </div>
      </div>

      <FinwizAnswer answer={answer.answer} />

      <div className={styles.suggestions}>
        <span>NEXT LEARNING PATHS</span>
        <div>
          {answer.suggestedQuestions.map((suggestion) => <button
            type="button"
            key={suggestion}
            onClick={() => {
              setQuestion(suggestion);
              document.querySelector(`.${styles.commandConsole}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
            }}
          >{suggestion}<b>↗</b></button>)}
        </div>
      </div>
      <p className={styles.disclaimer}>{answer.disclaimer}</p>
    </article> : <section className={styles.idlePanel}>
      <div className={styles.idlePulse} aria-hidden="true"><span>F</span></div>
      <div>
        <span>AWAITING QUERY</span>
        <h2>The learning core is ready.</h2>
        <p>Choose a reactor segment or transmit your own question. Finwiz will turn complex market language into a structured, readable explanation.</p>
      </div>
    </section>}
  </main>;
}
