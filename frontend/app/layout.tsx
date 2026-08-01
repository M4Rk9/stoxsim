import type { Metadata } from "next";
import DashboardTools from "./components/DashboardTools";
import ThemeToggle from "./components/ThemeToggle";
import "./globals.css";
import "./theme.css";

export const metadata: Metadata = {
  title: "StoxSim | Practise markets. Risk nothing.",
  description: "Paper trade Indian and US stocks with virtual capital.",
};

const themeScript = `(() => {
  try {
    const saved = window.localStorage.getItem("stoxsim-theme");
    const theme = saved === "light" || saved === "dark"
      ? saved
      : window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
  } catch {
    document.documentElement.dataset.theme = "light";
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
        <ThemeToggle />
        <DashboardTools />
      </body>
    </html>
  );
}
