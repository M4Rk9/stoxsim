"use client";

import { GoogleSignInLink } from "./components/GoogleSignInLink";
import HomeRecommendation from "./components/HomeRecommendation";
import Notifications from "./components/Notifications";
import { recordProductActivity } from "./lib/product-activity";

import { financialValue } from "./lib/financial-units";
import PricingNotice, { PortfolioPricingNotice, priceSourceLabel } from "./components/PricingNotice";

import { EMAIL_GUIDANCE } from "./lib/email-guidance";

import { FormEvent, MouseEvent, useEffect, useMemo, useRef, useState } from "react";
import Image from "next/image";
import { Client, IMessage } from "@stomp/stompjs";
import {
  FirstTradeCoach,
  OnboardingJourney,
  type OnboardingState,
} from "./components/OnboardingJourney";
import FeatureJourney, { type JourneyState } from "./components/FeatureJourney";
import FinwizTradeFeedback, { type FinwizPortfolioFeedback } from "./components/FinwizTradeFeedback";

import { marketAccount } from "./lib/portfolio-selection";
import { readStockOrder, stockHref } from "./lib/stock-links";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const MARKET_WS_URL = `${API_URL.replace(/\/$/, "").replace(/^http/, "ws")}/ws/market`;

const HOME_FAQ = [
  {
    question: "What is paper trading?",
    answer: "Paper trading is the practice of making simulated market decisions with virtual money. It can help you learn how orders and portfolios work without placing real brokerage orders.",
  },
  {
    question: "Can I practise Indian and US stocks on StoxSim?",
    answer: "Yes. StoxSim provides separate virtual portfolios for Indian and US markets, with market-specific currencies, sessions and available instruments.",
  },
  {
    question: "Does StoxSim use real money?",
    answer: "No. StoxSim is an educational simulator. It does not accept deposits, hold securities or send orders to a stock exchange or brokerage account.",
  },
];

const HOME_FAQ_STRUCTURED_DATA = {
  "@context": "https://schema.org",
  "@type": "FAQPage",
  mainEntity: HOME_FAQ.map(({ question, answer }) => ({
    "@type": "Question",
    name: question,
    acceptedAnswer: { "@type": "Answer", text: answer },
  })),
};

type MarketRegion = "INDIA" | "UNITED_STATES";
type CurrencyCode = "INR" | "USD";
type OrderSide = "BUY" | "SELL";
type OrderType = "MARKET" | "LIMIT";
type MarketDataStatus = "LIVE" | "CLOSED" | "STALE" | "UNAVAILABLE";
type ChartRange = 1 | 3 | 6 | 12 | 36 | 60;
type FinancialPeriod = "quarterly" | "yearly";
type AccountKind = "STANDARD" | "SANDBOX";
type SubscriptionPlan = "FREE" | "PLUS" | "PRO";

interface Account {
  id: string;
  marketRegion: MarketRegion;
  accountKind: AccountKind;
  sandboxPlan?: SubscriptionPlan;
  sandboxSlot: number;
  accountLabel: string;
  currency: string;
  startingCapital: number;
  availableCash: number;
  blockedCash: number;
  realizedProfitLoss: number;
  active: boolean;
  leaderboardEligible: boolean;
}

interface User {
  id: string;
  email: string;
  displayName: string;
  emailVerified: boolean;
  accounts: Account[];
}

interface AuthResponse {
  accessToken: string;
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
  pricingSource?: string;
  dataStatus: MarketDataStatus;
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
  pricingSource?: string;
  pricingStatus: MarketDataStatus;
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
  pricingSource?: string;
  dataStatus: MarketDataStatus;
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
  volume?: number;
  pricingSource?: string;
  dataStatus: MarketDataStatus;
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
  pricingSource?: string;
  dataStatus: MarketDataStatus;
  exchangeTimestamp?: string;
  addedAt: string;
}

interface Watchlist {
  id: string;
  name: string;
  items: WatchlistItem[];
}

interface MarketQuoteMessage {
  pricingSource?: string;
  instrumentKey: string;
  marketRegion: MarketRegion;
  lastPrice?: number;
  bid?: number;
  ask?: number;
  open?: number;
  high?: number;
  low?: number;
  previousClose?: number;
  volume?: number;
  exchangeTimestamp?: string;
}

interface MarketMover {
  instrumentKey: string;
  symbol: string;
  name: string;
  exchange: string;
  lastPrice: number;
  change: number;
  changePercent: number;
  volume?: number;
  pricingSource?: string;
  dataStatus: MarketDataStatus;
  priceTimestamp?: string;
}

