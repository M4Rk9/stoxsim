import type { MetadataRoute } from "next";

const publicRoutes = [
  { path: "", changeFrequency: "weekly", priority: 1 },
  { path: "/status", changeFrequency: "daily", priority: 0.7 },
  { path: "/terms", changeFrequency: "monthly", priority: 0.4 },
  { path: "/privacy", changeFrequency: "monthly", priority: 0.4 },
  { path: "/cookies", changeFrequency: "monthly", priority: 0.4 },
  { path: "/disclaimer", changeFrequency: "monthly", priority: 0.4 },
] as const;

export default function sitemap(): MetadataRoute.Sitemap {
  return publicRoutes.map(({ path, changeFrequency, priority }) => ({
    url: `https://stoxsim.com${path}`,
    changeFrequency,
    priority,
  }));
}
