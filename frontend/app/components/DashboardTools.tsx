"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import styles from "./DashboardTools.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type ThemePreference = "light" | "dark";

interface StoredSession {
  user: {
    id: string;
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
    const value = window.sessionStorage.getItem("stoxsim-session");
    return value ? JSON.parse(value) as StoredSession : null;
  } catch {
    return null;
  }
}

function themeStorageKey(session: StoredSession | null) {
  return session?.user.id ? `stoxsim-theme:${session.user.id}` : null;
}

function readThemePreference(session: StoredSession | null): ThemePreference {
  try {
    const key = themeStorageKey(session);
    const saved = key ? window.localStorage.getItem(key) : null;
    return saved === "dark" ? "dark" : "light";
  } catch {
    return "light";
  }
}

function applyTheme(preference: ThemePreference) {
  document.documentElement.dataset.theme = preference;
  document.documentElement.dataset.themePreference = preference;
  document.documentElement.style.colorScheme = preference;
}

const themeOptions: Array<{
  value: ThemePreference;
  label: string;
  icon: "sun" | "moon";
}> = [
  { value: "light", label: "Light", icon: "sun" },
  { value: "dark", label: "Dark", icon: "moon" },
];

function ThemeIcon({ icon }: { icon: "sun" | "moon" }) {
  if (icon === "sun") {
    return <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="3.5" />
      <path d="M12 2.5v2M12 19.5v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2.5 12h2M19.5 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>;
  }
  return <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M20.5 14.3A8.4 8.4 0 0 1 9.7 3.5 8.7 8.7 0 1 0 20.5 14.3Z" />
  </svg>;
}

export default function DashboardTools() {
  const pathname = usePathname();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [selectedStock, setSelectedStock] = useState<SelectedStock | null>(null);
  const [themePreference, setThemePreference] = useState<ThemePreference>("light");
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
    const sync = () => {
      const preference = readThemePreference(session);
      setThemePreference(preference);
      applyTheme(preference);
    };
    sync();
    window.addEventListener("storage", sync);
    return () => window.removeEventListener("storage", sync);
  }, [session?.user.id]);

  useEffect(() => {
    if (pathname !== "/") {
      setSelectedStock(null);
      return;
    }

    const scanSelectedStock = () => {
      const quoteCard = document.querySelector<HTMLElement>(".quoteCard");
      const symbol = quoteCard?.querySelector(".quoteTop h3")?.textContent?.trim();
      const exchange = quoteCard?.dataset.exchange?.trim().toUpperCase();
      setSelectedStock((current) => {
        if (!symbol || !exchange) return current == null ? current : null;
        if (current?.symbol === symbol && current.exchange === exchange) return current;
        return { exchange, symbol };
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

  function selectTheme(preference: ThemePreference) {
    const key = themeStorageKey(session);
    if (key) window.localStorage.setItem(key, preference);
    setThemePreference(preference);
    applyTheme(preference);
  }

  async function signOut() {
    await fetch(`${API_URL}/api/v1/auth/logout`, {
      method: "POST",
      credentials: "include",
    }).catch(() => undefined);
    window.sessionStorage.removeItem("stoxsim-session");
    applyTheme("light");
    window.location.assign("/");
  }

  if (!session) return null;
  if (pathname === "/finwiz") return null;

  const initial = session.user.displayName.trim().slice(0, 1).toUpperCase() || "U";
  const isDashboard = pathname === "/";

  return <>
    <div className={styles.profileMenuWrap} ref={wrapper}>
      <button
        type="button"
        className={isDashboard ? styles.profileButton : styles.profileButtonCompact}
        aria-label={`Open account menu for ${session.user.displayName}`}
        aria-haspopup="menu"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((open) => !open)}
      >
        <span className={styles.avatar} aria-hidden="true">{initial}</span>
        {isDashboard && <span className={styles.profileText}>
          <strong>{session.user.displayName}</strong>
          <small>{session.user.email}</small>
        </span>}
        <svg className={menuOpen ? styles.chevronOpen : styles.chevron} viewBox="0 0 20 20" aria-hidden="true">
          <path d="m5.5 7.5 4.5 4.5 4.5-4.5" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.8" />
        </svg>
      </button>

      {menuOpen && <div className={styles.menu} role="menu">
        <div className={styles.identity}>
          <strong>{session.user.displayName}</strong>
          <span>{session.user.email}</span>
        </div>
        {!isDashboard && <a className={styles.menuLink} href="/" role="menuitem">
          Dashboard <span>→</span>
        </a>}
        <a
          className={styles.menuLink}
          href="/portfolio"
          target="_blank"
          rel="noopener noreferrer"
          role="menuitem"
        >
          Portfolio <span>↗</span>
        </a>
        <a className={styles.menuLink} href="/settings" role="menuitem">
          Account settings <span>→</span>
        </a>

        <div className={styles.appearanceSection}>
          <div className={styles.appearanceHeader}>
            <span>Appearance</span>
            <small>Choose how StoxSim looks</small>
          </div>
          <div className={styles.themeOptions} role="group" aria-label="Appearance">
            {themeOptions.map((option) => <button
              key={option.value}
              type="button"
              className={themePreference === option.value ? styles.themeOptionActive : styles.themeOption}
              aria-label={`Use ${option.label.toLowerCase()} appearance`}
              aria-pressed={themePreference === option.value}
              onClick={() => selectTheme(option.value)}
            >
              <ThemeIcon icon={option.icon} />
              <span>{option.label}</span>
            </button>)}
          </div>
        </div>

        <button type="button" className={styles.signOut} role="menuitem" onClick={signOut}>
          Sign out <span>→</span>
        </button>
      </div>}
    </div>

    {isDashboard && <div className={styles.quickActions}>
      {selectedStock && <a
        className={styles.stockLink}
        href={`/stocks/${encodeURIComponent(selectedStock.exchange)}/${encodeURIComponent(selectedStock.symbol)}`}
        target="_blank"
        rel="noopener noreferrer"
      >
        Study {selectedStock.symbol} in detail <span aria-hidden="true">↗</span>
      </a>}

      <a className={styles.finwizLauncher} href="/finwiz" aria-label="Ask Finwiz AI">
        <span className={styles.finwizCore} aria-hidden="true">
          <svg viewBox="0 0 64 64">
            <circle cx="32" cy="32" r="27" />
            <circle cx="32" cy="32" r="21" />
            <circle cx="32" cy="32" r="8" />
            <path d="M32 5v9M32 50v9M5 32h9M50 32h9M12.9 12.9l6.4 6.4M44.7 44.7l6.4 6.4M51.1 12.9l-6.4 6.4M19.3 44.7l-6.4 6.4" />
            <path d="m32 16 13.9 24H18.1L32 16Z" />
          </svg>
        </span>
      </a>
    </div>}
  </>;
}