interface MarketMovers {
  universe: string;
  generatedAt?: string;
  pricingSource?: string;
  dataStatus: MarketDataStatus;
  gainers: MarketMover[];
  losers: MarketMover[];
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

interface CompanyProfile {
  description?: string;
  sector?: string;
  sectorMarketCapInrCrore?: number;
  sectorMarketCapInrFormatted?: string;
}

interface FundamentalRatio {
  name: string;
  companyValue?: string;
  sectorValue?: string;
}

interface FinancialHistoryPoint {
  period: string;
  value: number;
  change?: string;
}

interface FinancialMetric {
  category: string;
  history: FinancialHistoryPoint[];
}

interface FinancialPerformance {
  statementType?: string;
  timePeriod?: string;
  unitsIn?: string;
  metrics: FinancialMetric[];
}

interface StockInsights {
  provider: string;
  symbol: string;
  isin?: string;
  asOf: string;
  status: "AVAILABLE" | "PARTIAL" | "UNAVAILABLE";
  profile?: CompanyProfile;
  ratios: FundamentalRatio[];
  financials?: FinancialPerformance;
  message?: string;
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
  finwizFeedback?: FinwizPortfolioFeedback;
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
  constructor(
    message: string,
    readonly status: number,
    readonly requestId?: string,
  ) {
    super(message);
  }
}

async function rawRequest<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
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
    const requestId = response.headers.get("X-Request-ID") ?? undefined;
    const message = payload?.message ?? `Request failed with status ${response.status}`;
    const actionableMessage = response.status >= 500 && requestId
      ? `${message} Request ID: ${requestId}`
      : message;
    throw new ApiError(actionableMessage, response.status, requestId);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

const money = (value?: number, currency: CurrencyCode = "INR") =>
  new Intl.NumberFormat(currency === "INR" ? "en-IN" : "en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value ?? 0);

const moneyOrDash = (value?: number, currency: CurrencyCode = "INR") =>
  value == null || !Number.isFinite(value) ? "—" : money(value, currency);

const inr = (value?: number) => money(value, "INR");
const inrOrDash = (value?: number) => moneyOrDash(value, "INR");

const number = (value?: number) =>
  new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(value ?? 0);

const compactNumber = (value?: number) =>
  value == null || !Number.isFinite(value)
    ? "—"
    : new Intl.NumberFormat("en-IN", { notation: "compact", maximumFractionDigits: 2 }).format(value);

const crore = (value?: number) =>
  value == null || !Number.isFinite(value)
    ? "—"
    : `₹${new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(value)} Cr`;

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
    PRE_MARKET: "Pre-market",
    PRE_OPEN_ORDER_ENTRY: "Pre-open orders",
    PRE_OPEN_MATCHING: "Pre-open matching",
    PRE_OPEN_BUFFER: "Opening buffer",
    AFTER_HOURS: "After-hours",
    AFTER_MARKET: "Market closed",
    HOLIDAY: "Market holiday",
    CALENDAR_UNAVAILABLE: "Schedule unavailable",
    CLOSING_AUCTION: "Closing auction — matching paused",
  })[phase ?? ""] ?? "Checking market";

