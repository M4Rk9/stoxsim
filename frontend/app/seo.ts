import type { Metadata } from "next";

export const SITE_URL = new URL("https://stoxsim.com");
export const SITE_NAME = "StoxSim";
export const DEFAULT_TITLE = "StoxSim — Free Stock Market Simulator for India & US";
export const DEFAULT_DESCRIPTION =
  "Learn stock trading with virtual money on StoxSim. Practise Indian and US markets, research stocks and ETFs, and review simulated portfolio performance without risking real money.";

export const SEARCH_KEYWORDS = [
  "StoxSim",
  "stock market simulator",
  "paper trading simulator",
  "virtual trading India",
  "Indian stock market simulator",
  "US stock market simulator",
  "learn stock trading",
  "practice stock trading",
];

export function createPublicMetadata(
  title: string,
  description: string,
  path: string,
): Metadata {
  const absoluteTitle = `${title} | ${SITE_NAME}`;
  return {
    title,
    description,
    alternates: { canonical: path },
    openGraph: {
      type: "website",
      url: path,
      siteName: SITE_NAME,
      title: absoluteTitle,
      description,
      locale: "en_IN",
    },
    twitter: {
      card: "summary_large_image",
      title: absoluteTitle,
      description,
    },
  };
}

export const PRIVATE_PAGE_METADATA: Metadata = {
  robots: {
    index: false,
    follow: false,
    noarchive: true,
    nocache: true,
  },
};

export const siteStructuredData = [
  {
    "@context": "https://schema.org",
    "@type": "Organization",
    "@id": "https://stoxsim.com/#organization",
    name: SITE_NAME,
    url: SITE_URL.toString(),
    logo: "https://stoxsim.com/stoxsim-logo.png",
    email: "support.stoxsim@gmail.com",
  },
  {
    "@context": "https://schema.org",
    "@type": "WebSite",
    "@id": "https://stoxsim.com/#website",
    url: SITE_URL.toString(),
    name: SITE_NAME,
    alternateName: "StoxSim Paper Trading",
    description: DEFAULT_DESCRIPTION,
    publisher: { "@id": "https://stoxsim.com/#organization" },
    inLanguage: "en-IN",
  },
  {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    "@id": "https://stoxsim.com/#application",
    name: SITE_NAME,
    url: SITE_URL.toString(),
    description: DEFAULT_DESCRIPTION,
    applicationCategory: "FinanceApplication",
    applicationSubCategory: "Paper trading simulator",
    operatingSystem: "Web",
    browserRequirements: "Requires JavaScript and a modern web browser",
    isAccessibleForFree: true,
    offers: {
      "@type": "Offer",
      price: "0",
      priceCurrency: "INR",
    },
    featureList: [
      "Indian and US paper trading",
      "Virtual portfolios",
      "Stock and ETF research",
      "Simulated market orders and charges",
      "Portfolio analytics and learning feedback",
    ],
    publisher: { "@id": "https://stoxsim.com/#organization" },
    inLanguage: "en-IN",
  },
];
