import type { ReactNode } from "react";

import { createPublicMetadata } from "../seo";

export const metadata = createPublicMetadata(
  "Service Status",
  "Check the current operational status of the StoxSim web application and API.",
  "/status",
);

export default function StatusLayout({ children }: { children: ReactNode }) {
  return children;
}

