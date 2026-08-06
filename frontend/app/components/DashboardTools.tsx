"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import styles from "./DashboardTools.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type ThemePreference = "light" | "dark" | "system";
type ResolvedTheme = "light" | "dark";

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

function readThemePreference(): ThemePreference {
  try {
    const saved = window.localStorage.getItem("stoxsim-theme");
    return saved === "light" || saved === "dark" || saved === "system"
      ? saved
      : "system";
  } catch {
    return "system";
  }
}

function resolveTheme(preference: ThemePreference, systemDark: boolean): ResolvedTheme {
  return preference === "system"
    ? systemDark ? "dark" : "light"
    : preference;
}

function applyResolvedTheme(preference: ThemePreference, systemDark: boolean) {
  const resolved = resolveTheme(preference, systemDark);
  document.documentElement.dataset.theme = resolved;
  document.documentElement.dataset.themePreference = preference;
  document.documentElement.style.colorScheme = resolved;
}

const themeOptions: Array<{
  value: ThemePreference;
  label: string;
  icon: "sun" | "moon" | "system";
}> = [
  { value: "light", label: "Light", icon: "sun" },
  { value: "dark", label: "Dark", icon: "moon" },
  { value: "system", label: "System", icon: "system" },
];

function ThemeIcon({ icon }: { icon: "sun" | "moon" | "system" }) {
  if (icon === "sun") {
    return <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="3.5" />
      <path d="M12 2.5v2M12 19.5v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2.5 12h2M19.5 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>;
  }
  if (icon === "moon") {
    return <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M20.5 14.3A8.4 8.4 0 0 1 9.7 3.5 8.7 8.7 0 1 0 20.5 14.3Z" />
    </svg>;
  }
  return <svg viewBox="0 0 24 24" aria-hidden="true">
    <rect x="3.5" y="4.5" width="17" height="12" rx="2" />
    <path d="M8.5 20h7M12 16.5V20" />
  </svg>;
}

export default function DashboardTools() {
  const pathname = usePathname();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [selectedStock, setSelectedStock] = useState<SelectedStock | null>(null);
  const [themePreference, setThemePreference] = useState<ThemePreference>("system");
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
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const sync = () => {
      const preference = readThemePreference();
      setThemePreference(preference);
      applyResolvedTheme(preference, media.matches);
    };
    const syncSystemTheme = () => {
      if (readThemePreference() === "system") sync();
    };

    sync();
    window.addEventListener("storage", sync);
    media.addEventListener("change", syncSystemTheme);
    return () => {
      window.removeEventListener("storage", sync);
      media.removeEventListener("change", syncSystemTheme);
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

  function selectTheme(preference: ThemePreference) {
    window.localStorage.setItem("stoxsim-theme", preference);
    setThemePreference(preference);
    applyResolvedTheme(
      preference,
      window.matchMedia("(prefers-color-scheme: dark)").matches,
    );
  }

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
