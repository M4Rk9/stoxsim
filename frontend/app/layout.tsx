import type { Metadata } from "next";
import DashboardTools from "./components/DashboardTools";
import {
  DEFAULT_DESCRIPTION,
  DEFAULT_TITLE,
  SEARCH_KEYWORDS,
  SITE_NAME,
  SITE_URL,
  siteStructuredData,
} from "./seo";
import "./globals.css";
import "./theme.css";

export const metadata: Metadata = {
  metadataBase: SITE_URL,
  title: {
    default: DEFAULT_TITLE,
    template: "%s | StoxSim",
  },
  description: DEFAULT_DESCRIPTION,
  applicationName: SITE_NAME,
  keywords: SEARCH_KEYWORDS,
  authors: [{ name: SITE_NAME, url: SITE_URL }],
  creator: SITE_NAME,
  publisher: SITE_NAME,
  category: "finance",
  alternates: {
    canonical: "/",
  },
  openGraph: {
    type: "website",
    url: "/",
    siteName: SITE_NAME,
    title: DEFAULT_TITLE,
    description: DEFAULT_DESCRIPTION,
    locale: "en_IN",
  },
  twitter: {
    card: "summary_large_image",
    title: DEFAULT_TITLE,
    description: DEFAULT_DESCRIPTION,
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-image-preview": "large",
      "max-snippet": -1,
      "max-video-preview": -1,
    },
  },
  icons: {
    icon: "/stoxsim-logo.png",
    apple: "/stoxsim-logo.png",
  },
};

const themeScript = `(() => {
  try {
    const storedSession = window.sessionStorage.getItem("stoxsim-session");
    const session = storedSession ? JSON.parse(storedSession) : null;
    const userId = typeof session?.user?.id === "string" ? session.user.id : "";
    const storageKey = userId ? "stoxsim-theme:" + userId : null;
    const saved = storageKey ? window.localStorage.getItem(storageKey) : null;
    const preference = saved === "dark" ? "dark" : "light";
    document.documentElement.dataset.theme = preference;
    document.documentElement.dataset.themePreference = preference;
    document.documentElement.style.colorScheme = preference;
  } catch {
    document.documentElement.dataset.theme = "light";
    document.documentElement.dataset.themePreference = "light";
    document.documentElement.style.colorScheme = "light";
  }
})();`;

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{
            __html: JSON.stringify(siteStructuredData).replace(/</g, "\\u003c"),
          }}
        />
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body>
        <a className="skipLink" href="#main-content">Skip to main content</a>
        {children}
        <DashboardTools />
      </body>
    </html>
  );
}
