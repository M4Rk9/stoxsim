import type { ReactNode } from "react";
import { PRIVATE_PAGE_METADATA } from "../seo";

export const metadata = PRIVATE_PAGE_METADATA;

export default function PrivateLayout({ children }: { children: ReactNode }) {
  return children;
}

