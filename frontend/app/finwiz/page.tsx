"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import styles from "./finwiz.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type Topic =
  | "LEARN"
  | "STOCK_FUNDAMENTALS"
  | "TECHNICAL_ANALYSIS"
  | "FUNDAMENTAL_ANALYSIS"
  | "VALUATION"
  | "CASH_FLOW"
  | "MARKET_EVALUATION"
  | "PORTFOLIO_EDUCATION";
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

const topics: Array<{ id: Topic; label: string; description: string }> = [
  { id: "LEARN", label: "Learn the basics", description: "Markets, orders, risk and beginner terminology." },
  { id: "STOCK_FUNDAMENTALS", label: "Stock fundamentals", description: "Business model, sector, ratios and company quality." },
  { id: "TECHNICAL_ANALYSIS", label: "Technical analysis", description: "Trends, moving averages, RSI, support and resistance." },
  { id: "FUNDAMENTAL_ANALYSIS", label: "Fundamental analysis", description: "Financial statements, profitability, debt and growth." },
  { id: "VALUATION", label: "Valuation", description: "P/E, P/S, EV/EBITDA and assumptions inside a price." },
  { id: "CASH_FLOW", label: "Cash flows", description: "Operating, investing, financing and free cash flow." },
  { id: "MARKET_EVALUATION", label: "Market evaluation", description: "Breadth, rates, earnings, volatility and sector leadership." },
  { id: "PORTFOLIO_EDUCATION", label: "Portfolio education", description: "Diversification, concentration, drawdown and sizing." },
];

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
  const [question, setQuestion] = useState("Explain how a beginner should analyse a stock step by step.");
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

    response = await fetch(`${API_URL}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${active.accessToken}`,
        ...(init.headers ?? {}),
      },
    });
    return response;
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
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Finwiz could not answer right now.");
    } finally {
      setLoading(false);
    }
  }

  if (!session) return null;

  return <main className={styles.shell}>
    <header className={styles.header}>
      <a className={styles.brand} href="/">Stox<span>Sim</span></a>
      <div>
        <span className={styles.beta}>EDUCATIONAL AI</span>
        <a href="/">Back to dashboard</a>
      </div>
    </header>

    <section className={styles.hero}>
      <div>
        <span className={styles.eyebrow}>FINWIZ AI</span>
        <h1>Understand markets.<br />Do not just follow them.</h1>
        <p>Learn financial statements, valuation, technical indicators, cash flow and portfolio risk using plain explanations grounded in StoxSim data.</p>
      </div>
      <div className={styles.guardrail}>
        <strong>Learning assistant, not a tip service</strong>
        <p>Finwiz explains evidence and uncertainty. It does not issue buy, sell, hold or target-price recommendations.</p>
      </div>
    </section>

    <section className={styles.workspace}>
      <aside className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <span>Choose a lesson</span>
          <small>{selectedTopic.description}</small>
        </div>
        <div className={styles.topicList}>
          {topics.map((item) => <button
            type="button"
            key={item.id}
            className={topic === item.id ? styles.topicActive : styles.topic}
            onClick={() => setTopic(item.id)}
          >
            <strong>{item.label}</strong>
            <small>{item.description}</small>
          </button>)}
        </div>
      </aside>

      <section className={styles.chatPanel}>
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
            Ask Finwiz
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
            <small>Company-specific statements use available StoxSim data. Missing data remains explicitly unavailable.</small>
            <button type="submit" disabled={loading || !question.trim()}>
              {loading ? "Finwiz is studying…" : "Explain this"}
            </button>
          </div>
        </form>

        {error && <div className={styles.error}>{error}</div>}

        {answer ? <article className={styles.answer}>
          <div className={styles.answerHeader}>
            <div>
              <span>FINWIZ RESPONSE</span>
              <h2>{selectedTopic.label}</h2>
            </div>
            <div className={styles.answerMeta}>
              <span>{answer.groundedInStoxSimData ? "Grounded in StoxSim data" : "General learning mode"}</span>
              <small>{answer.provider === "GEMINI" ? `Gemini · ${answer.model}` : "Built-in learning fallback"}</small>
            </div>
          </div>
          <div className={styles.answerText}>{answer.answer}</div>
          <div className={styles.suggestions}>
            <span>Continue learning</span>
            <div>
              {answer.suggestedQuestions.map((suggestion) => <button
                type="button"
                key={suggestion}
                onClick={() => setQuestion(suggestion)}
              >{suggestion}</button>)}
            </div>
          </div>
          <p className={styles.disclaimer}>{answer.disclaimer}</p>
        </article> : <div className={styles.emptyState}>
          <div className={styles.orb}>F</div>
          <h2>Ask a question to begin</h2>
          <p>Try “Why can profit increase while operating cash flow decreases?” or add a symbol and ask Finwiz to explain the available fundamentals.</p>
        </div>}
      </section>
    </section>
  </main>;
}
