import type { Metadata } from "next";
import DashboardTools from "./components/DashboardTools";
import "./globals.css";
import "./theme.css";

export const metadata: Metadata = {
  title: "StoxSim | Practise markets. Risk nothing.",
  description: "Paper trade Indian and US stocks with virtual capital.",
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
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body>
        {children}
        <DashboardTools />
      </body>
    </html>
  );
}
