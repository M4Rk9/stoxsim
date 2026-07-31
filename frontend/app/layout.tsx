import type { Metadata } from "next";
import DashboardTools from "./components/DashboardTools";
import "./globals.css";

export const metadata: Metadata = {
  title: "StoxSim | Practise markets. Risk nothing.",
  description: "Paper trade Indian and US stocks with virtual capital.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        {children}
        <DashboardTools />
      </body>
    </html>
  );
}
