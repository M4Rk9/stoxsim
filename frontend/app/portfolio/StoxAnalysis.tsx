"use client";
import { useEffect, useState } from "react";
import { authenticated } from "../campus/client";
import StoxScoreCard, { type PortfolioAnalytics } from "../components/StoxScoreCard";
import styles from "./portfolio.module.css";
type Sector = { name: string; value: number; percent: number };
const colors = ["#7c6ee6", "#00b386", "#f2b85b", "#5f9de8", "#e889a5", "#8eb68a", "#b39ddb", "#8a949e"];
export default function StoxAnalysis({ accountId, currency }: { accountId: string; currency: string }) {
  const [analytics, setAnalytics] = useState<PortfolioAnalytics | null>();
  const [sectors, setSectors] = useState<Sector[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(""); setSectors([]); setAnalytics(undefined);
    void authenticated<PortfolioAnalytics>(`accounts/${accountId}/portfolio/analytics`).then(data => { if (active) setAnalytics(data); }).catch(() => { if (active) setAnalytics(null); });
    void authenticated<Sector[]>(`accounts/${accountId}/portfolio/sectors`).then(data => { if (active) setSectors(data); }).catch(() => { if (active) setError("Sector allocation could not be loaded."); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [accountId]);
  const total = sectors.reduce((sum, s) => sum + s.value, 0);
  let offset = 0;
  return <section className={styles.stoxAnalysis} aria-labelledby="stox-analysis-heading">
    <h2 id="stox-analysis-heading">Stox Analysis</h2>
    <div className={styles.sectorCard}>
      <div><h3>Sector allocation</h3><p>Share of invested value</p></div>
      {loading ? <p role="status">Loading allocation…</p> : error ? <p role="alert">{error}</p> : total <= 0 ? <p>Make your first trade to see your sector allocation.</p> : <div className={styles.sectorGrid}>
        <svg viewBox="0 0 240 240" className={styles.donut} role="img" aria-label="Sector allocation pie chart">
          {sectors.map((sector, i) => {
            const percent = sector.value / total * 100, start = offset; offset += percent;
            return <circle key={sector.name} cx="120" cy="120" r="86" fill="none" stroke={colors[i % colors.length]} strokeWidth="32" pathLength="100" strokeDasharray={`${percent} ${100 - percent}`} strokeDashoffset={-start} transform="rotate(-90 120 120)"><title>{sector.name}: {percent.toFixed(2)}%</title></circle>;
          })}
          <text x="120" y="117" textAnchor="middle" fill="currentColor" fontSize="27" fontWeight="700">{sectors.length}</text>
          <text x="120" y="140" textAnchor="middle" fill="currentColor" fontSize="12">sectors</text>
        </svg>
        <ul className={styles.sectorLegend}>{sectors.map((sector, i) => <li key={sector.name}><i style={{ background: colors[i % colors.length] }} /><span>{sector.name}<small>{new Intl.NumberFormat(currency === "INR" ? "en-IN" : "en-US", { style: "currency", currency }).format(sector.value)}</small></span><strong>{(sector.value / total * 100).toFixed(2)}%</strong></li>)}</ul>
      </div>}
    </div>
    <StoxScoreCard analytics={analytics} />
  </section>;
}
