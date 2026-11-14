"use client";

import { useMemo, useState, type PointerEvent } from "react";
import { candleDay, orderedCandles, type PriceCandle } from "../lib/candle-patterns";
import styles from "./PriceHistoryChart.module.css";

const width = 780, height = 250, left = 26, right = 26, top = 17, bottom = 27;

export default function PriceHistoryChart({ candles, loading, error, currency, exchange, provider, interval = "Daily" }: {
  candles: PriceCandle[];
  loading: boolean;
  error: string;
  currency: "INR" | "USD";
  exchange: string;
  provider: string;
  interval?: string;
}) {
  const [mode, setMode] = useState<"line" | "candles">("line");
  const [selectedTimestamp, setSelectedTimestamp] = useState<string>();
  const bars = useMemo(() => orderedCandles(candles), [candles]);
  const zone = currency === "INR" ? "Asia/Kolkata" : "America/New_York";
  const today = candleDay(new Date(), zone);


  if (loading) return <div className={styles.empty} role="status">Loading historical prices…</div>;
  if (!bars.length) return <div className={styles.empty} role="status">{error || "Price history is unavailable for this selection."}</div>;

  const first = bars[0], last = bars[bars.length - 1];
  const index = bars.findIndex(bar => bar.timestamp === selectedTimestamp);
  const activeIndex = index < 0 ? bars.length - 1 : index;
  const active = bars[activeIndex];
  const min = Math.min(...bars.map(bar => mode === "line" ? bar.close : bar.low));
  const max = Math.max(...bars.map(bar => mode === "line" ? bar.close : bar.high));
  const pad = Math.max((max - min) * .06, max * .001);
  const y = (price: number) => top + (max + pad - price) * (height - top - bottom) / (max - min + 2 * pad);
  const duration = Date.parse(last.timestamp) - Date.parse(first.timestamp);
  const positions = new Map(bars.map((bar, index) => [bar.timestamp, index]));
  const x = (bar: PriceCandle) => bars.length === 1 ? width / 2
    : left + (positions.get(bar.timestamp) ?? 0) * (width - left - right) / (bars.length - 1);
  const line = bars.map((bar, i) => `${i === 0 ? "M" : "L"}${x(bar)},${y(bar.close)}`).join(" ");
  const candleWidth = Math.max(.7, Math.min(13, (width - left - right) * .65 / bars.length));
  const money = new Intl.NumberFormat(currency === "INR" ? "en-IN" : "en-US", { style: "currency", currency });
  const day = new Intl.DateTimeFormat("en-IN", { timeZone: zone, day: "numeric", month: "short", year: "numeric" });
  const time = new Intl.DateTimeFormat("en-IN", { timeZone: zone, dateStyle: "medium", timeStyle: "short" });

  function inspect(event: PointerEvent<SVGSVGElement>) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const target = (event.clientX - bounds.left) * width / bounds.width;
    const nearest = bars.reduce((best, bar) => Math.abs(x(bar) - target) < Math.abs(x(best) - target) ? bar : best);
    setSelectedTimestamp(nearest.timestamp);
  }

  return <div className={styles.chart}>
    <div className={styles.controls} aria-label="Chart display">
      <button type="button" aria-pressed={mode === "line"} onClick={() => setMode("line")}>Line</button>
      <button type="button" aria-pressed={mode === "candles"} onClick={() => setMode("candles")}>Candlestick</button>
    </div>
    <p className={styles.source}>Prices through {day.format(new Date(last.timestamp))}
      {candleDay(last.timestamp, zone) >= today ? " · Today's session may still be incomplete" : " · Last available session"}</p>
    <details className={styles.source}><summary>About this market data</summary>
      <p>{exchange} · {provider} · {interval} open, high, low and close. Latest observation: {time.format(new Date(last.timestamp))} ({zone}). This chart is historical data, not a live quote.</p>
      <p>Available observations are evenly spaced. Closed sessions and missing prices are not filled in.</p>
    </details>
    <div className={styles.readout} aria-label="Selected candle">
      <time dateTime={active.timestamp}>{time.format(new Date(active.timestamp))}</time>
      <span>Open <strong>{money.format(active.open)}</strong></span>
      <span>High <strong>{money.format(active.high)}</strong></span>
      <span>Low <strong>{money.format(active.low)}</strong></span>
      <span>Close <strong>{money.format(active.close)}</strong></span>
      {typeof active.volume === "number" && Number.isFinite(active.volume) && active.volume >= 0
        && <span>Volume <strong>{active.volume.toLocaleString("en-IN")}</strong></span>}
    </div>
    <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" role="img"
      aria-label={mode === "line" ? "Historical closing-price chart" : `${interval} candlestick chart`} tabIndex={0}
      aria-description="Use the left and right arrow keys to inspect prices."
      onPointerMove={event => { if (event.pointerType === "mouse") inspect(event); }} onPointerDown={inspect}
      onKeyDown={event => {
        if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
        event.preventDefault();
        const next = event.key === "Home" ? 0 : event.key === "End" ? bars.length - 1
          : Math.min(bars.length - 1, Math.max(0, activeIndex + (event.key === "ArrowLeft" ? -1 : 1)));
        setSelectedTimestamp(bars[next].timestamp);
      }}>
      {[.25, .5, .75].map(ratio => {
        const lineY = top + ratio * (height - top - bottom);
        return <line key={ratio} x1={left} x2={width - right} y1={lineY} y2={lineY} className={styles.grid} />;
      })}
      {mode === "line" ? <path d={line} className={styles.line} /> : bars.map(bar => <g key={bar.timestamp} className={bar.close >= bar.open ? styles.up : styles.down}>
        <line x1={x(bar)} x2={x(bar)} y1={y(bar.high)} y2={y(bar.low)} />
        <rect x={x(bar) - candleWidth / 2} y={Math.min(y(bar.open), y(bar.close))} width={candleWidth} height={Math.max(1, Math.abs(y(bar.open) - y(bar.close)))} />
      </g>)}
      <line x1={x(active)} x2={x(active)} y1={top} y2={height - bottom} className={styles.crosshair} />
      <circle cx={x(active)} cy={y(active.close)} r="4" className={styles.point} />

    </svg>
    <div className={styles.axisDates}><span>{day.format(new Date(first.timestamp))}</span>{duration > 0 && <span>{day.format(new Date(last.timestamp))}</span>}</div>

    {bars.length < 5 && <p className={styles.source}>Limited history. Only available observations are shown.</p>}
  </div>;
}
