import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: ["/", "/terms", "/privacy", "/cookies", "/disclaimer", "/status"],
      disallow: [
        "/settings",
        "/verify-email",
        "/reset-password",
        "/forgot-password",
        "/finwiz",
        "/stocks/",
      ],
    },
    sitemap: "https://stoxsim.com/sitemap.xml",
  };
}
