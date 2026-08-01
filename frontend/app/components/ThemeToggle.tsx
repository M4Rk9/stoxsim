"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import styles from "./ThemeToggle.module.css";

type Theme = "light" | "dark";

function currentTheme(): Theme {
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

function hasSession() {
  try {
    return Boolean(window.localStorage.getItem("stoxsim-session"));
  } catch {
    return false;
  }
}

export default function ThemeToggle() {
  const pathname = usePathname();
  const [theme, setTheme] = useState<Theme>("light");
  const [signedIn, setSignedIn] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setTheme(currentTheme());
    setReady(true);
    const syncSession = () => setSignedIn(hasSession());
    syncSession();
    const timer = window.setInterval(syncSession, 1_000);
    window.addEventListener("storage", syncSession);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("storage", syncSession);
    };
  }, [pathname]);

  function apply(next: Theme) {
    document.documentElement.dataset.theme = next;
    document.documentElement.style.colorScheme = next;
    window.localStorage.setItem("stoxsim-theme", next);
    setTheme(next);
  }

  if (!ready) return null;

  const next = theme === "dark" ? "light" : "dark";
  const dashboardPosition = pathname === "/" && signedIn;

  return <button
    type="button"
    className={`${styles.toggle} ${dashboardPosition ? styles.dashboard : styles.standalone}`}
    aria-label={`Switch to ${next} mode`}
    title={`Switch to ${next} mode`}
    onClick={() => apply(next)}
  >
    {theme === "dark" ? <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg> : <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M20.5 14.3A8.4 8.4 0 0 1 9.7 3.5 8.7 8.7 0 1 0 20.5 14.3Z" />
    </svg>}
    <span>{theme === "dark" ? "Light" : "Dark"}</span>
  </button>;
}
