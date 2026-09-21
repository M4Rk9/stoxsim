type BrowserEvent = "ACTIVE" | "STOCK_OPENED";
const attempts = new Map<string, number>();
const completed = new Set<string>();
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/** Best effort, no extra cookies or persistent tracking IDs, no background heartbeat. */
export function recordProductActivity(event: BrowserEvent) {
  if (typeof window === "undefined" || document.visibilityState !== "visible") return;
  try {
    const session = JSON.parse(sessionStorage.getItem("stoxsim-session") ?? "null");
    if (!session?.accessToken || !session?.user?.id || session.user.platformAdmin) return;
    const key = `${session.user.id}:${new Date().toISOString().slice(0, 10)}:${event}`;
    const now = Date.now();
    if (completed.has(key) || now - (attempts.get(key) ?? 0) < 300_000) return;
    // Bound memory even if a tab stays open for months.
    if (attempts.size > 20) { attempts.clear(); completed.clear(); }
    attempts.set(key, now);
    void fetch(`${API_URL}/api/v1/analytics/events`, {
      method: "POST", credentials: "include", cache: "no-store",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${session.accessToken}` },
      body: JSON.stringify({ version: 1, event }),
    }).then(response => { if (response.ok) completed.add(key); }).catch(() => {});
  } catch { /* Analytics must not interrupt learner actions or trigger authentication. */ }
}
