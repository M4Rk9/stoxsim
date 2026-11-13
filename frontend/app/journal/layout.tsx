import { PRIVATE_PAGE_METADATA } from "../seo";
export const metadata = { ...PRIVATE_PAGE_METADATA, title: "Strategy journal" };
export default function Layout({ children }: { children: React.ReactNode }) { return children; }