export default function Home() {
  const [session, setSession] = useState<AuthResponse | null>(null);
  const sessionRef = useRef<AuthResponse | null>(null);
  const refreshPromiseRef = useRef<Promise<AuthResponse> | null>(null);
  const [authMode, setAuthMode] = useState<"login" | "register">("register");
  const [authForm, setAuthForm] = useState({
    displayName: "",
    email: "",
    password: "",
    termsAccepted: false,
  });
  const [marketRegion, setMarketRegion] = useState<MarketRegion>("INDIA");
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [activeAccountId, setActiveAccountId] = useState("");
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [market, setMarket] = useState<MarketStatus | null>(null);
  const [indices, setIndices] = useState<IndexQuote[]>([]);
  const [movers, setMovers] = useState<MarketMovers | null>(null);
  const [moverTab, setMoverTab] = useState<"gainers" | "losers">("gainers");
  const [watchlist, setWatchlist] = useState<Watchlist | null>(null);
  const [streamStatus, setStreamStatus] = useState<StreamStatus>("OFFLINE");
  const [orders, setOrders] = useState<PaperOrder[]>([]);
  const [trades, setTrades] = useState<Trade[]>([]);
  const [search, setSearch] = useState("");
  const [results, setResults] = useState<Instrument[]>([]);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState("");
  const [searchRetry, setSearchRetry] = useState(0);
  const searchGeneration = useRef(0);
  const [selected, setSelected] = useState<Instrument | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [candles, setCandles] = useState<Candle[]>([]);
  const [yearCandles, setYearCandles] = useState<Candle[]>([]);
  const [chartRange, setChartRange] = useState<ChartRange>(12);
  const [chartLoading, setChartLoading] = useState(false);
  const [chartError, setChartError] = useState("");
  const [stockInsights, setStockInsights] = useState<StockInsights | null>(null);
  const [financialPeriod, setFinancialPeriod] = useState<FinancialPeriod>("quarterly");
  const [insightsLoading, setInsightsLoading] = useState(false);
  const [side, setSide] = useState<OrderSide>("BUY");
  const [orderType, setOrderType] = useState<OrderType>("MARKET");
  const [quantity, setQuantity] = useState("1");
  const [limitPrice, setLimitPrice] = useState("");
  const [chargeEstimate, setChargeEstimate] = useState<ChargeBreakdown | null>(null);
  const [loading, setLoading] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [onboarding, setOnboarding] = useState<OnboardingState | null>(null);
  const [tradeFeedback, setTradeFeedback] = useState<FinwizPortfolioFeedback | null>(null);
  const selectedRef = useRef<Instrument | null>(null);
  const quoteRequestRef = useRef<AbortController | null>(null);

  const token = session?.accessToken;
  const primaryExchange = marketRegion === "INDIA" ? "NSE" : "NASDAQ";
  const activeCurrency: CurrencyCode = marketRegion === "INDIA" ? "INR" : "USD";
  const activeAccount = useMemo(
    () => accounts.find((account) => account.id === activeAccountId),
    [accounts, activeAccountId],
  );
  const standardAccount = activeAccount?.accountKind !== "SANDBOX";
  const marketName = marketRegion === "INDIA" ? "India" : "USA";
  const displayMoney = (value?: number) => money(value, activeCurrency);
  const displayMoneyOrDash = (value?: number) => moneyOrDash(value, activeCurrency);
  const openOrders = useMemo(() => orders.filter((order) => order.status === "OPEN"), [orders]);
  const visibleWatchlistItems = useMemo(
    () => (watchlist?.items ?? []).filter((item) => item.marketRegion === marketRegion),
    [watchlist, marketRegion],
  );
  const watchedItem = useMemo(
    () => visibleWatchlistItems.find((item) => item.instrumentId === selected?.id),
    [visibleWatchlistItems, selected],
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
  const visibleMovers = movers?.[moverTab] ?? [];
  const marketDataStatus: "LIVE" | "CLOSED" = market?.phase === "REGULAR" ? "LIVE" : "CLOSED";

  useEffect(() => {
    const saved = window.sessionStorage.getItem("stoxsim-session");
    if (saved) {
      try {
        const restored = JSON.parse(saved) as AuthResponse;
        sessionRef.current = restored;
        setSession(restored);
        return;
      } catch {
        window.sessionStorage.removeItem("stoxsim-session");
      }
    }

    void rawRequest<AuthResponse>("/api/v1/auth/refresh", {
      method: "POST",
    }).then(persistSession).catch(() => undefined);
  }, []);

  useEffect(() => {
    selectedRef.current = selected;
  }, [selected]);

  useEffect(() => {
    if (!token) return;
    let active = true;
    void authorizedRequest<Account[]>("/api/v1/accounts", {}, token)
      .then((ownedAccounts) => {
        if (!active) return;
        setAccounts(ownedAccounts);
        const savedId = window.sessionStorage.getItem("stoxsim-active-account");
        const intent = readStockOrder(window.location.search);
        const saved = ownedAccounts.find(a => a.id === savedId);
        const preferred = intent && saved?.active && saved.marketRegion === intent.marketRegion ? saved
          : marketAccount(ownedAccounts, intent?.marketRegion ?? saved?.marketRegion ?? "INDIA") ?? ownedAccounts[0];
        if (preferred) selectAccount(preferred.id, ownedAccounts);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : "Portfolio accounts could not be loaded");
      });
    return () => { active = false; };
  }, [token]);

  useEffect(() => {
    if (!token || !activeAccount) return;
    const intent = readStockOrder(window.location.search);
    if (!intent) return;
    if (!activeAccount.active || activeAccount.marketRegion !== intent.marketRegion) {
      setError("A virtual account for this listing's market is unavailable.");
      return;
    }
    const controller = new AbortController();
    const clearIntent = () => {
      const url = new URL(window.location.href);
      for (const key of ["exchange", "symbol", "side"]) url.searchParams.delete(key);
      window.history.replaceState(window.history.state, "", url);
    };
    void authorizedRequest<Instrument>(
      `/api/v1/instruments/${intent.marketRegion}/${intent.exchange}/${encodeURIComponent(iuthorizedRequest<Account[]>("/api/v1/accounts", {}, token)
      .then((ownedAccounts) => {
        if (!active) return;
        setAccounts(ownedAccounts);
        const savedId = window.sessionStorage.getItem("stoxsim-active-account");
        const intent = readStockOrder(window.location.search);
        const saved = ownedAccounts.find(a => a.id === savedId);
        const preferred = intent && saved?.active && saved.marketRegion === intent.marketRegion ? saved
          : marketAccount(ownedAccounts, intent?.marketRegion ?? saved?.marketRegion ?? "INDIA") ?? ownedAccounts[0];
        if (preferred) selectAccount(preferred.id, ownedAccounts);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : "Portfolio accounts could not be loaded");
      });
    return () => { active = false; };
  }, [token]);

  useEffect(() => {
    if (!token || !activeAccount) return;
    const intent = readStockOrder(window.location.search);
    if (!intent) return;
    if (!activeAccount.active || activeAccount.marketRegion !== intent.marketRegion) {
      setError("A virtual account for this listing's market is unavailable.");
      return;
    }
    const controller = new AbortController();
    const clearIntent = () => {
      const url = new URL(window.location.href);
      for (const key of ["exchange", "symbol", "side"]) url.searchParams.delete(key);
      window.history.replaceState(window.history.state, "", url);
    };
    void authorizedRequest<Instrument>(
      `/api/v1/instruments/${intent.marketRegion}/${intent.exchange}/${encodeURIComponent(intent.symbol)}`,
      { signal: controller.signal },
    ).then(instrument => {
      if (controller.signal.aborted || selectedRef.current) return;
      if (instrument.marketRegion !== intent.marketRegion || instrument.exchange !== intent.exchange
          || instrument.tradingSymbol !== intent.symbol || !["EQUITY", "ETF"].includes(instrument.instrumentType)) {
        throw new Error("This listing is unavailable for virtual orders.");
      }
      clearIntent();
      setSide(intent.side);
      void chooseInstrument(instrument).then(() => {
        document.getElementById("paper-order-ticket")?.focus();
      });
    }).catch(cause => {
      if (controller.signal.aborted) return;
      clearIntent();
      setError(cause instanceof Error ? cause.message : "Could not open the practice order ticket.");
    });
    return () => controller.abort();
  }, [token, activeAccount]);

  useEffect(() => {
    if (!token || !activeAccount) return;
    void loadDashboard(token, activeAccount);
  }, [token, activeAccountId, accounts]);

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
  }, [token, marketRegion]);

  useEffect(() => {
    if (!token) return;
    const timer = window.setInterval(() => {
      void loadMarketOverview();
    }, 60_000);
    return () => window.clearInterval(timer);
  }, [token, marketRegion]);

  useEffect(() => {
    if (!token || !selected) {
      setCandles([]);
      setYearCandles([]);
      return;
    }
    void loadCandles(selected, chartRange);
  }, [token, selected, chartRange]);

  useEffect(() => {
    if (!token || !selected) {
      setStockInsights(null);
      return;
    }
    void loadStockInsights(selected, financialPeriod);
  }, [token, selected, financialPeriod]);

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
        `/api/v1/trading/charges/estimate?side=${side}&exchange=${selected?.exchange ?? primaryExchange}&turnover=${turnover}`,
        {},
      ).then(setChargeEstimate).catch(() => setChargeEstimate(null));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [token, quote, quantity, limitPrice, orderType, side, selected?.exchange, primaryExchange]);

  function persistSession(next: AuthResponse | null) {
    if (sessionRef.current?.user.id !== next?.user.id) {
      cancelQuoteRequest();
      selectedRef.current = null;
      setSelected(null);
      setQuote(null);
    }
    sessionRef.current = next;
    setSession(next);
    if (next) {
      window.sessionStorage.setItem("stoxsim-session", JSON.stringify(next));
    } else {
      window.sessionStorage.removeItem("stoxsim-session");
    }
  }

  async function rotateSession(): Promise<AuthResponse> {
    if (refreshPromiseRef.current) return refreshPromiseRef.current;
    const active = sessionRef.current;
    if (!active) throw new ApiError("Your session has expired", 401);

    refreshPromiseRef.current = rawRequest<AuthResponse>("/api/v1/auth/refresh", {
      method: "POST",
      credentials: "include",
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

  async function loadDashboard(
    accessToken: string,
    account: Account,
  ) {
    const region = account.marketRegion;
    const accountPath = `/api/v1/accounts/${account.id}`;
    setLoading(true);
    setError("");
    const exchange = region === "INDIA" ? "NSE" : "NASDAQ";

    void authorizedRequest<MarketStatus>(`/api/v1/market/status?exchange=${exchange}`, {}, accessToken).then(setMarket).catch(() => undefined);
    void authorizedRequest<PaperOrder[]>(`${accountPath}/orders`, {}, accessToken).then(setOrders).catch(() => undefined);
    void authorizedRequest<Trade[]>(`${accountPath}/trades`, {}, accessToken).then(setTrades).catch(() => undefined);
    void authorizedRequest<IndexQuote[]>(`/api/v1/market/indices?marketRegion=${region}`, {}, accessToken).then(setIndices).catch(() => undefined);
    void authorizedRequest<Watchlist>("/api/v1/watchlists/default", {}, accessToken).then(setWatchlist).catch(() => undefined);
    void authorizedRequest<MarketMovers>(`/api/v1/market/movers?marketRegion=${region}`, {}, accessToken).then(setMovers).catch(() => undefined);
    void authorizedRequest<OnboardingState>("/api/v1/onboarding", {}, accessToken).then(setOnboarding).catch(() => undefined);

    try {
      setPortfolio(await authorizedRequest<Portfolio>(`${accountPath}/portfolio`, {}, accessToken));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load your portfolio");
    } finally {
      setLoading(false);
    }
  }

  async function loadIndices() {
    try {
      setIndices(await authorizedRequest<IndexQuote[]      changePercent,
      dataStatus: "LIVE",
        pricingSource: message.pricingSource ?? "UNKNOWN",
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
        pricingSource: message.pricingSource ?? "UNKNOWN",
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
        volume: message.volume ?? current.volume,
        dataStatus: "LIVE",
        pricingSource: message.pricingSource ?? "UNKNOWN",
        exchangeTimestamp: message.exchangeTimestamp ?? current.exchangeTimestamp,
      } : current);
    }
  }

  async function loadCandles(instrument: Instrument, range: ChartRange) {
    setChartLoading(true);
    setChartError("");
    try {
      const to = new Date().toISOString().slice(0, 10);
      const series = await authorizedRequest<CandleSeries>(
        `/api/v1/instruments/${instrument.marketRegion}/${instrument.exchange}/${encodeURIComponent(instrument.tradingSymbol)}/candles?interval=ONE_DAY&from=${isoDate(range)}&to=${to}`,
      );
      if (selectedRef.current?.id !== instrument.id) return;
      setCandles(series.candles);
      if (range === 12) setYearCandles(series.candles);
    } catch (cause) {
      if (selectedRef.current?.id !== instrument.id) return;
      setCandles([]);
      setChartError(cause instanceof Error ? cause.message : "Historical prices are unavailable");
    } finally {
      if (selectedRef.current?.id === instrument.id) setChartLoading(false);
    }
  }

  async function loadStockInsights(
    instrument: Instrument,
    period: FinancialPeriod,
  ) {
    setInsightsLoading(true);
    try {
      const response = await authorizedRequest<StockInsights>(
        `/api/v1/instruments/${instrument.marketRegion}/${instrument.exchange}/${encodeURIComponent(instrument.tradingSymbol)}/insights?timePeriod=${period}`,
      );
      if (selectedRef.current?.id === instrument.id) setStockInsights(response);
    } catch {
      if (selectedRef.current?.id === instrument.id) setStockInsights(null);
    } finally {
      if (selectedRef.current?.id === instrument.id) setInsightsLoading(false);
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
      if (authMode === "register") setNotice("Account created. Verify your email address. " + EMAIL_GUIDANCE);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Authentication failed");
    } finally {
      setWorking(false);
    }
  }

  function updateSearch(value: string) {
    searchGeneration.current++;
    setSearch(value.slice(0, 100));
    setResults([]);
    setSearchError("");
    setSearchLoading(value.trim().length >= 2);
    setSearchOpen(true);
  }

  function searchInstruments(event: FormEvent) {
    event.preventDefault();
    searchGeneration.current++;
    setSearchOpen(true);
    setSearchRetry(value => value + 1);
  }

  useEffect(() => {
    setResults([]);
    setSearchError("");
    const query = search.trim();
    if (!token || !searchOpen || query.length < 2) { setSearchLoading(false); return; }
    const generation = searchGeneration.current;
    const controller = new AbortController();
    let active = true;
    setSearchLoading(true);
    const timer = window.setTimeout(async () => {
      try {
        const found = await authorizedRequest<Instrument[]>(
          `/api/v1/instruments/search?marketRegion=${marketRegion}&q=${encodeURIComponent(query)}`,
          { signal: controller.signal },
        );
        if (!active || generation !== searchGeneration.current) return;
        const exchanges = marketRegion === "INDIA" ? ["NSE"] : ["NASDAQ", "NYSE", "NYSE_ARCA", "AMEX", "CBOE"];
        setResults(found.filter(item => item.marketRegion === marketRegion
          && (item.instrumentType === "EQUITY" || item.instrumentType === "ETF")
          && exchanges.includes(item.exchange)).slice(0, 8));
      } catch (cause) {
        if (active && generation === searchGeneration.current) setSearchError(cause instanceof Error ? cause.message : "Search unavailable. Please try again.");
      } finally {
        if (active && generation === searchGeneration.current) setSearchLoading(false);
      }
    }, 300);
    return () => { active = false; window.clearTimeout(timer); controller.abort(); };
  }, [search, searchOpen, searchRetry, marketRegion, token, activeAccountId]);

  function cancelQuoteRequest() {
    if (!quoteRequestRef.current) return;
    quoteRequestRef.current.abort();
    quoteRequestRef.current = null;
    setWorking(false);
  }

  async function chooseInstrument(instrument: Instrument) {
    if (!token) return;
    cancelQuoteRequest();
    const controller = new AbortController();
    quoteRequestRef.current = controller;
    const userId = sessionRef.current?.user.id;
    const isCurrent = () => quoteRequestRef.current === controller && !controller.signal.aborted
      && selectedRef.current === instrument && sessionRef.current?.user.id === userId;
    searchGeneration.current++;
    setSearchOpen(false);
    setSearchLoading(false);
    selectedRef.current = instrument;
    setSelected(instrument);
    setResults([]);
    setSearch(instrument.tradingSymbol);
    setQuote(null);
    setCandles([]);
    setYearCandles([]);
    setStockInsights(null);
    setChartRange(12);
    setFinancialPeriod("quarterly");
    setChartError("");
    setWorking(true);
    setError("");
    try {
      const nextQuote = await authorizedRequest<Quote>(
        `/api/v1/instruments/${instrument.marketRegion}/${instrument.exchange}/${encodeURIComponent(instrument.tradingSymbol)}/quote`,
        { signal: controller.signal },
      );
      if (!isCurrent()) return;
      setQuote(nextQuote);
      setLimitPrice(String(nextQuote.lastPrice));
      recordProductActivity("STOCK_OPENED");
    } catch (cause) {
      if (!isCurrent()) return;
      setQuote(null);
      setError(cause instanceof Error ? cause.message : "Quote unavailable");
    } finally {
      if (quoteRequestRef.current === controller) {
        quoteRequestRef.current = null;
        setWorking(false);
      }
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

  async function placeOrder(event: FormEvent) {
    event.preventDefault();
    if (!token || !selected || !quote || !activeAccount || !activeAccount.active || selected.marketRegion !== activeAccount.marketRegion) return;
    setWorking(true);
    setError("");
    setNotice("");
    try {
      const placedOrder = await authorizedRequest<PaperOrder>(
        `/api/v1/accounts/${activeAccount.id}/orders`,
        {
          method: "POST",
          headers: { "Idempotency-Key": crypto.randomUUID() },
         eturn;
    cancelQuoteRequest();
    const controller = new AbortController();
    quoteRequestRef.current = controller;
    const userId = sessionRef.current?.user.id;
    const isCurrent = () => quoteRequestRef.current === controller && !controller.signal.aborted
      && selectedRef.current === instrument && sessionRef.current?.user.id === userId;
    searchGeneration.current++;
    setSearchOpen(false);
    setSearchLoading(false);
    selectedRef.current = instrument;
    setSelected(instrument);
    setResults([]);
    setSearch(instrument.tradingSymbol);
    setQuote(null);
    setCandles([]);
    setYearCandles([]);
    setStockInsights(null);
    setChartRange(12);
    setFinancialPeriod("quarterly");
    setChartError("");
    setWorking(true);
    setError("");
    try {
      const nextQuote = await authorizedRequest<Quote>(
        `/api/v1/instruments/${instrument.marketRegion}/${instrument.exchange}/${encodeURIComponent(instrument.tradingSymbol)}/quote`,
        { signal: controller.signal },
      );
      if (!isCurrent()) return;
      setQuote(nextQuote);
      setLimitPrice(String(nextQuote.lastPrice));
      recordProductActivity("STOCK_OPENED");
    } catch (cause) {
      if (!isCurrent()) return;
      setQuote(null);
      setError(cause instanceof Error ? cause.message : "Quote unavailable");
    } finally {
      if (quoteRequestRef.current === controller) {
        quoteRequestRef.current = null;
        setWorking(false);
      }
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

  async function placeOrder(event: FormEvent) {
    event.preventDefault();
    if (!token || !selected || !quote || !activeAccount || !activeAccount.active || selected.marketRegion !== activeAccount.marketRegion) return;
    setWorking(true);
    setError("");
    setNotice("");
    try {
      const placedOrder = await authorizedRequest<PaperOrder>(
        `/api/v1/accounts/${activeAccount.id}/orders`,
        {
          method: "POST",
          headers: { "Idempotency-Key": crypto.randomUUID() },
          body: JSON.stringify({
            marketRegion,
            exchange: selected.exchange,
            symbol: selected.tradingSymbol,
            side,
            orderType,
            quantity: Number(quantity),
            limitPrice: orderType === "LIMIT" ? Number(limitPrice) : null,
          }),
        },
      );
      setTradeFeedback(placedOrder.finwizFeedback ?? null);
      const wasFirstOrder = standardAccount
        && Boolean(onboarding && !onboarding.firstOrderCompleted);
      const nextOnboarding = standardAccount
        ? await authorizedRequest<OnboardingState>("/api/v1/onboarding").catch(() => null)
        : null;
      if (nextOnboarding) setOnboarding(nextOnboarding);
      setNotice(placedOrder.finwizFeedback
        ? `${selected.tradingSymbol} executed. FinWiz portfolio feedback is ready below.`
        : wasFirstOrder && nextOnboarding?.firstOrderCompleted
        ? `First paper order submitted for ${selected.tradingSymbol}. Check its status below; the learning journey advances after execution.`
        : `${side === "BUY" ? "Buy" : "Sell"} order submitted for ${selected.tradingSymbol}.`);
      await loadDashboard(token, activeAccount);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Order could not be submitted");
    } finally {
      setWorking(false);
    }
  }

  async function cancelOrder(orderId: string) {
    if (!token || !activeAccount) return;
    setWorking(true);
    setError("");
    try {
      await authorizedRequest<PaperOrder>(
        `/api/v1/accounts/${activeAccount.id}/orders/${orderId}`,
        { method: "DELETE" },
      );
      setNotice("Order cancelled and blocked resources released.");
      await loadDashboard(token, activeAccount);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Order could not be cancelled");
    } finally {
      setWorking(false);
    }
  }

  function selectAccount(accountId: string, source: Account[] = accounts) {
    const account = source.find((candidate) => candidate.id === accountId);
    if (!account || account.id === activeAccountId) return;
    const marketChanged = account.marketRegion !== marketRegion;
    // The first account response can arrive after the learner has begun searching.
    // Keep that search when initializing the same market; actual switches invalidate it.
    if (activeAccountId || marketChanged) {
      searchGeneration.current++;
      setSearchOpen(false);
      setSearchLoading(false);
      setResults([]);
    }
    setActiveAccountId(account.id);
    setMarketRegion(account.marketRegion);
    window.sessionStorage.setItem("stoxsim-active-account", account.id);
    setPortfolio(null);
    setTradeFeedback(null);
    setOrders([]);
    setTrades([]);
    setError("");
    setNotice("");
    if (!marketChanged) return;
    cancelQuoteRequest();
    setSelected(null);
    selectedRef.current = null;
    setQuote(null);
    setCandles([]);
    setYearCandles([]);
    setStockInsights(null);
    setResults([]);
    setSearch("");
    setIndices([]);
    setMovers(null);
  }

  function switchMarket(region: MarketRegion) {
    const target = marketAccount(accounts, region);
    if (target) selectAccount(target.id);
  }

  async function logout() {
    if (session) {
      await rawRequest<void>(
        "/api/v1/auth/logout",
        { method: "POST" },
      ).catch(() => undefined);
    }
    persistSession(null);
    setAccounts([]);
    setActiveAccountId("");
    window.sessionStorage.removeItem("stoxsim-active-account");
    setPortfolio(null);
    setOrders([]);
    setTrades([]);
    setWatchlist(null);
    setIndices([]);
    setMovers(null);
    setOnboarding(null);
    setTradeFeedback(null);
  }

  async function completeOnboardingIntroduction() {
    try {
      const next = await authorizedRequest<OnboardingState>(
        "/api/v1/onboarding/introduction/complete",
        { method: "POST" },
      );
      setOnboarding(next);
      window.requestAnimationFrame(() => {
        document.getElementById("stock-search")?.scrollIntoView({ behavior: "smooth", block: "center" });
      });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Onboarding progress could not be saved");
    }
  }

  async function dismissOnboarding() {
    try {
      const next = await authorizedRequest<OnboardingState>(
        "/api/v1/onboarding/dismiss",
        { method: "POST" },
      );
      setOnboarding(next);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Onboarding preference could not be saved");
    }
  }

  async function resumeOnboarding() {
    try {
      const next = await authorizedRequest<OnboardingState>(
        "/api/v1/onboarding/resume",
        { method: "POST" },
      );
      setOnboarding(next);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Onboarding could not be resumed");
    }
  }

  if (!session) {
    return (
      <main className="welcomeShell" id="main-content" tabIndex={-1}>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{
            __html: JSON.stringify(HOME_FAQ_STRUCTURED_DATA).replace(/</g, "\\u003c"),
          }}
        />
        <nav className="welcomeNav">
          <Brand />
          <div className="welcomeNavLinks">
            <a href="/paper-trading">Paper trading</a>
            <a href="/stock-market-simulator-india">India simulator</a>
            <a href="/learn-stock-trading">Learn</a>
          </div>
        </nav>
        <section className="welcomeGrid">
          <div className="welcomeCopy">
            <span className="eyebrow">PAPER TRADING, BUILT LIKE THE REAL THING</span>
            <h1>Learn stock trading.<br /><span>Risk nothing.</span></h1>
            <p>Use a free stock-market simulator with ₹5,00,000 in India and $10,000 in the USA. Research stocks and ETFs, practise simulated orders, and learn from every virtual trade.</p>
            <div className="featureRow">
              <span>Live market structure</span><span>Simulated charges</span><span>Portfolio analytics</span>
            </div>
          </div>
          <form className="authCard" id="get-started" onSubmit={submitAuth}>
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
            {authMode === "register" && (
              <label className="legalConsent">
                <input
                  required
                  type="checkbox"
                  checked={authForm.termsAccepted}
                  onChange={(event) => setAuthForm({ ...authForm, termsAccepted: event.target.checked })}
                />
                <span>
                  I agree to the <a href="/terms" target="_blank" rel="noreferrer">Terms of Use</a> and
                  consent to the processing described in the <a href="/privacy" target="_blank" rel="noreferrer">Privacy Notice</a>.
                </span>
              </label>
            )}
            {authMode === "login" && <a className="authHelpLink" href="/forgot-password">Forgot password?</a>}
            <GoogleSignInLink register={authMode === "register"} />
            {error && <div className="message errorMessage">{error}</div>}
            <button className="primaryButton wide" disabled={working}>{working ? "Setting up…" : authMode === "register" ? "Start Now!" : "Open dashboard"}</button>
            <small>StoxSim is an educational simulator. No real orders or investment advice.</small>
          </form>
        </section>
        <section className="welcomeSeo" aria-labelledby="stoxsim-overview">
          <div className="welcomeSeoIntro">
            <span className="eyebrow">BUILT FOR BEGINNERS</span>
            <h2 id="stoxsim-overview">A free paper-trading simulator for India and the US</h2>
            <p>StoxSim helps new market learners understand the relationship between research, orders, holdings and portfolio performance before real money is involved.</p>
          </div>
          <div className="welcomeSeoGrid">
            <article>
              <strong>01</strong>
              <h3>Learn without financial risk</h3>
              <p>Create virtual Indian and US portfolios, try simulated trades and learn from mistakes without depositing money.</p>
              <a href="/paper-trading">Explore paper trading <span aria-hidden="true">→</span></a>
            </article>
            <article>
              <strong>02</strong>
              <h3>Practise Indian-market workflows</h3>
              <p>Follow Indian market sessions, work in rupees and review holdings in a dedicated ₹5 lakh practice portfolio.</p>
              <a href="/stock-market-simulator-india">Explore the India simulator <span aria-hidden="true">→</span></a>
            </article>
            <article>
              <strong>03</strong>
              <h3>Build a repeatable learning habit</h3>
              <p>Use watchlists, guided milestones and portfolio reviews to focus on process instead of chasing quick returns.</p>
              <a href="/learn-stock-trading">See the learning path <span aria-hidden="true">→</span></a>
            </article>
          </div>
        </section>
        <section className="welcomeFaq" aria-labelledby="stoxsim-faq">
          <div>
            <span className="eyebrow">COMMON QUESTIONS</span>
            <h2 id="stoxsim-faq">Understand StoxSim before you begin</h2>
          </div>
          <dl>
            {HOME_FAQ.map(({ question, answer }) => (
              <div key={question}>
                <dt>{question}</dt>
                <dd>{answer}</dd>
              </div>
            ))}
          </dl>
        </section>
        <footer className="legalFooter welcomeLegalFooter">
          <span>StoxSim · Educational paper trading</span>
          <nav aria-label="Legal">
            <a href="/paper-trading">Paper trading</a>
            <a href="/stock-market-simulator-india">India simulator</a>
            <a href="/learn-stock-trading">Learn</a>
            <a href="/terms">Terms</a>
            <a href="/privacy">Privacy</a>
            <a href="/cookies">Cookies</a>
            <a href="/disclaimer">Risk disclaimer</a>
            <a href="/status">Status</a>
          </nav>
        </footer>
      </main>
    );
  }

  return (
    <main className="appShell" id="main-content" tabIndex={-1}>
      <OnboardingJourney
        state={onboarding}
        onCompleteIntroduction={completeOnboardingIntroduction}
        onDismiss={dismissOnboarding}
      />
      <header className="appHeader">
        <Brand />
        <div className="marketSwitch" aria-label="Market selection">
          <button
            type="button"
            className={marketRegion === "INDIA" ? "active" : ""}
            aria-pressed={marketRegion === "INDIA"}
            onClick={() => switchMarket("INDIA")}
          ><span>🇮🇳</span> India</button>
          <button
            type="button"
            className={marketRegion === "UNITED_STATES" ? "active" : ""}
            aria-pressed={marketRegion === "UNITED_STATES"}
            onClick={() => switchMarket("UNITED_STATES")}
          ><span>🇺🇸</span> US</button>
        </div>
        <a className="labLink" href="/scenario-lab" target="_blank" rel="noopener">Scenario Lab ↗</a>
      </header>

      <section className={`marketBanner ${market?.phase === "REGULAR" ? "open" : "closed"}`}>
        <div><span className="pulse" /><div><strong>{marketDataStatus}</strong><small>{primaryExchange} · {market?.timezone ?? (marketRegion === "INDIA" ? "Asia/Kolkata" : "America/New_York")}</small></div></div>
        <div className="bannerStatusGroup"><div className={`streamBadge ${marketDataStatus.toLowerCase()}`}><i />{marketDataStatus}</div><div className="bannerRight"><span>Next transition</span><strong>{dateTime(market?.nextTransition)}</