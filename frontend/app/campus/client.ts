const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
export interface Session { accessToken: string; user: { id: string } }
export class ApiError extends Error { constructor(message: string, readonly status: number) { super(message); } }
export function session(): Session | null {
  try { return JSON.parse(sessionStorage.getItem("stoxsim-session") ?? "null") as Session | null; } catch { return null; }
}
let refreshing: Promise<Session> | null = null;
async function request<T>(path: string, options: RequestInit, token?: string): Promise<T> {
  const response = await fetch(`${API_URL}/api/v1/${path}`, { ...options, credentials: "include", cache: "no-store",
    headers: { ...(options.body ? { "Content-Type": "application/json" } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) } });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new ApiError(error?.message ?? error?.detail ?? "This request could not be completed. Please try again.", response.status);
  }
  const body = await response.text();
  return (body ? JSON.parse(body) : null) as T;
}
async function refresh(): Promise<Session> {
  if (!refreshing) refreshing = request<Session>("auth/refresh", { method: "POST" })
    .then(value => { sessionStorage.setItem("stoxsim-session", JSON.stringify(value)); return value; })
    .finally(() => { refreshing = null; });
  return refreshing;
}
export async function campus<T>(path = "", body?: unknown): Promise<T> {
  const options = body === undefined ? {} : { method: "POST", ...(body === null ? {} : { body: JSON.stringify(body) }) };
  const current = session() ?? await refresh();
  try { return await request<T>(`campus${path}`, options, current.accessToken); }
  catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    return request<T>(`campus${path}`, options, (await refresh()).accessToken);
  }
}
