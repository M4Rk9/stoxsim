"use client";

import { FormEvent, MouseEvent, useEffect, useMemo, useRef, useState } from "react";
import { Client, IMessage } from "@stomp/stompjs";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const MARKET_WS_URL = `${API_URL.replace(/\/$/, "").replace(/^http/, "ws")}/ws/market`;

type MarketRegion = "INDIA" | "UNITED_STATES";
type OrderSide = "BUY" | "SELL";
type OrderType = "MARKET" | "LIMIT";

interface Account {
  id: string;
  marketRegion: MarketRegion;
  currency: string;
  availableCash: number;
  blockedCash: number;
  realizedProfitLoss: number;
}

interface User {
  id: string;
  email: string;
  displayName: string;
  accounts: Account[];
}

interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: User;
}

interface MarketStatus {
  exchange: string;
  phase: string;
  timezone: string;
  currentTime: string;
  nextTransition: string;
  orderDate: string;
}

interface IndexQuote {
  code: string;
  label: string;
  exchange: string;
  instrumentKey: string;
  value?: number;
  change?: number;
  changePercent?: number;
  previousClose?: number;
  dataStatus: "LIVE" | "STALE" | "UNAVAILABLE";
  exchangeTimestamp?: string;
}

interface Position {
  holdingId: string;
  exchange: string;
  symbol: string;
  name: string;
  quantity: number;
  blockedQuantity: number;
  averagePrice: number;
  currentPrice: number;
  investedValue: number;
  marketValue: number;
  unrealizedProfitLoss: number;
  returnPercent: number;
  pricingStatus: "LIVE" | "STALE" | "UNAVAILABLE";
  priceTimestamp?: string;
}

interface Portfolio {
  marketRegion: MarketRegion;
  currency: string;
  startingCapital: number;
  availableCash: number;
  blockedCash: number;
  investedValue: number;
  marketValue: number;
  realizedProfitLoss: number;
  unrealizedProfitLoss: number;
  totalProfitLoss: number;
  totalAccountValue: number;
  totalReturnPercent: number;
  dataStatus: "LIVE" | "STALE" | "UNAVAILABLE";
  valuedAt: string;
  holdings: Position[];
}

interface Instrument {
  id: string;
  provider: string;
  instrumentKey: string;
  marketRegion: MarketRegion;
  tradingSymbol: string;
  name: string;
  exchange: string;
  instrumentType: string;
  currency: string;
  tickSize: number;
}

interface Quote {
  symbol: string;
  exchange: string;
  lastPrice: number;
  bid?: number;
  ask?: number;
  open?: number;
  high?: number;
  low?: number;
  previousClose?: number;
  dataStatus: "LIVE" | "STALE";
  exchangeTimestamp?: string;
}

interface WatchlistItem {
  itemId: string;
  instrumentId: string;
  provider: string;
  instrumentKey: string;
  marketRegion: MarketRegion;
  exchange: string;
  symbol: string;
  name: string;
  instrumentType: string;
  currency: string;
  tickSize: number;
  lastPrice?: number;
  change?: number;
  changePercent?: number;
  dataStatus: "LIVE" | "STALE" | "UNAVAILABLE";
  exchangeTimestamp?: string;
  addedAt: string;
}

interface Watchlist {
  id: string;
  name: string;
  items: WatchlistItem[];
}

interface MarketQuoteMessage {
  instrumentKey: string;
  marketRegion: MarketRegion;
  lastPrice?: number;
  bid?: number;
  ask?: number;
  open?: number;
  high?: number;
  low?: number;
  previousClose?: number;
  exchangeTimestamp?: string;
}

type StreamStatus = "CONNECTING" | "LIVE" | "RECONNECTING" | "OFFLINE";

interface Candle {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume?: number;
}

interface CandleSeries {
  symbol: string;
  interval: string;
  from: string;
  to: string;
  candles: Candle[];
}

interface ChargeBreakdown {
  scheduleVersion: string;
  simulated: boolean;
  turnover: number;
  brokerage: number;
  stt: number;
  exchangeCharges: number;
  gst: number;
  sebiCharges: number;
  stampDuty: number;
  dpCharges: number;
  totalCharges: number;
}

interface PaperOrder {
  id: string;
  symbol: string;
  side: OrderSide;
  orderType: OrderType;
  status: string;
  quantity: number;
  limitPrice?: number;
  executionPrice?: number;
  reservedCash: number;
  rejectionReason?: string;
  createdAt: string;
}

interface Trade {
  id: string;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
  grossValue: number;
  charges: number;
  netCashEffect: number;
  chargeScheduleVersion: string;
  chargesSimulated: boolean;
  executedAt: string;
}

class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

async function rawRequest<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
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
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

const inr = (value?: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 2,
  }).format(value ?? 0);

const number = (value?: number) =>
  new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(value ?? 0);

const dateTime = (value?: string) =>
  value
    ? new Intl.DateTimeFormat("en-IN", {
        day: "2-digit",
        month: "short",
        hour: "2-digit",
        minute: "2-digit",
      }).format(new Date(value))
    : "—";

