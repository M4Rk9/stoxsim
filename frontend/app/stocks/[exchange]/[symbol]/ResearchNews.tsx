"use client";
import { useEffect, useState } from "react";
import styles from "./stock.module.css";
type Article = { headline: string; source: string; url: string; publishedAt: string };
export type News = { status: "AVAILABLE" | "EMPTY" | "DISABLED" | "UNAVAILABLE"; provider: string; checkedAt: string; articles: Article[]; message: string };
function safeLink(value: string) { try { const url = new URL(value); return url.protocol === "https:" && !url.username && !url.password ? url.href : null; } catch { return null; } }
const time = (value: string) => { const date = new Date(value); return Number.isNaN(date.getTime()) ? "Time unavailable" : date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }); };
export default function ResearchNews({ listing, load }: { listing: string; load: (path: string) => Promise<News> }) {
  const [result, setResult] = useState<{ listing: string; data: News } | null>(null);
  const [failed, setFailed] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setResult(null); setFailed(null);
    load(`${listing}/news`).then(data => { if (active) setResult({ listing, data }); })
      .catch(() => { if (active) setFailed(listing); });
    return () => { active = false; };
    // Parent loader reads the current session and refreshes it on 401.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [listing, attempt]);
  const data = result?.listing === listing ? result.data : null;
  return <article className={styles.card} aria-labelledby="company-news-heading">
    <div className={styles.cardHeader}><div><h2 id="company-news-heading">Company news</h2><p>Read the evidence behind a headline</p></div></div>
    <div className={styles.profile}>
      {failed === listing ? <div role="alert"><p>News could not be checked. Your price and research tools remain available.</p><button type="button" onClick={() => setAttempt(value => value + 1)}>Try news again</button></div>
      : !data ? <p role="status">Checking available headlines…</p> : <>
        <p className={styles.description}>{data.message}</p>
        {data.status === "EMPTY" && <p>No usable headlines were returned for this listing. This does not mean nothing happened.</p>}
        {data.status === "AVAILABLE" && <ul className={styles.newsList}>{data.articles.map(article => {
          const href = safeLink(article.url); if (!href) return null;
          return <li key={article.url}><a href={href} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">{article.headline}<span className={styles.newsSource}>{article.source} · opens original article ↗</span></a><time dateTime={article.publishedAt}>Published {time(article.publishedAt)}</time></li>;
        })}</ul>}
        <details><summary>Coverage and sources</summary><p>Delivered by {data.provider}. Checked {time(data.checkedAt)}. Publication times are shown in your local time zone. This is a limited selection, not a complete news archive. Articles are not sent to Finwiz.</p></details>
      </>}
    </div>
  </article>;
}
