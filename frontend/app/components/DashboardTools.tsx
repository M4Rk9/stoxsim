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

    const scanSelectedStock = () => {
      const symbol = document
        .querySelector(".quoteCard .quoteTop h3")
        ?.textContent
        ?.trim();
      setSelectedStock((current) => {
        if (!symbol) return current == null ? current : null;
        if (current?.symbol === symbol && current.exchange === "NSE") return current;
        return { exchange: "NSE", symbol };
      });
    };

    scanSelectedStock();
    const timer = window.setInterval(scanSelectedStock, 500);
    return () => window.clearInterval(timer);
  }, [pathname]);

  useEffect(() => {
    setMenuOpen(false);
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

  const initial = session.user.displayName.trim().slice(0, 1).toUpperCase() || "U";

  return <>
    <div className={styles.profileMenuWrap} ref={wrapper}>
      <button
        type="button"
        className={styles.profileButton}
        aria-label={`Open account menu for ${session.user.displayName}`}
        aria-haspopup="menu"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((open) => !open)}
      >
        <span className={styles.avatar} aria-hidden="true">{initial}</span>
        <span className={styles.profileText}>
          <strong>{session.user.displayName}</strong>
          <small>{session.user.email}</small>
        </span>
        <svg className={menuOpen ? styles.chevronOpen : styles.chevron} viewBox="0 0 20 20" aria-hidden="true">
          <path d="m5.5 7.5 4.5 4.5 4.5-4.5" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.8" />
        </svg>
      </button>

      {menuOpen && <div className={styles.menu} role="menu">
        <div className={styles.identity}>
          <strong>{session.user.displayName}</strong>
          <span>{session.user.email}</span>
        </div>
        {pathname !== "/" && <a className={styles.menuLink} href="/" role="menuitem">
          Dashboard <span>→</span>
        </a>}
        <a className={styles.menuLink} href="/settings" role="menuitem">
          Account settings <span>→</span>
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
