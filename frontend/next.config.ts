import type { NextConfig } from "next";

// This frontend talks to the Java API. No provider credentials belong here.
const unexpectedPublicVariables = Object.keys(process.env).filter(
  (name) => name.startsWith("NEXT_PUBLIC_") && name !== "NEXT_PUBLIC_API_URL",
);
if (unexpectedPublicVariables.length) {
  throw new Error(`Unapproved browser environment variables: ${unexpectedPublicVariables.join(", ")}`);
}
const apiUrl = new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080");
if (!["http:", "https:"].includes(apiUrl.protocol)
  || apiUrl.username || apiUrl.password || apiUrl.search || apiUrl.hash) {
  throw new Error("NEXT_PUBLIC_API_URL must be an HTTP(S) API address without credentials, query parameters or fragments");
}

const nextConfig: NextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  output: "standalone",
};

export default nextConfig;
