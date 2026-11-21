"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { authenticatedRequest } from "../../campus/client";
import BasketWorkspace, { type BasketDraft } from "./BasketWorkspace";
import styles from "./derivatives.module.css";

type Kind = "FUT" | "CE" | "PE";
type Action = "OPEN_LONG" | "OPEN_SHORT" | "CLOSE";
interface Contract {
  id: string; providerKey?: string; underlyingKey?: string; symbol: string; kind: Kind; expiry: string;
  lotSize: number; tickSize: number; strike: number | null;
}
interface Market {
  contract: Contract; last: number | null; bid: number | null; ask: number | null;
  volume: number | null; lastTradeAt: string | null; fresh: boolean;
}
interface Position {
  market: Market; lots: number; direction: "LONG" | "SHORT"; averageEntry: number;
  reservedMargin: number; unrealizedPnl: number | null; awaitingSettlement: boolean;
}
interface Fill {
  basketId?: string | null;
  id: string; providerKey?: string; underlyingKey?: string; symbol: string; action: Action | "EXPIRY"; lots: number;
  price: number; realizedPnl: number; filledAt: string;
}
interface Portfolio {
  cash: number; reservedMargin: number; realizedPnl: number; totalEquity: number | null;
  positions: Position[]; fills: Fill[];
}
interface Ticket { market: Market; action: Action; limit: number; direction?: Position["direction"]; }

interface Preview {
  contract: Contract; action: Action; lots: number; quantity: number; price: number;
  requiredFunds: number; cashChange: number; cashAfter: number; realizedPnl: number;
  breakeven: number | null; maximumLoss: number | null; unlimitedLoss: boolean;
  quoteAt: string; generatedAt: string;
}

const indices = ["Nifty 50", "Nifty Bank"] as const;
const price = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });
const stamp = new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", dateStyle: "medium", timeStyle: "medium" });
const money = (value: number | null) => value == null ? "—" : price.format(value);
const errorMessage = (error: unknown) => error instanceof Error ? error.message : "Virtual derivatives are temporarily unavailable.";
const actions: Record<Action | "EXPIRY", string> = {
  OPEN_LONG: "Buy to open", OPEN_SHORT: "Sell to open", CLOSE: "Close position", EXPIRY: "Expiry settlement",
};

