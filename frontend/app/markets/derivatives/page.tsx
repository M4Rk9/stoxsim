"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { authenticatedRequest } from "../../campus/client";
import styles from "./derivatives.module.css";

type Kind = "FUT" | "CE" | "PE";
type Action = "OPEN_LONG" | "OPEN_SHORT" | "CLOSE";
interface Contract {
  id: string; symbol: string; kind: Kind; expiry: string;
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
  id: string; symbol: string; action: Action | "EXPIRY"; lots: number;
  price: number; realizedPnl: number; filledAt: string;
}
interface Portfolio {
  cash: number; reservedMargin: number; realizedPnl: number; totalEquity: number | null;
  positions: Position[]; fills: Fill[];
}
interface Ticket { market: Market; action: Action; limit: number; direction?: Position["direction"]; }

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
  const [ticket, setTicket] = useState<Ticket>();
  const [lots, setLots] = useState("1");
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

  const strikes = new Map<number, { call?: Market; put?: Market }>();
  for (const market of chain) {
    const strike = market.contract.strike;
    if (strike === null) continue;
    const row = strikes.get(strike) ?? {};
    if (market.contract.kind === "CE") row.call = market;
    if (market.contract.kind === "PE") row.put = market;
    strikes.set(strike, row);
  }

  function start(market: Market, action: Action, position?: Position) {
    setTicket({ market, action, limit: position?.lots ?? 100, direction: position?.direction });
    setLots("1"); setOrderError(""); setNotice("");
    window.requestAnimationFrame(() => document.getElementById("derivative-order")?.focus());
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!ticket || submitting.current) return;
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

  const estimatePrice = ticket && (ticket.action === "OPEN_LONG" || ticket.direction === "SHORT"
    ? ticket.market.ask ?? ticket.market.last : ticket.market.bid ?? ticket.market.last);
  const estimate = ticket && estimatePrice != null ? estimatePrice * ticket.market.contract.lotSize * Number(lots) : null;

  return <main id="main-content" tabIndex={-1} className={styles.shell}>
    <header className={styles.header}>
      <a href="/#stock-search">← Markets</a>
      <h1>Futures &amp; Options</h1>
      <p>Practise NSE index futures and buying calls and puts with a separate INR virtual wallet.</p>
    </header>
    {enabled === undefined && <p role="status">Checking virtual derivatives access…</p>}
    {enabled === false && <p role="status">{catalogError || "Virtual derivatives are currently unavailable."}</p>}
    {enabled && <>
      <section className={styles.card} aria-labelledby="derivative-market-heading">
        <h2 id="derivative-market-heading">NSE index contracts</h2>
        <div className={styles.controls}>
          <label>Underlying <select value={index} onChange={event => setIndex(event.target.value as typeof index)}>
            {indices.map(value => <option key={value}>{value}</option>)}
          </select></label>
          <label>Expiry <select value={expiry} disabled={!expiries.length} onChange={event => {
            setExpiry(event.target.value); setOffset(0);
          }}>
            {!expiries.length && <option value="">No expiry available</option>}
            {expiries.map(date => <option key={date}>{date}</option>)}
          </select></label>
          <button type="button" disabled={loading || working} onClick={() => setRevision(value => value + 1)}>Refresh quotes</button>
        </div>
        {catalogError && <p role="alert">{catalogError}</p>}
        {loading && <p role="status">Loading the contract chain…</p>}
        {!loading && !chain.length && !catalogError && <p role="status">No contracts are available for this selection.</p>}
        {chain.filter(market => market.contract.kind === "FUT").map(market => <div key={market.contract.id} className={styles.future}>
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
              <td>{row.call && <><MarketQuote market={row.call} /><button type="button" disabled={!row.call.fresh || working} onClick={() => row.call && start(row.call, "OPEN_LONG")} aria-label={`Buy ${strike} call`}>Buy call</button></>}</td>
              <th scope="row">{money(strike)}</th>
              <td>{row.put && <><MarketQuote market={row.put} /><button type="button" disabled={!row.put.fresh || working} onClick={() => row.put && start(row.put, "OPEN_LONG")} aria-label={`Buy ${strike} put`}>Buy put</button></>}</td>
            </tr>)}</tbody>
          </table>
        </div>}
        {(offset > 0 || strikes.size >= 25) && <div className={styles.controls}>
          <button type="button" disabled={!offset || loading} onClick={() => setOffset(value => value - 25)}>Previous strikes</button>
          <button type="button" disabled={strikes.size < 25 || offset >= 1000 || loading} onClick={() => setOffset(value => value + 25)}>More strikes</button>
        </div>}
        <p>Source: Upstox · Times in IST · A fresh provider quote is required for a virtual fill.</p>
      </section>

      {ticket && <form className={styles.card} onSubmit={submit} id="derivative-order" tabIndex={-1}>
        <h2>{actions[ticket.action]}</h2>
        <p>{ticket.market.contract.symbol} · NSE · {ticket.market.contract.expiry} · {ticket.market.contract.lotSize} units per lot</p>
        <div className={styles.controls}>
          <label>Lots <input required type="number" min={1} max={ticket.limit} step={1} value={lots}
            disabled={working} onChange={event => setLots(event.target.value)} /></label>
          <button type="submit" disabled={working}>{working ? "Submitting…" : "Place virtual order"}</button>
          <button type="button" disabled={working} onClick={() => { setTicket(undefined); setOrderError(""); }}>Cancel</button>
        </div>
        {estimate != null && Number.isFinite(estimate) && Number(lots) > 0 && ticket.action !== "CLOSE" && <p>
          {ticket.market.contract.kind === "FUT" ? `Estimated 20% simulated margin ${money(estimate * .2)}` : `Estimated premium ${money(estimate)}`}.
          The server rechecks price and funds before filling.
        </p>}
        {orderError && <p role="alert">{orderError}</p>}
      </form>}
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
            {portfolio.fills.map(fill => <li key={fill.id}>{actions[fill.action]} · {fill.symbol} · {fill.lots} lots · {money(fill.price)} · P&amp;L {money(fill.realizedPnl)}</li>)}
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
