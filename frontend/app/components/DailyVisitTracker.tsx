"use client";
import { useEffect } from "react";
import { recordDailyVisit } from "../lib/daily-visit";
export default function DailyVisitTracker() {
  useEffect(() => {
    const record = () => { void recordDailyVisit(); };
    record();
    const timer = window.setInterval(record, 15_000);
    document.addEventListener("visibilitychange", record);
    return () => { clearInterval(timer); document.removeEventListener("visibilitychange", record); };
  }, []);
  return null;
}
