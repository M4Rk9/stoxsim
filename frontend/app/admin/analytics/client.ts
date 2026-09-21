const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
interface Session { accessToken: string }

export class ApiError extends Error {
  constructor(message: string, readonly status: number) { super(message); }
}

function readSession(): Session | null {
  try {
    const raw = window.sessionStorage.getItem("stoxsim-session");
    const session = raw ? JSON.parse(raw) as Session : null;
    return session?.accessToken ? session : null;
  } catch { return null; }
}

let refreshInFlight: Promise<Session> | null = null;
function refreshSession(): Promise<Session> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    const response = await fetch(`${API_URL}/api/v1/auth/refresh`, {
      method: "POST", credentials: "include", cache: "no-store",
    });
    if (!response.ok) throw new ApiError("Please sign in to view owner analytics.", response.status);
    const session = await response.json() as Session;
    window.sessionStorage.setItem("stoxsim-session", JSON.stringify(session));
    return session;
  })().finally(() => { refreshInFlight = null; });
  return refreshInFlight;
}

export async function loadAnalytics<T>(endpoint: "overview" | "activity", from: string, to: string, signal: AbortSignal): Promise<T> {
  const session = readSession() ?? await refreshSession();
  const url = `${API_URL}/api/v1/admin/analytics/${endpoint}?${new URLSearchParams({ from, to })}`;
  const request = (token: string) => fetch(url, {
    credentials: "include", cache: "no-store", signal,
    headers: { Authorization: `Bearer ${token}` },
  });
  let response = await request(session.accessToken);
  if (response.status === 401) response = await request((await refreshSession()).accessToken);
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new ApiError(payload?.message ?? "Analytics could not be loaded. Please try again.", response.status);
  }
  return response.json() as Promise<T>;
}
