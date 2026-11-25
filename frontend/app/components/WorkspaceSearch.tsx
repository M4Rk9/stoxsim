"use client";
import { useEffect, useRef, useState } from "react";
import { authenticatedRequest } from "../campus/client";
import { stockHref } from "../lib/stock-links";
import styles from "./WorkspaceSearch.module.css";

type Region = "INDIA" | "UNITED_STATES";
type Instrument = { id: string; tradingSymbol: string; name: string; exchange: string; marketRegion: Region; instrumentType: string };
const exchanges: Record<Region, string[]> = { INDIA: ["NSE", "BSE"], UNITED_STATES: ["NASDAQ", "NYSE", "NYSE_ARCA", "AMEX", "CBOE"] };

export default function WorkspaceSearch() {
  const dialog = useRef<HTMLDialogElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [region, setRegion] = useState<Region>("INDIA");
  const [results, setResults] = useState<Instrument[]>([]);
  const [status, setStatus] = useState<"idle" | "loading" | "ready" | "error">("idle");

  useEffect(() => {
    if (!open || query.trim().length < 2) return;
    const controller = new AbortController();
    let active = true;
    const timer = window.setTimeout(() => {
      const params = new URLSearchParams({ marketRegion: region, q: query.trim() });
      void authenticatedRequest<Instrument[]>(`instruments/search?${params}`, { signal: controller.signal }).then(items => {
        if (!active) return;
        if (!Array.isArray(items)) throw new Error("Unexpected search response");
        setResults(items.filter(item => item.marketRegion === region && ["EQUITY", "ETF"].includes(item.instrumentType) && exchanges[region].includes(item.exchange)).sort((a,b) => Number(b.exchange === "NSE") - Number(a.exchange === "NSE")).slice(0,12));
        setStatus("ready");
      }).catch(() => { if (active && !controller.signal.aborted) setStatus("error"); });
    }, 300);
    return () => { active = false; window.clearTimeout(timer); controller.abort(); };
  }, [open, query, region]);

  function reset(nextQuery: string) {
    setResults([]);
    setStatus(nextQuery.trim().length >= 2 ? "loading" : "idle");
  }
  function show() {
    setQuery(""); reset(""); setOpen(true);
    dialog.current?.showModal(); input.current?.focus();
  }
  function close() { dialog.current?.close(); setOpen(false); reset(""); }

  return <>
    <button type="button" className={styles.trigger} aria-label="Search instruments across StoxSim" onClick={show}>
      <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></svg><span>Search instruments</span>
    </button>
    <dialog ref={dialog} className={styles.dialog} aria-labelledby="workspace-search-title" onKeyDownCapture={event => { if (event.key === "Escape") { event.preventDefault(); close(); } }} onCancel={event => { event.preventDefault(); close(); }} onClose={() => { setOpen(false); reset(""); }}>
      <div className={styles.heading}><h2 id="workspace-search-title">Search instruments</h2><button type="button" onClick={close} aria-label="Close instrument search">×</button></div>
      <div className={styles.regions} role="group" aria-label="Search market">
        {(["INDIA", "UNITED_STATES"] as const).map(value => <button key={value} type="button" aria-pressed={region === value} onClick={() => { if (region !== value) { reset(query); setRegion(value); } }}>{value === "INDIA" ? "India" : "US"}</button>)}
      </div>
      <label className={styles.label}>Stock or ETF<input ref={input} type="search" value={query} maxLength={100} placeholder="Search by symbol or company" onChange={event => { setQuery(event.target.value); reset(event.target.value); }} /></label>
      <p className={styles.status} role="status">{status === "idle" ? "Enter at least two characters." : status === "loading" ? "Searching…" : status === "error" ? "Search unavailable. Please try again or use Markets." : results.length ? `${results.length} matching instruments` : "No matching instruments."}</p>
      <ul className={styles.results}>{results.map(item => <li key={item.id}><a href={stockHref(item.exchange, item.tradingSymbol)} onClick={close}><strong>{item.tradingSymbol}</strong><span>{item.name}</span><small>{item.exchange} · {item.instrumentType}</small></a></li>)}</ul>
    </dialog>
  </>;
}
