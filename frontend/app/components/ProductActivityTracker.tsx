"use client";

import { useEffect } from "react";
import { recordProductActivity } from "../lib/product-activity";

export default function ProductActivityTracker() {
  useEffect(() => {
    let observedUser: string | null = null;
    const activity = () => recordProductActivity("ACTIVE");
    const observeLogin = () => {
      try {
        const session = JSON.parse(sessionStorage.getItem("stoxsim-session") ?? "null");
        const user = session?.user?.id ?? null;
        if (user !== observedUser) { observedUser = user; if (user) activity(); }
      } catch { observedUser = null; }
    };
    observeLogin();
    // Detect an asynchronously restored/login session, without recording idle activity.
    const timer = window.setInterval(observeLogin, 2000);
    window.addEventListener("pointerdown", activity, { passive: true });
    window.addEventListener("keydown", activity);
    window.addEventListener("scroll", activity, { passive: true });
    document.addEventListener("visibilitychange", activity);
    return () => {
      clearInterval(timer);
      window.removeEventListener("pointerdown", activity);
      window.removeEventListener("keydown", activity);
      window.removeEventListener("scroll", activity);
      document.removeEventListener("visibilitychange", activity);
    };
  }, []);
  return null;
}
