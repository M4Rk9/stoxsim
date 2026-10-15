const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
export const DAILY_VISIT_EVENT = "stoxsim-daily-visit";
type Session = { accessToken: string; user: { id: string } };
type Visit = { checkedInToday: boolean; lastCheckInDate?: string; checkInZoneId: string };
let owner: string | null = null;
let attemptedAt = 0;
let confirmed: Visit | null = null;
let pending: Promise<Visit | null> | null = null;

function localDate(zone: string, now: Date): string {
  const parts = new Intl.DateTimeFormat("en-US", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
  return ["year", "month", "day"].map(type => parts.find(part => part.type === type)?.value).join("-");
}
function session(): Session | null {
  try { return JSON.parse(sessionStorage.getItem("stoxsim-session") ?? "null") as Session | null; }
  catch { return null; }
}
/** Server-owned credit; foreground only, bounded retries, no invented historical visits. */
export function recordDailyVisit(): Promise<Visit | null> {
  if (typeof document === "undefined" || document.visibilityState !== "visible") return Promise.resolve(null);
  const current = session();
  const id = current?.user?.id ?? null;
  if (id !== owner) { owner = id; confirmed = null; attemptedAt = 0; pending = null; }
  if (!id || !current?.accessToken) return Promise.resolve(null);
  if (pending) return pending;
  try {
    if (confirmed?.checkedInToday && confirmed.lastCheckInDate === localDate(confirmed.checkInZoneId, new Date())) return Promise.resolve(confirmed);
  } catch { confirmed = null; }
  if (attemptedAt && Date.now() - attemptedAt < 60_000) return Promise.resolve(null);
  attemptedAt = Date.now();
  const request = fetch(`${API_URL}/api/v1/progression/check-in`, {
    method: "POST", credentials: "include", cache: "no-store",
    headers: { Authorization: `Bearer ${current.accessToken}` }, signal: AbortSignal.timeout(15_000),
  }).then(async response => {
    if (!response.ok) return null; // Existing page auth owns refresh; this optional request never logs a user out.
    const result = await response.json() as Visit;
    if (owner !== id || session()?.user?.id !== id) return null;
    if (result.checkedInToday && result.lastCheckInDate && result.checkInZoneId) {
      // Cache only visit acknowledgement. A cached full progression response would
      // overwrite newer XP/missions when Learning path loads later that day.
      confirmed = { checkedInToday: true, lastCheckInDate: result.lastCheckInDate, checkInZoneId: result.checkInZoneId };
    }
    window.dispatchEvent(new CustomEvent(DAILY_VISIT_EVENT, { detail: result }));
    return result;
  }).catch(() => null).finally(() => { if (pending === request) pending = null; });
  pending = request;
  return request;
}
