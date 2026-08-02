import type { Metadata } from "next";
import DashboardTools from "./components/DashboardTools";
import "./globals.css";
import "./theme.css";

export const metadata: Metadata = {
  title: "StoxSim | Practise markets. Risk nothing.",
  description: "Paper trade Indian and US stocks with virtual capital.",
};

const themeScript = `(() => {
  try {
    const saved = window.localStorage.getItem("stoxsim-theme");
    const preference = saved === "light" || saved === "dark" || saved === "system"
      ? saved
      : "system";
    const resolved = preference === "system"
      ? window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"
      : preference;
    document.documentElement.dataset.theme = resolved;
    document.documentElement.dataset.themePreference = preference;
    document.documentElement.style.colorScheme = resolved;
  } catch {
    document.documentElement.dataset.theme = "light";
    document.documentElement.dataset.themePreference = "system";
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
