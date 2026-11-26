"use client";
import { useEffect, useMemo, useState } from "react";
import { authenticatedRequest } from "../campus/client";
import { stockHref } from "../lib/stock-links";
import styles from "./portfolio.module.css";

type Trade = { id: string; exchange: string; symbol: string; name: string; side: "BUY" | "SELL"; quantity: number; price: number; grossValue: number; charges: number; netCashEffect: number; executedAt: string };
const PAGE_SIZE = 25;
export default function TransactionHistory({ accountId, currency, accountLabel }: {accountId: string; currency: string; accountLabel: string}) {
  const [trades,setTrades] = useState<Trade[]>([]);
  const [status,setStatus] = useState<"loading"|"ready"|"error">("loading");
  const [query,setQuery] = useState("");
  const [side,setSide] = useState("ALL");
  const [page,setPage] = useState(0);
  const [reload,setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    setStatus("loading"); setTrades([]); setPage(0);
    authenticatedRequest<Trade[]>(`accounts/${encodeURIComponent(accountId)}/trades`, {signal:controller.signal})
      .then(result => {
        if (!Array.isArray(result)) throw new Error("Unexpected trade history");
        if (active) {setTrades(result); setStatus("ready");}
      }).catch(() => { if (active && !controller.signal.aborted) setStatus("error"); });
    return () => {active=false; controller.abort();};
  },[accountId,reload]);
  const filtered = useMemo(() => {
    const q=query.trim().toLowerCase();
    return trades.filter(t => (side==="ALL" || t.side===side) && (!q || `${t.symbol} ${t.name} ${t.exchange}`.toLowerCase().includes(q)));
  },[trades,query,side]);
  const count=Math.max(1,Math.ceil(filtered.length/PAGE_SIZE));
  const money=(n:number)=>new Intl.NumberFormat(currency==="INR"?"en-IN":"en-US",{style:"currency",currency}).format(n);
  return <section className={styles.holdings} id="portfolio-transactions" aria-labelledby="transactions-title">
    <div className={styles.sectionHeader}><div><span>EXECUTED PRACTICE TRADES</span><h2 id="transactions-title">Transaction history</h2><small>{accountLabel} · {currency}</small></div><button className={styles.historyButton} type="button" onClick={()=>setReload(n=>n+1)} disabled={status==="loading"}>Refresh transactions</button></div>
    <div className={styles.transactionFilters}><label>Search transactions<input type="search" value={query} maxLength={100} placeholder="Symbol, company or exchange" onChange={e=>{setQuery(e.target.value);setPage(0);}}/></label>
    <label>Trade side<select value={side} onChange={e=>{setSide(e.target.value);setPage(0);}}><option value="ALL">All trades</option><option value="BUY">Buy</option><option value="SELL">Sell</option></select></label></div>
    {status==="loading" ? <p className={styles.historyMessage} role="status">Loading transactions…</p> : status==="error" ? <p className={styles.historyMessage} role="alert">Transaction history is unavailable. Use Refresh transactions to try again.</p> : <>
      <p className={styles.historyMessage} role="status">{filtered.length} of {trades.length} executed trades</p>
      {filtered.length ? <div className={styles.tableWrap} role="region" aria-label="Scrollable transaction history" tabIndex={0}><table>
        <thead><tr>{["Executed","Instrument","Side","Quantity","Price","Gross value","Simulated charges","Net cash effect"].map(h=><th scope="col" key={h}>{h}</th>)}</tr></thead>
        <tbody>{filtered.slice(page*PAGE_SIZE,(page+1)*PAGE_SIZE).map(t=><tr key={t.id}><td>{new Date(t.executedAt).toLocaleString("en-IN")}</td><td><a href={stockHref(t.exchange,t.symbol)}>{t.symbol}</a><small>{t.name} · {t.exchange}</small></td><td>{t.side==="BUY"?"Buy":"Sell"}</td><td>{t.quantity}</td><td>{money(t.price)}</td><td>{money(t.grossValue)}</td><td>{money(t.charges)}</td><td>{money(t.netCashEffect)}</td></tr>)}</tbody>
      </table></div> : <p className={styles.historyMessage}>{trades.length ? "No trades match these filters." : "No executed trades for this portfolio yet."}</p>}
      {count>1 && <div className={styles.transactionPagination}><button className={styles.historyButton} type="button" disabled={page===0} onClick={()=>setPage(n=>n-1)}>Previous</button><span>Page {page+1} of {count}</span><button className={styles.historyButton} type="button" disabled={page+1>=count} onClick={()=>setPage(n=>n+1)}>Next</button></div>}
      <p className={styles.historyMessage}>Only executed equity and ETF practice trades are shown. Derivatives use a separate wallet and history.</p>
    </>}
  </section>;
}