export default function DerivativesPage() {
  const [enabled, setEnabled] = useState<boolean>();
  const [index, setIndex] = useState<(typeof indices)[number]>("Nifty 50");
  const [expiries, setExpiries] = useState<string[]>([]);
  const [expiry, setExpiry] = useState("");
  const [offset, setOffset] = useState(0);
  const [revision, setRevision] = useState(0);
  const [chain, setChain] = useState<Market[]>([]);
  const [portfolio, setPortfolio] = useState<Portfolio>();
  const [basket, setBasket] = useState<BasketDraft[]>([]);
  const [ticket, setTicket] = useState<Ticket>();
  const [lots, setLots] = useState("1");
  const [preview, setPreview] = useState<Preview>();
  const [accepted, setAccepted] = useState(false);
  const [previewUntil, setPreviewUntil] = useState(0);
  const [now, setNow] = useState(0);
  const [advanced, setAdvanced] = useState(false);
  const [search, setSearch] = useState("");
  const [kind, setKind] = useState("ALL");
  const [working, setWorking] = useState(false);
  const [loading, setLoading] = useState(false);
  const [catalogError, setCatalogError] = useState("");
  const [portfolioError, setPortfolioError] = useState("");
  const [notice, setNotice] = useState("");
  const [orderError, setOrderError] = useState("");
  const request = useRef<{ signature: string; key: string } | undefined>(undefined);
  const submitting = useRef(false);
  const underlying = `NSE_INDEX|${index}`;

  useEffect(() => {
    let active = true;
    authenticatedRequest<{ enabled: boolean }>("derivatives/capability")
      .then(result => { if (active) setEnabled(result.enabled); })
      .catch(error => { if (active) { setEnabled(false); setCatalogError(errorMessage(error)); } });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    setExpiries([]); setExpiry(""); setChain([]); setOffset(0); setCatalogError("");
    authenticatedRequest<string[]>(`derivatives/expiries?underlying=${encodeURIComponent(underlying)}`, { signal: controller.signal })
      .then(dates => { if (!controller.signal.aborted) { setExpiries(dates); setExpiry(dates[0] ?? ""); } })
      .catch(error => { if (!controller.signal.aborted) setCatalogError(errorMessage(error)); });
    return () => controller.abort();
  }, [enabled, underlying]);

  useEffect(() => {
    if (!enabled || !expiry) return;
    const controller = new AbortController();
    setLoading(true); setCatalogError(""); setChain([]);
    const query = new URLSearchParams({ underlying, expiry, offset: String(offset) });
    authenticatedRequest<Market[]>(`derivatives/chain?${query}`, { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) setChain(value); })
      .catch(error => { if (!controller.signal.aborted) setCatalogError(errorMessage(error)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [enabled, underlying, expiry, offset, revision]);

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    setPortfolioError("");
    authenticatedRequest<Portfolio>("derivatives/portfolio", { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) setPortfolio(value); })
      .catch(error => { if (!controller.signal.aborted) setPortfolioError(errorMessage(error)); });
    return () => controller.abort();
  }, [enabled, revision]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const previewValid = !!preview && now < previewUntil;
  const visible = chain.filter(market =>
    (kind === "ALL" || market.contract.kind === kind) &&
    (market.contract.symbol + " " + (market.contract.strike ?? "")).toLowerCase().includes(search.toLowerCase().trim()));
  const strikes = new Map<number, { call?: Market; put?: Market }>();
  for (const market of visible) {
    const strike = market.contract.strike;
    if (strike === null) continue;
    const row = strikes.get(strike) ?? {};
    if (market.contract.kind === "CE") row.call = market;
    if (market.contract.kind === "PE") row.put = market;
    strikes.set(strike, row);
  }

  function start(market: Market, action: Action, position?: Position) {
    setTicket({ market, action, limit: position?.lots ?? 100, direction: position?.direction });
    setPreview(undefined); setAccepted(false);
    setLots("1"); setOrderError(""); setNotice("");
    window.requestAnimationFrame(() => document.getElementById("derivative-order")?.focus());
  }

  function addToBasket() {
    if (!ticket || ticket.action === "CLOSE" || working) return;
    const size = Number(lots);
    if (!Number.isInteger(size) || size < 1 || size > 100) { setOrderError("Choose valid lots first."); return; }
    if (basket.length >= 4 || basket.some(l => l.contract.id === ticket.market.contract.id)) {
      setOrderError("A basket supports up to four distinct contracts. Edit existing legs in the basket."); return;
    }
    const first = basket[0]?.contract, c = ticket.market.contract;
    if (first && (first.underlyingKey !== c.underlyingKey || first.expiry !== c.expiry)) {
      setOrderError("Use the same index and expiry as the basket, or clear it first."); return;
    }
    setBasket([...basket, { contract: c, action: ticket.action, lots: size }]);
    setTicket(undefined); setPreview(undefined);
  }

  async function review() {
    if (!ticket || submitting.current) return;
    const size = Number(lots);
    if (!Number.isInteger(size) || size < 1 || size > ticket.limit) {
      setOrderError("Choose a valid number of lots."); return;
    }
    submitting.current = true; setWorking(true); setPreview(undefined); setAccepted(false); setOrderError("");
    try {
      const result = await authenticatedRequest<Preview>("derivatives/preview", {
        method: "POST", body: JSON.stringify({ contractId: ticket.market.contract.id, action: ticket.action, lots: size }),
      });
      setPreview(result); setNow(Date.now()); setPreviewUntil(Date.now() + 30000);
    } catch (error) { setOrderError(errorMessage(error)); }
    finally { submitting.current = false; setWorking(false); }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!ticket || submitting.current) return;
    if (!preview || Date.now() >= previewUntil || !accepted ||
        preview.contract.id !== ticket.market.contract.id || preview.action !== ticket.action || preview.lots !== Number(lots)) {
      setOrderError("Review the current order and acknowledge the simulation rules first."); return;
    }
    const size = Number(lots);
    if (!Number.isInteger(size) || size < 1 || size > ticket.limit) {
      setOrderError("Choose a valid number of lots.");
      return;
    }
    const payload = { contractId: ticket.market.contract.id, action: ticket.action, lots: size };
    const signature = JSON.stringify(payload);
    if (request.current?.signature !== signature) request.current = { signature, key: crypto.randomUUID() };
    submitting.current = true; setWorking(true); setOrderError("");
    try {
      const fill = await authenticatedRequest<Fill>("derivatives/orders", {
        method: "POST", body: JSON.stringify({ ...payload, requestKey: request.current.key }),
      });
      setNotice(`${actions[fill.action]} filled for ${fill.lots} lot(s) of ${fill.symbol} at ${money(fill.price)}.`);
      setTicket(undefined); request.current = undefined;
      setRevision(value => value + 1);
    } catch (error) {
      setOrderError(errorMessage(error));
    } finally {
      submitting.current = false; setWorking(false);
    }
  }

  return <main id="main-content" tabIndex={-1} className={styles.shell}>
    <header className={styles.header}>
      <a href="/#stock-search">← Markets</a>
      <h1>F&amp;O practice workspace</h1>
      <p>Practise NSE index futures and buying calls and puts with a separate INR virtual wallet.</p>
    </header>
    {enabled === undefined && <p role="status">Checking virtual derivatives access…</p>}
    {enabled === false && <section className={styles.card}><h2>Availability</h2><p role="status">{catalogError || "Virtual derivatives are currently unavailable."}</p><p>{catalogError ? "Please retry when your connection is available." : "This server has not enabled virtual F&O trading. Contract selection, quotes and orders will appear here when it is enabled."}</p><a href="/markets/derivatives">Check again</a></section>}
    {enabled && <>
      <section className={styles.card} aria-label="Practice guide">
        <div className={styles.controls}>
          <button type="button" aria-pressed={!advanced} onClick={() => setAdvanced(false)}>Beginner view</button>
          <button type="button" aria-pressed={advanced} onClick={() => setAdvanced(true)}>Advanced view</button>
        </div>
        {!advanced && <p>Choose an index and expiry, select a contract, then review the risk before practising.
          A call gains expiry value above its strike; a put gains expiry value below it. Premium is the price per unit;
          one lot contains the contract’s stated number of units. Futures expose you to the full contract value.</p>}
        <p>Contract prices and option premiums are not the index spot price. Spot, Greeks and open interest are unavailable in this workspace.</p>
      </section>
      <section className={styles.card} aria-labelledby="derivative-market-heading">
        <h2 id="derivative-market-heading">NSE index contracts</h2>
        <div className={styles.controls}>
          <label>Underlying <select value={index} disabled={working} onChange={event => { setIndex(event.target.value as typeof index); setTicket(undefined); setPreview(undefined); }}>
            {indices.map(value => <option key={value}>{value}</option>)}
          </select></label>
          <label>Expiry <select value={expiry} disabled={!expiries.length || working} onChange={event => {
            setExpiry(event.target.value); setOffset(0); setTicket(undefined); setPreview(undefined);
          }}>
            {!expiries.length && <option value="">No expiry available</option>}
            {expiries.map(date => <option key={date}>{date}</option>)}
          </select></label>
          <button type="button" disabled={loading || working} onClick={() => setRevision(value => value + 1)}>Refresh quotes</button>
        </div>
        <div className={styles.controls}>
          <label>Find in loaded contracts <input className={styles.search} type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Symbol or strike" /></label>
          <label>Contract type <select value={kind} onChange={event => setKind(event.target.value)}>
            <option value="ALL">All contracts</option><option value="FUT">Futures</option><option value="CE">Calls</option><option value="PE">Puts</option>
          </select></label>
        </div>
        <p>Filters apply to this page of strikes. Use More strikes to browse further.</p>
        {!loading && chain.length > 0 && visible.length === 0 && <p role="status">No loaded contracts match your filters.</p>}
        {catalogError && <p role="alert">{catalogError}</p>}
        {loading && <p role="status">Loading the contract chain…</p>}
        {!loading && !chain.length && !catalogError && <p role="status">No contracts are available for this selection.</p>}
        {visible.filter(market => market.contract.kind === "FUT").map(market => <div key={market.contract.id} className={styles.future}>
          <div><strong>{market.contract.symbol}</strong><MarketQuote market={market} /></div>
          <div className={styles.controls}>
            <button type="button" disabled={!market.fresh || working} onClick={() => start(market, "OPEN_LONG")}>Open long</button>
            <button type="button" disabled={!market.fresh || working} onClick={() => start(market, "OPEN_SHORT")}>Open short</button>
          </div>
        </div>)}
        {strikes.size > 0 && <div className={styles.table}>
          <table aria-label="Index option chain">
            <thead><tr><th scope="col">Call</th><th scope="col">Strike</th><th scope="col">Put</th></tr></thead>
            <tbody>{[...strikes].map(([strike, row]) => <tr key={strike}>
              <td>{row.call && <><MarketQuote market={row.call} />{advanced && <small>{row.call.contract.symbol} · {row.call.contract.providerKey ?? row.call.contract.id}</small>}<button type="button" disabled={!row.call.fresh || working} onClick={() => row.call && start(row.call, "OPEN_LONG")} aria-label={`Buy ${strike} call`}>Buy call</button></>}</td>
              <th scope="row">{money(strike)}</th>
              <td>{row.put && <><MarketQuote market={row.put} />{advanced && <small>{row.put.contract.symbol} · {row.put.contract.providerKey ?? row.put.contract.id}</small>}<button type="button" disabled={!row.put.fresh || working} onClick={() => row.put && start(row.put, "OPEN_LONG")} aria-label={`Buy ${strike} put`}>Buy put</button></>}</td>
            </tr>)}</tbody>
          </table>
        </div>}
        {(offset > 0 || new Set(chain.filter(m => m.contract.strike != null).map(m => m.contract.strike)).size >= 25) && <div className={styles.controls}>
          <button type="button" disabled={!offset || loading || working} onClick={() => setOffset(value => value - 25)}>Previous strikes</button>
          <button type="button" disabled={new Set(chain.filter(m => m.contract.strike != null).map(m => m.contract.strike)).size < 25 || offset >= 1000 || loading || working} onClick={() => setOffset(value => value + 25)}>More strikes</button>
        </div>}
        <p>Source: Upstox · Times in IST · A fresh provider quote is required for a virtual fill.</p>
      </section>

      {ticket && <form className={styles.card} onSubmit={submit} id="derivative-order" tabIndex={-1}>
        <h2>{actions[ticket.action]}</h2>
        <p>{ticket.market.contract.symbol} · NSE · {ticket.market.contract.expiry} · {ticket.market.contract.lotSize} units per lot</p>
        <div className={styles.controls}>
          <label>Lots <input required type="number" min={1} max={ticket.limit} step={1} value={lots}
            disabled={working} onChange={event => { setLots(event.target.value); setPreview(undefined); setAccepted(false); }} /></label>
          {ticket.action !== "CLOSE" && <button type="button" disabled={working} onClick={addToBasket}>Add to strategy basket</button>}
          <button type="button" disabled={working} onClick={review}>Review risk and funds</button>
          <button type="submit" disabled={working || !previewValid || !accepted}>{working ? "Submitting…" : "Place virtual order"}</button>
          <button type="button" disabled={working} onClick={() => { setTicket(undefined); setOrderError(""); }}>Cancel</button>
        </div>
        {preview && <section className={styles.preview} aria-label="Order risk review">
          <h3>Review this virtual order</h3>
          <p>{preview.contract.symbol} · NSE · {preview.contract.kind === "FUT" ? "Future" : preview.contract.kind === "CE" ? "Call option" : "Put option"}
            · Expiry {preview.contract.expiry}</p>
          <p>Underlying: {preview.contract.underlyingKey?.replace("NSE_INDEX|", "") ?? index}<br />
            Exact contract: <span className={styles.identifier}>{preview.contract.providerKey ?? preview.contract.id}</span></p>
          <dl className={styles.totals}>
            <div><dt>Quantity</dt><dd>{preview.lots} lots × {preview.contract.lotSize} = {preview.quantity} units</dd></div>
            {preview.contract.strike != null && <div><dt>Strike</dt><dd>{money(preview.contract.strike)}</dd></div>}
            <div><dt>Estimated {preview.contract.kind === "FUT" ? "contract price" : "premium per unit"}</dt><dd>{money(preview.price)}</dd></div>
            <div><dt>{preview.action === "CLOSE" ? "Required upfront funds" : preview.contract.kind === "FUT" ? "Required virtual margin (20%)" : "Required virtual premium"}</dt><dd>{money(preview.requiredFunds)}</dd></div>
            <div><dt>Estimated cash change</dt><dd>{money(preview.cashChange)}</dd></div>
            <div><dt>Available cash after fill</dt><dd>{money(preview.cashAfter)}</dd></div>
            {preview.action === "CLOSE" ? <div><dt>Estimated realized P&amp;L</dt><dd>{money(preview.realizedPnl)}</dd></div> : <>
              <div><dt>Expiry breakeven for these lots</dt><dd>{preview.breakeven == null ? "Not reachable at a nonnegative index level" : money(preview.breakeven)}</dd></div>
              <div><dt>Maximum loss for these lots</dt><dd>{preview.unlimitedLoss ? "Unbounded" : money(preview.maximumLoss)}</dd></div>
            </>}
          </dl>
          <p>Exchange quote: {stamp.format(new Date(preview.quoteAt))}. Estimates use ask for buys, bid for sells,
            or last trade if that side is unavailable, rounded to the contract tick. This does not reserve a price:
            the server checks a fresh quote, session, positions and funds again at execution. The fill can differ.</p>
          <p>Simulated fees: ₹0. Brokerage, taxes and additional slippage are not modelled; this is not a real-world cost estimate.
            Expiry calculations assume a nonnegative index and holding these lots to expiry, without costs.</p>
          {preview.contract.kind === "FUT" && <p className={styles.warning}>Futures losses can exceed the 20% margin and leave negative virtual cash.
            Short futures have unbounded loss; long futures can lose the full contract value if the index reaches zero.
            There is no daily cash mark-to-market or forced liquidation.</p>}
          <p>Expiry uses the verified underlying index close for cash settlement. Missing settlement data leaves the position pending.
            No physical delivery or early exercise is simulated. Option writing is unsupported.</p>
          <label className={styles.acknowledge}><input type="checkbox" checked={accepted} disabled={working || !previewValid}
            onChange={event => setAccepted(event.target.checked)} /> I understand the risk and simplified simulation rules.</label>
          {!previewValid && <p role="status">This review has expired. Review risk and funds again before submitting.</p>}
        </section>}
        {orderError && <p role="alert">{orderError}</p>}
      </form>}
      <BasketWorkspace legs={basket} onChange={setBasket} disabled={working} onBusy={setWorking}
        onFilled={message => { setNotice(message); setTicket(undefined); setPreview(undefined); setRevision(value => value + 1); }} />
      {notice && <p role="status">{notice}</p>}

      <section className={styles.card} aria-labelledby="derivative-positions-heading">
        <h2 id="derivative-positions-heading">Virtual positions and wallet</h2>
        {portfolioError && <p role="alert">{portfolioError}</p>}
        {portfolio && <>
          <dl className={styles.totals}>
            <div><dt>Available cash</dt><dd>{money(portfolio.cash)}</dd></div>
            <div><dt>Reserved margin</dt><dd>{money(portfolio.reservedMargin)}</dd></div>
            <div><dt>Realized P&amp;L</dt><dd>{money(portfolio.realizedPnl)}</dd></div>
            <div><dt>Marked equity</dt><dd>{money(portfolio.totalEquity)}</dd></div>
          </dl>
          {portfolio.totalEquity == null && <p>Marked equity requires fresh prices for every position.</p>}
          {portfolio.positions.length === 0 && <p>No open derivative positions.</p>}
          {portfolio.positions.map(position => <div key={position.market.contract.id} className={styles.future}>
            <div><strong>{position.market.contract.symbol}</strong><small>{position.direction} · {position.lots} lots · Entry {money(position.averageEntry)} · Unrealized P&amp;L {money(position.unrealizedPnl)}</small>
              {position.awaitingSettlement && <small>Awaiting verified expiry settlement.</small>}
            </div>
            <button type="button" disabled={!position.market.fresh || position.awaitingSettlement || working}
              onClick={() => start(position.market, "CLOSE", position)}>Close lots</button>
          </div>)}
          {portfolio.fills.length > 0 && <><h3>Recent virtual fills</h3><ul className={styles.fills}>
            {portfolio.fills.map(fill => <li key={fill.id}>{fill.basketId && <strong>Basket {fill.basketId.slice(0, 8)} · </strong>}{actions[fill.action]} · {fill.symbol} · {fill.lots} lots · {money(fill.price)} · P&amp;L {money(fill.realizedPnl)}</li>)}
          </ul></>}
        </>}
        <p>Educational simulator. Futures use a flat margin model and can lose more than reserved margin.</p>
      </section>
    </>}
  </main>;
}

function MarketQuote({ market }: { market: Market }) {
  return <div className={styles.quote}>
    <strong>{money(market.last)}</strong>
    <small>Lot {market.contract.lotSize} · Bid {money(market.bid)} · Ask {money(market.ask)}</small>
    <small>{market.lastTradeAt ? stamp.format(new Date(market.lastTradeAt)) : "Quote unavailable"}{market.lastTradeAt && !market.fresh ? " · Stale" : ""}</small>
    {market.volume != null && <small>Volume {market.volume.toLocaleString("en-IN")}</small>}
  </div>;
}