const isoDate = (monthsAgo: number) => {
  const date = new Date();
  date.setUTCMonth(date.getUTCMonth() - monthsAgo);
  return date.toISOString().slice(0, 10);
};

const phaseLabel = (phase?: string) =>
  ({
    REGULAR: "Market open",
    PRE_OPEN_ORDER_ENTRY: "Pre-open orders",
    PRE_OPEN_MATCHING: "Pre-open matching",
    PRE_OPEN_BUFFER: "Opening buffer",
    AFTER_MARKET: "Market closed",
    HOLIDAY: "Market holiday",
  })[phase ?? ""] ?? "Checking market";

export default function Home() {
  const [session, setSession] = useState<AuthResponse | null>(null);
  const sessionRef = useRef<AuthResponse | null>(null);
  const refreshPromiseRef = useRef<Promise<AuthResponse> | null>(null);
  const [authMode, setAuthMode] = useState<"login" | "register">("register");
  const [authForm, setAuthForm] = useState({ displayName: "", email: "", password: "" });
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [market, setMarket] = useState<MarketStatus | null>(null);
  const [indices, setIndices] = useState<IndexQuote[]>([]);
  const [watchlist, setWatchlist] = useState<Watchlist | null>(null);
  const [streamStatus, setStreamStatus] = useState<StreamStatus>("OFFLINE");
  const [orders, setOrders] = useState<PaperOrder[]>([]);
  const [trades, setTrades] = useState<Trade[]>([]);
  const [search, setSearch] = useState("");
  const [results, setResults] = useState<Instrument[]>([]);
  const [selected, setSelected] = useState<Instrument | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [candles, setCandles] = useState<Candle[]>([]);
  const [chartRange, setChartRange] = useState<1 | 3 | 12>(3);
  const [chartLoading, setChartLoading] = useState(false);
  const [side, setSide] = useState<OrderSide>("BUY");
  const [orderType, setOrderType] = useState<OrderType>("MARKET");
  const [quantity, setQuantity] = useState("1");
  const [limitPrice, setLimitPrice] = useState("");
  const [chargeEstimate, setChargeEstimate] = useState<ChargeBreakdown | null>(null);
  const [loading, setLoading] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const selectedRef = useRef<Instrument | null>(null);

  const token = session?.accessToken;
  const openOrders = useMemo(() => orders.filter((order) => order.status === "OPEN"), [orders]);
  const watchedItem = useMemo(
    () => watchlist?.items.find((item) => item.instrumentId === selected?.id),
    [watchlist, selected],
  );
  const displayedIndices = useMemo<IndexQuote[]>(
    () => indices.length ? indices : Array.from({ length: 6 }, (_, index) => ({
      code: `loading-${index}`,
      label: "Loading index",
      exchange: "",
      instrumentKey: "",
      dataStatus: "UNAVAILABLE",
    })),
    [indices],
  );

  useEffect(() => {
    const saved = window.localStorage.getItem("stoxsim-session");
    if (!saved) return;
    try {
      const restored = JSON.parse(saved) as AuthResponse;
      sessionRef.current = restored;
      setSession(restored);
      void loadDashboard(restored.accessToken);
    } catch {
      window.localStorage.removeItem("stoxsim-session");
    }
  }, []);

  useEffect(() => {
    selectedRef.current = selected;
  }, [selected]);

  useEffect(() => {
    if (!token) {
      setStreamStatus("OFFLINE");
      return;
    }

    let active = true;
    let connectedOnce = false;
    const client = new Client({
      brokerURL: MARKET_WS_URL,
      reconnectDelay: 5_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      connectHeaders: { Authorization: `Bearer ${token}` },
      beforeConnect: () => {
        if (active) setStreamStatus(connectedOnce ? "RECONNECTING" : "CONNECTING");
      },
      onConnect: () => {
        if (!active) return;
        connectedOnce = true;
        setStreamStatus("LIVE");
        client.subscribe("/topic/market/quotes", (message: IMessage) => {
          try {
            applyMarketTick(JSON.parse(message.body) as MarketQuoteMessage);
          } catch {
            // Ignore malformed provider frames and keep the last valid prices.
          }
        });
        void loadIndices();
        void loadWatchlist();
      },
      onStompError: () => active && setStreamStatus("RECONNECTING"),
      onWebSocketError: () => active && setStreamStatus("RECONNECTING"),
      onWebSocketClose: () => active && setStreamStatus(connectedOnce ? "RECONNECTING" : "OFFLINE"),
    });

    setStreamStatus("CONNECTING");
    client.activate();
    return () => {
      active = false;
      setStreamStatus("OFFLINE");
      void client.deactivate();
    };
  }, [token]);

  useEffect(() => {
    if (!token || !selected) {
      setCandles([]);
      return;
    }
    void loadCandles(selected, chartRange);
  }, [token, selected, chartRange]);

  useEffect(() => {
    if (!token || !quote) {
      setChargeEstimate(null);
      return;
    }
    const units = Number(quantity);
    const referencePrice = orderType === "LIMIT" ? Number(limitPrice) : quote.lastPrice;
    if (!Number.isFinite(units) || units <= 0 || !Number.isFinite(referencePrice) || referencePrice <= 0) {
      setChargeEstimate(null);
      return;
    }
    const turnover = units * referencePrice;
    const timer = window.setTimeout(() => {
      authorizedRequest<ChargeBreakdown>(
        `/api/v1/trading/charges/estimate?side=${side}&exchange=NSE&turnover=${turnover}`,
        {},
      ).then(setChargeEstimate).catch(() => setChargeEstimate(null));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [token, quote, quantity, limitPrice, orderType, side]);

  function persistSession(next: AuthResponse | null) {
    sessionRef.current = next;
    setSession(next);
    if (next) {
      window.localStorage.setItem("stoxsim-session", JSON.stringify(next));
    } else {
      window.localStorage.removeItem("stoxsim-session");
    }
  }

  async function rotateSession(): Promise<AuthResponse> {
    if (refreshPromiseRef.current) return refreshPromiseRef.current;
    const active = sessionRef.current;
    if (!active) throw new ApiError("Your session has expired", 401);

    refreshPromiseRef.current = rawRequest<AuthResponse>("/api/v1/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken: active.refreshToken }),
    }).then((next) => {
      persistSession(next);
      return next;
    }).catch((cause) => {
      persistSession(null);
      throw cause;
    }).finally(() => {
      refreshPromiseRef.current = null;
    });
    return refreshPromiseRef.current;
  }

  async function authorizedRequest<T>(
    path: string,
    options: RequestInit = {},
    accessToken?: string,
  ): Promise<T> {
    const activeToken = accessToken ?? sessionRef.current?.accessToken;
    if (!activeToken) throw new ApiError("Please sign in to continue", 401);
    try {
      return await rawRequest<T>(path, options, activeToken);
    } catch (cause) {
      if (!(cause instanceof ApiError) || cause.status !== 401) throw cause;
      const latest = sessionRef.current;
      if (latest && latest.accessToken !== activeToken) {
        return rawRequest<T>(path, options, latest.accessToken);
      }
      const refreshed = await rotateSession();
      return rawRequest<T>(path, options, refreshed.accessToken);
    }
  }

  async function loadDashboard(accessToken: string) {
    setLoading(true);
    setError("");
    try {
      const [nextPortfolio, nextMarket, nextOrders, nextTrades, nextIndices, nextWatchlist] = await Promise.all([
        authorizedRequest<Portfolio>("/api/v1/portfolio?marketRegion=INDIA", {}, accessToken),
        authorizedRequest<MarketStatus>("/api/v1/market/status?exchange=NSE", {}, accessToken),
        authorizedRequest<PaperOrder[]>("/api/v1/orders?marketRegion=INDIA", {}, accessToken),
        authorizedRequest<Trade[]>("/api/v1/trades?marketRegion=INDIA", {}, accessToken),
        authorizedRequest<IndexQuote[]>("/api/v1/market/indices", {}, accessToken),
        authorizedRequest<Watchlist>("/api/v1/watchlists/default", {}, accessToken),
      ]);
      setPortfolio(nextPortfolio);
      setMarket(nextMarket);
      setOrders(nextOrders);
      setTrades(nextTrades);
      setIndices(nextIndices);
      setWatchlist(nextWatchlist);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load your dashboard");
    } finally {
      setLoading(false);
    }
  }

  async function loadIndices() {
    try {
      setIndices(await authorizedRequest<IndexQuote[]>("/api/v1/market/indices"));
    } catch {
      // Preserve the last successful values; each card already carries freshness metadata.
    }
  }

  async function loadWatchlist() {
    try {
      setWatchlist(await authorizedRequest<Watchlist>("/api/v1/watchlists/default"));
    } catch {
      // Preserve the saved watchlist while the connection or token recovers.
    }
  }

  function applyMarketTick(message: MarketQuoteMessage) {
    if (message.lastPrice == null) return;
    const change = message.previousClose == null ? undefined : message.lastPrice - message.previousClose;
    const changePercent = change == null || !message.previousClose
      ? undefined
      : (change / message.previousClose) * 100;

    setIndices((current) => current.map((index) => index.instrumentKey !== message.instrumentKey ? index : {
      ...index,
      value: message.lastPrice,
      previousClose: message.previousClose,
      change,
      changePercent,
      dataStatus: "LIVE",
      exchangeTimestamp: message.exchangeTimestamp,
    }));

    setWatchlist((current) => current ? {
      ...current,
      items: current.items.map((item) => item.instrumentKey !== message.instrumentKey ? item : {
        ...item,
        lastPrice: message.lastPrice,
        change,
        changePercent,
        dataStatus: "LIVE",
        exchangeTimestamp: message.exchangeTimestamp,
      }),
    } : current);

    if (selectedRef.current?.instrumentKey === message.instrumentKey) {
      setQuote((current) => current ? {
        ...current,
        lastPrice: message.lastPrice!,
        bid: message.bid ?? current.bid,
        ask: message.ask ?? current.ask,
        open: message.open ?? current.open,
        high: message.high ?? current.high,
        low: message.low ?? current.low,
        previousClose: message.previousClose ?? current.previousClose,
        dataStatus: "LIVE",
        exchangeTimestamp: message.exchangeTimestamp ?? current.exchangeTimestamp,
      } : current);
    }
  }

  async function loadCandles(instrument: Instrument, range: 1 | 3 | 12) {
    setChartLoading(true);
    try {
      const to = new Date().toISOString().slice(0, 10);
      const series = await authorizedRequest<CandleSeries>(
        `/api/v1/instruments/INDIA/${instrument.exchange}/${encodeURIComponent(instrument.tradingSymbol)}/candles?interval=ONE_DAY&from=${isoDate(range)}&to=${to}`,
      );
      setCandles(series.candles);
    } catch (cause) {
      setCandles([]);
      setError(cause instanceof Error ? cause.message : "Historical prices are unavailable");
    } finally {
      setChartLoading(false);
    }
  }

  async function submitAuth(event: FormEvent) {
    event.preventDefault();
    setWorking(true);
    setError("");
    try {
      const path = authMode === "register" ? "/api/v1/auth/register" : "/api/v1/auth/login";
      const body = authMode === "register" ? authForm : { email: authForm.email, password: authForm.password };
      const authenticated = await rawRequest<AuthResponse>(path, {
        method: "POST",
        body: JSON.stringify(body),
      });
      persistSession(authenticated);
      await loadDashboard(authenticated.accessToken);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Authentication failed");
    } finally {
      setWorking(false);
    }
  }

  async function searchInstruments(event: FormEvent) {
    event.preventDefault();
    if (!token || search.trim().length < 2) return;
    setWorking(true);
    setError("");
    try {
      const found = await authorizedRequest<Instrument[]>(
        `/api/v1/instruments/search?marketRegion=INDIA&q=${encodeURIComponent(search.trim())}`,
        {},
      );
      setResults(found.filter((item) =>
        item.exchange === "NSE" && (item.instrumentType === "EQUITY" || item.instrumentType === "ETF")
      ).slice(0, 8));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Search failed");
    } finally {
      setWorking(false);
    }
  }

  async function chooseInstrument(instrument: Instrument) {
    if (!token) return;
    setSelected(instrument);
    setResults([]);
    setSearch(instrument.tradingSymbol);
    setWorking(true);
    setError("");
    try {
      const nextQuote = await authorizedRequest<Quote>(
        `/api/v1/instruments/INDIA/${instrument.exchange}/${instrument.tradingSymbol}/quote`,
        {},
      );
      setQuote(nextQuote);
      setLimitPrice(String(nextQuote.lastPrice));
    } catch (cause) {
      setQuote(null);
      setError(cause instanceof Error ? cause.message : "Quote unavailable");
    } finally {
      setWorking(false);
    }
  }

  async function addSelectedToWatchlist() {
    if (!selected) return;
    setWorking(true);
    setError("");
    try {
      const next = await authorizedRequest<Watchlist>("/api/v1/watchlists/default/items", {
        method: "POST",
        body: JSON.stringify({
          marketRegion: selected.marketRegion,
          exchange: selected.exchange,
          symbol: selected.tradingSymbol,
        }),
      });
      setWatchlist(next);
      setNotice(`${selected.tradingSymbol} added to your live watchlist.`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Stock could not be added to your watchlist");
    } finally {
      setWorking(false);
    }
  }

  async function removeWatchlistItem(item: WatchlistItem) {
    setWorking(true);
    setError("");
    try {
      const next = await authorizedRequest<Watchlist>(`/api/v1/watchlists/default/items/${item.itemId}`, {
        method: "DELETE",
      });
      setWatchlist(next);
      setNotice(`${item.symbol} removed from your watchlist.`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Stock could not be removed from your watchlist");
    } finally {
      setWorking(false);
    }
  }

  function chooseWatchlistItem(item: WatchlistItem) {
    void chooseInstrument({
      id: item.instrumentId,
      provider: item.provider,
      instrumentKey: item.instrumentKey,
      marketRegion: item.marketRegion,
      tradingSymbol: item.symbol,
      name: item.name,
      exchange: item.exchange,
      instrumentType: item.instrumentType,
      currency: item.currency,
      tickSize: item.tickSize,
    });
  }

  async function placeOrder(event: FormEvent) {
    event.preventDefault();
    if (!token || !selected) return;
    setWorking(true);
    setError("");
    setNotice("");
    try {
      await authorizedRequest<PaperOrder>(
        "/api/v1/orders",
        {
          method: "POST",
          headers: { "Idempotency-Key": crypto.randomUUID() },
          body: JSON.stringify({
            marketRegion: "INDIA",
            exchange: "NSE",
            symbol: selected.tradingSymbol,
            side,
            orderType,
            quantity: Number(quantity),
            limitPrice: orderType === "LIMIT" ? Number(limitPrice) : null,
          }),
        },
      );
      setNotice(`${side === "BUY" ? "Buy" : "Sell"} order submitted for ${selected.tradingSymbol}.`);
      await loadDashboard(token);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Order could not be submitted");
    } finally {
      setWorking(false);
    }
  }

  async function cancelOrder(orderId: string) {
    if (!token) return;
    setWorking(true);
    setError("");
    try {
      await authorizedRequest<PaperOrder>(`/api/v1/orders/${orderId}`, { method: "DELETE" });
      setNotice("Order cancelled and blocked resources released.");
      await loadDashboard(token);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Order could not be cancelled");
    } finally {
      setWorking(false);
    }
  }

  async function logout() {
    if (session) {
      await rawRequest<void>(
        "/api/v1/auth/logout",
        { method: "POST", body: JSON.stringify({ refreshToken: session.refreshToken }) },
      ).catch(() => undefined);
    }
    persistSession(null);
    setPortfolio(null);
    setOrders([]);
    setTrades([]);
    setWatchlist(null);
    setIndices([]);
  }

  if (!session) {
    return (
      <main className="welcomeShell">
        <nav className="welcomeNav">
          <Brand />
          <span className="navNote">India live · US next</span>
        </nav>
        <section className="welcomeGrid">
          <div className="welcomeCopy">
            <span className="eyebrow">PAPER TRADING, BUILT LIKE THE REAL THING</span>
            <h1>Practise the market.<br /><span>Risk nothing.</span></h1>
            <p>Start with ₹5,00,000, research NSE stocks, place realistic market and limit orders, and learn from every decision.</p>
            <div className="featureRow">
              <span>Live market structure</span><span>Simulated charges</span><span>Portfolio analytics</span>
            </div>
          </div>
          <form className="authCard" onSubmit={submitAuth}>
            <div className="authTabs">
              <button type="button" className={authMode === "register" ? "active" : ""} onClick={() => setAuthMode("register")}>Create account</button>
              <button type="button" className={authMode === "login" ? "active" : ""} onClick={() => setAuthMode("login")}>Sign in</button>
            </div>
            <h2>{authMode === "register" ? "Your first virtual portfolio" : "Welcome back"}</h2>
            <p>{authMode === "register" ? "Free, educational and no brokerage account required." : "Continue practising where you stopped."}</p>
            {authMode === "register" && (
              <label>Display name<input required minLength={2} value={authForm.displayName} onChange={(event) => setAuthForm({ ...authForm, displayName: event.target.value })} placeholder="Market learner" /></label>
            )}
            <label>Email<input required type="email" value={authForm.email} onChange={(event) => setAuthForm({ ...authForm, email: event.target.value })} placeholder="you@example.com" /></label>
            <label>Password<input required minLength={8} type="password" value={authForm.password} onChange={(event) => setAuthForm({ ...authForm, password: event.target.value })} placeholder="At least 8 characters" /></label>
            {error && <div className="message errorMessage">{error}</div>}
            <button className="primaryButton wide" disabled={working}>{working ? "Setting up…" : authMode === "register" ? "Start with ₹5,00,000" : "Open dashboard"}</button>
            <small>StoxSim is an educational simulator. No real orders or investment advice.</small>
          </form>
        </section>
      </main>
    );
  }

  return (
    <main className="appShell">
      <header className="appHeader">
        <Brand />
        <div className="marketSwitch" aria-label="Market selection">
          <button className="active"><span>🇮🇳</span> India</button>
          <button disabled title="United States market is the next product phase"><span>🇺🇸</span> US <small>NEXT</small></button>
        </div>
        <div className="profileBlock"><span className="avatar">{session.user.displayName.slice(0, 1).toUpperCase()}</span><div><strong>{session.user.displayName}</strong><button onClick={logout}>Sign out</button></div></div>
      </header>

      <section className={`marketBanner ${market?.phase === "REGULAR" ? "open" : "closed"}`}>
        <div><span className="pulse" /><div><strong>{phaseLabel(market?.phase)}</strong><small>NSE · {market?.timezone ?? "Asia/Kolkata"}</small></div></div>
        <div className="bannerStatusGroup"><div className={`streamBadge ${streamStatus.toLowerCase()}`}><i />{streamStatus === "LIVE" ? "REAL-TIME STREAM" : streamStatus}</div><div className="bannerRight"><span>Next transition</span><strong>{dateTime(market?.nextTransition)}</strong></div></div>
      </section>

      <section className="indexStrip" aria-label="Indian market indices">
        {displayedIndices.map((index) => {
          const rising = (index.change ?? 0) >= 0;
          return <article className="indexCard" key={index.code}>
            <div><span>{index.label}</span><small>{index.exchange || "MARKET"}</small></div>
            <strong>{index.value == null ? "—" : number(index.value)}</strong>
            <div className="indexMove"><span className={index.value == null ? "muted" : rising ? "positive" : "negative"}>{index.value == null ? "Awaiting data" : `${rising ? "+" : ""}${number(index.change)} · ${rising ? "+" : ""}${number(index.changePercent)}%`}</span><i className={index.dataStatus.toLowerCase()} title={`${index.dataStatus} data`} /></div>
          </article>;
        })}
      </section>

      {(error || notice) && <div className={`message ${error ? "errorMessage" : "successMessage"}`}>{error || notice}<button onClick={() => { setError(""); setNotice(""); }}>×</button></div>}

      <section className="dashboardHeading">
        <div><span className="eyebrow">INDIA PORTFOLIO</span><h1>Good day, {session.user.displayName.split(" ")[0]}.</h1></div>
        <div className={`dataBadge ${(portfolio?.dataStatus ?? "LIVE").toLowerCase()}`}><span />{portfolio?.dataStatus ?? "LIVE"} DATA</div>
      </section>

      <section className="metricGrid" aria-busy={loading}>
        <Metric label="Account value" value={inr(portfolio?.totalAccountValue)} sub={`Started with ${inr(portfolio?.startingCapital)}`} />
        <Metric label="Available cash" value={inr(portfolio?.availableCash)} sub={`${inr(portfolio?.blockedCash)} blocked`} />
        <Metric label="Unrealized P/L" value={inr(portfolio?.unrealizedProfitLoss)} tone={(portfolio?.unrealizedProfitLoss ?? 0) >= 0 ? "positive" : "negative"} sub="Across current holdings" />
        <Metric label="Total return" value={`${number(portfolio?.totalReturnPercent)}%`} tone={(portfolio?.totalReturnPercent ?? 0) >= 0 ? "positive" : "negative"} sub={`${inr(portfolio?.totalProfitLoss)} all time`} />
      </section>

      <section className="workspaceGrid">
        <div className="leftRail">
          <article className="panel searchPanel">
            <div className="panelHeading"><div><span className="kicker">DISCOVER</span><h2>Find an NSE stock</h2></div><span className="shortcut">⌘ K</span></div>
            <form className="searchBox" onSubmit={searchInstruments}><span>⌕</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search Reliance, TCS, HDFC Bank…" /><button disabled={working || search.trim().length < 2}>Search</button></form>
            {results.length > 0 && <div className="searchResults">{results.map((instrument) => <button key={instrument.id} onClick={() => chooseInstrument(instrument)}><div><strong>{instrument.tradingSymbol}</strong><span>{instrument.name}</span></div><small>{instrument.exchange} · {instrument.instrumentType}</small></button>)}</div>}
            {!selected && <div className="searchEmpty"><span>⌁</span><p>Search for a company to view its quote and open a paper order ticket.</p></div>}
            {selected && quote && (
              <div className="quoteCard">
                <div className="quoteTop"><div><span className="symbolIcon">{selected.tradingSymbol.slice(0, 2)}</span><div><h3>{selected.tradingSymbol}</h3><p>{selected.name}</p></div></div><div className="quoteActions"><button type="button" className={watchedItem ? "watchButton watching" : "watchButton"} disabled={working || Boolean(watchedItem)} onClick={addSelectedToWatchlist}>{watchedItem ? "★ Watching" : "☆ Watch"}</button><span className={`quoteStatus ${quote.dataStatus.toLowerCase()}`}>{quote.dataStatus}</span></div></div>
                <div className="quotePrice"><strong>{inr(quote.lastPrice)}</strong><span>{dateTime(quote.exchangeTimestamp)}</span></div>
                <div className="quoteStats"><div><span>Open</span><strong>{inr(quote.open)}</strong></div><div><span>High</span><strong>{inr(quote.high)}</strong></div><div><span>Low</span><strong>{inr(quote.low)}</strong></div><div><span>Prev. close</span><strong>{inr(quote.previousClose)}</strong></div></div>
                <div className="chartBlock">
                  <div className="chartHeader"><div><span>Historical close</span><small>Upstox daily candles</small></div><div className="rangeTabs">{([1, 3, 12] as const).map((range) => <button type="button" key={range} className={chartRange === range ? "active" : ""} onClick={() => setChartRange(range)}>{range === 12 ? "1Y" : `${range}M`}</button>)}</div></div>
                  <PriceChart candles={candles} loading={chartLoading} />
                </div>
              </div>
            )}
          </article>

          <article className="panel">
            <div className="panelHeading"><div><span className="kicker">PORTFOLIO</span><h2>Your holdings</h2></div><span className="panelMeta">{portfolio?.holdings.length ?? 0} positions</span></div>
            <div className="tableWrap"><table><thead><tr><th>Stock</th><th>Qty</th><th>Avg. cost</th><th>LTP</th><th>Value</th><th>P/L</th></tr></thead><tbody>
              {(portfolio?.holdings ?? []).map((position) => <tr key={position.holdingId}><td><strong>{position.symbol}</strong><small>{position.name}</small></td><td>{position.quantity}<small>{position.blockedQuantity ? `${position.blockedQuantity} blocked` : "available"}</small></td><td>{inr(position.averagePrice)}</td><td>{inr(position.currentPrice)}<small className={position.pricingStatus.toLowerCase()}>{position.pricingStatus}</small></td><td>{inr(position.marketValue)}</td><td className={position.unrealizedProfitLoss >= 0 ? "positive" : "negative"}>{inr(position.unrealizedProfitLoss)}<small>{number(position.returnPercent)}%</small></td></tr>)}
              {!portfolio?.holdings.length && <tr><td colSpan={6} className="emptyCell">Your first executed buy will appear here.</td></tr>}
            </tbody></table></div>
          </article>

          <article className="panel">
            <div className="panelHeading"><div><span className="kicker">ACTIVITY</span><h2>Recent trades</h2></div><span className="panelMeta">Charges included</span></div>
            <div className="activityList">{trades.slice(0, 6).map((trade) => <div className="activityRow" key={trade.id}><span className={`sidePill ${trade.side.toLowerCase()}`}>{trade.side}</span><div><strong>{trade.symbol} · {trade.quantity} shares</strong><small>{dateTime(trade.executedAt)} · {trade.chargeScheduleVersion}</small></div><div><strong>{inr(trade.netCashEffect)}</strong><small>{inr(trade.charges)} simulated charges</small></div></div>)}{!trades.length && <div className="emptyState">No executed trades yet.</div>}</div>
          </article>
        </div>

        <aside className="rightRail">
          <article className="panel watchlistPanel">
            <div className="panelHeading"><div><span className="kicker">LIVE LIST</span><h2>{watchlist?.name ?? "My Watchlist"}</h2></div><span className="countPill">{watchlist?.items.length ?? 0}</span></div>
            <div className="watchlistRows">
              {(watchlist?.items ?? []).map((item) => {
                const rising = (item.change ?? 0) >= 0;
                return <div className="watchlistRow" key={item.itemId}>
                  <button type="button" className="watchlistSelect" onClick={() => chooseWatchlistItem(item)}><span><strong>{item.symbol}</strong><small>{item.exchange} · {item.dataStatus}</small></span><span><strong>{item.lastPrice == null ? "—" : inr(item.lastPrice)}</strong><small className={rising ? "positive" : "negative"}>{item.changePercent == null ? "Awaiting tick" : `${rising ? "+" : ""}${number(item.changePercent)}%`}</small></span></button>
                  <button type="button" className="watchlistRemove" aria-label={`Remove ${item.symbol} from watchlist`} title="Remove" disabled={working} onClick={() => removeWatchlistItem(item)}>×</button>
                </div>;
              })}
              {!watchlist?.items.length && <div className="emptyState compact">Open a stock and choose Watch to receive continuous price updates.</div>}
            </div>
          </article>

          <form className="panel orderTicket" onSubmit={placeOrder}>
            <div className="panelHeading"><div><span className="kicker">PAPER ORDER</span><h2>Order ticket</h2></div><span className="deliveryPill">DELIVERY</span></div>
            <div className="sideToggle"><button type="button" className={side === "BUY" ? "buy active" : ""} onClick={() => setSide("BUY")}>Buy</button><button type="button" className={side === "SELL" ? "sell active" : ""} onClick={() => setSide("SELL")}>Sell</button></div>
            <label>Stock<input value={selected?.tradingSymbol ?? ""} readOnly placeholder="Choose a stock from search" /></label>
            <div className="fieldGrid"><label>Order type<select value={orderType} onChange={(event) => setOrderType(event.target.value as OrderType)}><option value="MARKET">Market</option><option value="LIMIT">Limit</option></select></label><label>Quantity<input type="number" min="1" step="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label></div>
            {orderType === "LIMIT" && <label>Limit price<input type="number" min="0.01" step={selected?.tickSize ?? 0.05} value={limitPrice} onChange={(event) => setLimitPrice(event.target.value)} /></label>}
            <div className="estimateBox"><div><span>Estimated turnover</span><strong>{inr(chargeEstimate?.turnover)}</strong></div><div><span>Simulated charges</span><strong>{inr(chargeEstimate?.totalCharges)}</strong></div><div className="estimateTotal"><span>{side === "BUY" ? "Estimated debit" : "Estimated credit"}</span><strong>{inr(chargeEstimate ? chargeEstimate.turnover + (side === "BUY" ? chargeEstimate.totalCharges : -chargeEstimate.totalCharges) : 0)}</strong></div>{chargeEstimate && <small>{chargeEstimate.scheduleVersion} · final amount uses execution price</small>}</div>
            <button className={`orderButton ${side.toLowerCase()}`} disabled={!selected || working}>{working ? "Working…" : `${side === "BUY" ? "Place buy" : "Place sell"} order`}</button>
            <p className="ticketNote">Market orders use disadvantageous simulated slippage. No real brokerage order is placed.</p>
          </form>

          <article className="panel">
            <div className="panelHeading"><div><span className="kicker">PENDING</span><h2>Open orders</h2></div><span className="countPill">{openOrders.length}</span></div>
            <div className="orderList">{openOrders.map((order) => <div className="openOrder" key={order.id}><div><span className={`sidePill ${order.side.toLowerCase()}`}>{order.side}</span><strong>{order.symbol}</strong><small>{order.quantity} · {order.orderType}{order.limitPrice ? ` @ ${inr(order.limitPrice)}` : ""}</small></div><button type="button" onClick={() => cancelOrder(order.id)}>Cancel</button></div>)}{!openOrders.length && <div className="emptyState compact">No cash or shares are currently blocked by open orders.</div>}</div>
          </article>

          <article className="learnCard"><span>STOXSIM NOTE</span><h3>Charges change the lesson.</h3><p>Buy charges are included in your cost basis. Sell charges reduce realized returns. Every schedule is versioned and marked simulated.</p></article>
        </aside>
      </section>
      <footer><span>StoxSim · Educational paper trading</span><span>Quotes may be live, stale or unavailable. Not investment advice.</span></footer>
    </main>
  );
}

function Brand() {
  return <a className="brand" href="#" aria-label="StoxSim home"><span className="brandMark"><i /><i /><i /></span><span>Stox<span>Sim</span></span></a>;
}

function Metric({ label, value, sub, tone = "" }: { label: string; value: string; sub: string; tone?: string }) {
  return <article className="metric"><span>{label}</span><strong className={tone}>{value}</strong><small>{sub}</small></article>;
}

function PriceChart({ candles, loading }: { candles: Candle[]; loading: boolean }) {
  const [hovered, setHovered] = useState<number | null>(null);
  const ordered = useMemo(
    () => [...candles]
      .filter((candle) => Number.isFinite(candle.close))
      .sort((left, right) => new Date(left.timestamp).getTime() - new Date(right.timestamp).getTime()),
    [candles],
  );

  if (loading) return <div className="chartEmpty"><span className="chartLoader" />Loading historical prices…</div>;
  if (!ordered.length) return <div className="chartEmpty">Historical prices are unavailable for this selection.</div>;

  const width = 720;
  const height = 240;
  const left = 18;
  const right = 18;
  const top = 18;
  const bottom = 30;
  const values = ordered.map((candle) => candle.close);
  const minimum = Math.min(...values);
  const maximum = Math.max(...values);
  const spread = Math.max(maximum - minimum, maximum * 0.005, 1);
  const points = ordered.map((candle, index) => ({
    candle,
    x: left + (index / Math.max(ordered.length - 1, 1)) * (width - left - right),
    y: top + ((maximum - candle.close) / spread) * (height - top - bottom),
  }));
  const line = points.map((point, index) => `${index ? "L" : "M"}${point.x.toFixed(2)},${point.y.toFixed(2)}`).join(" ");
  const area = `${line} L${points.at(-1)?.x},${height - bottom} L${points[0].x},${height - bottom} Z`;
  const active = hovered == null ? points.at(-1)! : points[hovered];
  const rising = ordered.at(-1)!.close >= ordered[0].close;

  function move(event: MouseEvent<SVGSVGElement>) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const ratio = Math.min(1, Math.max(0, (event.clientX - bounds.left) / bounds.width));
    setHovered(Math.round(ratio * (points.length - 1)));
  }

  return <div className="priceChart">
    <div className="chartReadout"><div><strong>{inr(active.candle.close)}</strong><span>{new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", year: "numeric" }).format(new Date(active.candle.timestamp))}</span></div><div><span>High {inr(active.candle.high)}</span><span>Low {inr(active.candle.low)}</span></div></div>
    <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Historical closing-price chart" onMouseMove={move} onMouseLeave={() => setHovered(null)}>
      <defs><linearGradient id="chartFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor={rising ? "#0b8f55" : "#c33c3c"} stopOpacity=".22" /><stop offset="100%" stopColor={rising ? "#0b8f55" : "#c33c3c"} stopOpacity="0" /></linearGradient></defs>
      {[0.25, 0.5, 0.75].map((ratio) => <line key={ratio} x1={left} x2={width - right} y1={top + ratio * (height - top - bottom)} y2={top + ratio * (height - top - bottom)} className="chartGridLine" />)}
      <path d={area} fill="url(#chartFill)" />
      <path d={line} className={rising ? "chartLine rising" : "chartLine falling"} />
      <line x1={active.x} x2={active.x} y1={top} y2={height - bottom} className="chartCrosshair" />
      <circle cx={active.x} cy={active.y} r="5" className={rising ? "chartPoint rising" : "chartPoint falling"} />
      <text x={left} y={height - 7} className="chartAxisLabel">{new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short" }).format(new Date(ordered[0].timestamp))}</text>
      <text x={width - right} y={height - 7} textAnchor="end" className="chartAxisLabel">{new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short" }).format(new Date(ordered.at(-1)!.timestamp))}</text>
    </svg>
  </div>;
}
