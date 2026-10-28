import { randomBytes } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";

export function proxy(request: NextRequest) {
  const nonce = randomBytes(32).toString("base64");
  const api = new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080");
  const socket = `${api.protocol === "https:" ? "wss:" : "ws:"}//${api.host}`;
  const billing = request.nextUrl.pathname === "/admin/billing"
    || request.nextUrl.pathname.startsWith("/admin/billing/");
  const google = request.nextUrl.pathname === "/google-sign-in";
  const development = process.env.NODE_ENV === "development";
  const csp = [
    "default-src 'self'", "base-uri 'self'", "form-action 'self'",
    "frame-ancestors 'none'", "object-src 'none'",
    `script-src 'self' 'nonce-${nonce}'${development ? " 'unsafe-eval'" : ""}${billing ? " https://checkout.razorpay.com https://cdn.razorpay.com" : ""}`,
    "script-src-attr 'none'", `style-src 'self' 'unsafe-inline'${google ? " https://accounts.google.com/gsi/style" : ""}`,
    `img-src 'self' data:${billing ? " https://*.razorpay.com" : ""}${google ? " https://accounts.google.com/gsi/" : ""}`,
    "font-src 'self' data:",
    `connect-src 'self' ${api.origin} ${socket}${billing ? " https://*.razorpay.com" : ""}${google ? " https://accounts.google.com/gsi/" : ""}`,
    `frame-src ${google ? "https://accounts.google.com/gsi/" : billing ? "https://api.razorpay.com https://checkout.razorpay.com" : "'none'"}`,
    ...(api.protocol === "https:" ? ["upgrade-insecure-requests"] : []),
  ].join("; ");
  const forwarded = new Headers(request.headers);
  // Override caller-supplied headers; Next uses this trusted CSP to nonce hydration.
  forwarded.set("x-nonce", nonce);
  forwarded.set("Content-Security-Policy", csp);
  const response = NextResponse.next({ request: { headers: forwarded } });
  response.headers.set("Content-Security-Policy", csp);
  if (google) response.headers.set("Cross-Origin-Opener-Policy", "same-origin-allow-popups");
  response.headers.set("Cache-Control", "private, no-store");
  return response;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml).*)"],
};
