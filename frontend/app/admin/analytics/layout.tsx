import type { ReactNode } from "react";
import { PRIVATE_PAGE_METADATA } from "../../seo";

export const metadata = { ...PRIVATE_PAGE_METADATA, title: "Owner analytics | StoxSim" };

export default function AnalyticsLayout({ children }: { children: ReactNode }) {
  return children;
}
