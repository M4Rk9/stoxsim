"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import styles from "./DashboardTools.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface StoredSession {
  refreshToken: string;
  user: {
    email: string;
    displayName: string;
  };
}

interface SelectedStock {
  exchange: string;
  symbol: string;
}

function readSession(): StoredSession | null {
  try {
    const value = window.localStorage.getItem("stoxsim-session");
    return value ? JSON.parse(value) as StoredSession : null;
  } catch {
    return null;
  }
}

export default function DashboardTools() {
  const pathname = usePathname();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [selectedStock, setSelectedStock] = useState<SelectedStock | null>(null);
  const wrapper = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const sync = () => setSession(readSession());
    sync();
    const timer = window.setInterval(sync, 1_000);
    window.addEventListener("storage", sync);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("storage", sync);
    };
  }, []);

  useEffect(() => {
    if (pathname !== "/") {
      setSelectedStock(null);
      return;
    }
    const scan = () => {
      const quoteCard = document.querySelector(".quoteCard");
      const symbol = quoteCard?.querySelector(".quoteTop h3")?.textContent?.trim();
      if (!symbol) {
        setSelectedStock(null);
        return;
      }
      const exchange = quoteCard
        .querySelector(".quoteStats")
        ? "NSE"
        : "NSE";
      setSelectedStock((current) => current?.symbol === symbol
        ? current
        : { exchange, symbol });
    };
    scan();
    const observer = new MutationObserver(scan);
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, [pathname]);

  useEffect(() => {
    const close = (event: globalThis.MouseEvent) => {
      if (!wrapper.current?.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  async function signOut() {
    const active = readSession();
    if (active?.refreshToken) {
      await fetch(`${API_URL}/api/v1/auth/logout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: active.refreshToken }),
      }).catch(() => undefined);
    }
    window.localStorage.removeItem("stoxsim-session");
    window.location.assign("/");
  }

  if (!session) return null;

  return <>
    <div className={styles.accountWrap} ref={wrapper}>
      <button
        type="button"
        className={styles.accountButton}
        aria-haspopup="menu"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((open) => !open)}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 2a4 4 0 1 1 0 8 4 4 0 0 1 0-8Zm0 10c5.1 0 8 2.55 8 5.7V20H4v-2.3C4 14.55 6.9 12 12 12Z" fill="currentColor" />
        </svg>
        Account
      </button>
      {menuOpen && <div className={styles.menu} role="menu">
        <div className={styles.identity}>
          <strong>{session.user.displayName}</strong>
          <span>{session.user.email}</span>
        </div>
        <a className={styles.menuLink} href="/settings" role="menuitem">
          Profile &amp; security <span>→</span>
        </a>
        <button type="button" className={styles.signOut} role="menuitem" onClick={signOut}>
          Sign out <span>→</span>
        </button>
      </div>}
    </div>

    {pathname === "/" && selectedStock && <a
      className={styles.stockLink}
      href={`/stocks/${encodeURIComponent(selectedStock.exchange)}/${encodeURIComponent(selectedStock.symbol)}`}
      target="_blank"
      rel="noopener noreferrer"
    >
      Study {selectedStock.symbol} in detail <span aria-hidden="true">↗</span>
    </a>}
  </>;
}
