"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

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

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
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
    throw new Error(payload?.message ?? `Request failed with status ${response.status}`);
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
  const [authMode, setAuthMode] = useState<"login" | "register">("register");
  const [authForm, setAuthForm] = useState({ displayName: "", email: "", password: "" });
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [market, setMarket] = useState<MarketStatus | null>(null);
  const [orders, setOrders] = useState<PaperOrder[]>([]);
  const [trades, setTrades] = useState<Trade[]>([]);
  const [search, setSearch] = useState("");
  const [results, setResults] = useState<Instrument[]>([]);
  const [selected, setSelected] = useState<Instrument | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [side, setSide] = useState<OrderSide>("BUY");
  const [orderType, setOrderType] = useState<OrderType>("MARKET");
  const [quantity, setQuantity] = useState("1");
  const [limitPrice, setLimitPrice] = useState("");
  const [chargeEstimate, setChargeEstimate] = useState<ChargeBreakdown | null>(null);
  const [loading, setLoading] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const token = session?.accessToken;
  const openOrders = useMemo(() => orders.filter((order) => order.status === "OPEN"), [orders]);

  useEffect(() => {
    const saved = window.localStorage.getItem("stoxsim-session");
    if (!saved) return;
    try {
      const restored = JSON.parse(saved) as AuthResponse;
      setSession(restored);
      void loadDashboard(restored.accessToken);
    } catch {
      window.localStorage.removeItem("stoxsim-session");
    }
  }, []);

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
      request<ChargeBreakdown>(
        `/api/v1/trading/charges/estimate?side=${side}&exchange=NSE&turnover=${turnover}`,
        {},
        token,
      ).then(setChargeEstimate).catch(() => setChargeEstimate(null));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [token, quote, quantity, limitPrice, orderType, side]);

  async function loadDashboard(accessToken: string) {
    setLoading(true);
    setError("");
    try {
      const [nextPortfolio, nextMarket, nextOrders, nextTrades] = await Promise.all([
        request<Portfolio>("/api/v1/portfolio?marketRegion=INDIA", {}, accessToken),
        request<MarketStatus>("/api/v1/market/status?exchange=NSE", {}, accessToken),
        request<PaperOrder[]>("/api/v1/orders?marketRegion=INDIA", {}, accessToken),
        request<Trade[]>("/api/v1/trades?marketRegion=INDIA", {}, accessToken),
      ]);
      setPortfolio(nextPortfolio);
      setMarket(nextMarket);
      setOrders(nextOrders);
      setTrades(nextTrades);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load your dashboard");
    } finally {
      setLoading(false);
    }
  }

  async function submitAuth(event: FormEvent) {
    event.preventDefault();
    setWorking(true);
    setError("");
    try {
      const path = authMode === "register" ? "/api/v1/auth/register" : "/api/v1/auth/login";
      const body = authMode === "register" ? authForm : { email: authForm.email, password: authForm.password };
      const authenticated = await request<AuthResponse>(path, {
        method: "POST",
        body: JSON.stringify(body),
      });
      setSession(authenticated);
      window.localStorage.setItem("stoxsim-session", JSON.stringify(authenticated));
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
      const found = await request<Instrument[]>(
        `/api/v1/instruments/search?marketRegion=INDIA&q=${encodeURIComponent(search.trim())}`,
        {},
        token,
      );
      setResults(found.filter((item) => item.exchange === "NSE").slice(0, 8));
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
      const nextQuote = await request<Quote>(
        `/api/v1/instruments/INDIA/${instrument.exchange}/${instrument.tradingSymbol}/quote`,
        {},
        token,
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

  async function placeOrder(event: FormEvent) {
    event.preventDefault();
    if (!token || !selected) return;
    setWorking(true);
    setError("");
    setNotice("");
    try {
      await request<PaperOrder>(
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
        token,
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
      await request<PaperOrder>(`/api/v1/orders/${orderId}`, { method: "DELETE" }, token);
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
      await request<void>(
        "/api/v1/auth/logout",
        { method: "POST", body: JSON.stringify({ refreshToken: session.refreshToken }) },
      ).catch(() => undefined);
    }
    window.localStorage.removeItem("stoxsim-session");
    setSession(null);
    setPortfolio(null);
    setOrders([]);
    setTrades([]);
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
        <div className="bannerRight"><span>Next transition</span><strong>{dateTime(market?.nextTransition)}</strong></div>
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
                <div className="quoteTop"><div><span className="symbolIcon">{selected.tradingSymbol.slice(0, 2)}</span><div><h3>{selected.tradingSymbol}</h3><p>{selected.name}</p></div></div><span className={`quoteStatus ${quote.dataStatus.toLowerCase()}`}>{quote.dataStatus}</span></div>
                <div className="quotePrice"><strong>{inr(quote.lastPrice)}</strong><span>{dateTime(quote.exchangeTimestamp)}</span></div>
                <div className="quoteStats"><div><span>Open</span><strong>{inr(quote.open)}</strong></div><div><span>High</span><strong>{inr(quote.high)}</strong></div><div><span>Low</span><strong>{inr(quote.low)}</strong></div><div><span>Prev. close</span><strong>{inr(quote.previousClose)}</strong></div></div>
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
