import http from "k6/http";
import { check, sleep } from "k6";

const apiUrl = (__ENV.STAGING_API_URL || "").replace(/\/$/, "");
const webUrl = (__ENV.STAGING_WEB_URL || "").replace(/\/$/, "");
const password = "Stoxsim-load-2026";

if (!apiUrl.startsWith("https://") || !webUrl.startsWith("https://")) {
  throw new Error("STAGING_API_URL and STAGING_WEB_URL must be HTTPS URLs");
}

export const options = {
  scenarios: {
    staging_baseline: {
      executor: "constant-vus",
      vus: 4,
      duration: "3m",
      gracefulStop: "30s",
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
    "http_req_duration{expected_response:true}": ["p(95)<2000"],
    "http_req_duration{surface:authenticated}": ["p(95)<2000"],
  },
};

export function setup() {
  const email = `load-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}@stoxsim.test`;
  const response = http.post(
    `${apiUrl}/api/v1/auth/register`,
    JSON.stringify({
      displayName: "StoxSim Load Test",
      email,
      password,
      termsAccepted: true,
    }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "setup_register" },
    },
  );

  const registered = check(response, {
    "load fixture registered": (result) => result.status === 201,
    "load fixture received access token": (result) => Boolean(result.json("accessToken")),
  });
  if (!registered) {
    throw new Error(`Could not create the load-test fixture (HTTP ${response.status})`);
  }

  return {
    email,
    password,
    accessToken: response.json("accessToken"),
  };
}

const surfaces = [
  { name: "web_home", path: "", publicWeb: true },
  { name: "system_status", path: "/api/v1/system/status", publicApi: true },
  { name: "current_user", path: "/api/v1/auth/me" },
  { name: "india_portfolio", path: "/api/v1/portfolio?marketRegion=INDIA" },
  { name: "india_orders", path: "/api/v1/orders?marketRegion=INDIA" },
  { name: "default_watchlist", path: "/api/v1/watchlists/default" },
];

export default function (fixture) {
  const surface = surfaces[__ITER % surfaces.length];
  const target = surface.publicWeb
    ? webUrl
    : `${apiUrl}${surface.path}`;
  const params = {
    headers: surface.publicWeb || surface.publicApi
      ? {}
      : { Authorization: `Bearer ${fixture.accessToken}` },
    tags: {
      name: surface.name,
      surface: surface.publicWeb || surface.publicApi ? "public" : "authenticated",
    },
  };
  const response = http.get(target, params);

  check(response, {
    [`${surface.name} returned 200`]: (result) => result.status === 200,
  });
  sleep(1);
}

export function teardown(fixture) {
  if (!fixture?.accessToken) {
    return;
  }
  const response = http.del(
    `${apiUrl}/api/v1/auth/me`,
    JSON.stringify({ password: fixture.password }),
    {
      headers: {
        Authorization: `Bearer ${fixture.accessToken}`,
        "Content-Type": "application/json",
      },
      tags: { name: "teardown_delete_account" },
    },
  );
  check(response, {
    "load fixture deleted": (result) => result.status === 204,
  });
}
