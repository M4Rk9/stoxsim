"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import styles from "./AppNavigation.module.css";

const areas = [
  { label: "Home", href: "/" },
  { label: "Markets", href: "/#stock-search" },
  { label: "Learn", href: "/learn" },
  { label: "Compete", href: "/competitions" },
  { label: "Account", href: "/settings" },
] as const;

export default function AppNavigation() {
  const pathname = usePathname();
  const [hash, setHash] = useState("");
  useEffect(() => {
    const update = () => setHash(window.location.hash);
    update(); window.addEventListener("hashchange", update);
    return () => window.removeEventListener("hashchange", update);
  }, [pathname]);
  const current = pathname === "/" ? (hash === "#stock-search" ? "Markets" : "Home")
    : /^(\/stocks|\/portfolio)/.test(pathname) ? "Markets"
      : /^(\/learn|\/progress|\/finwiz|\/scenario-lab)/.test(pathname) ? "Learn"
        : /^(\/competitions|\/campus)/.test(pathname) ? "Compete" : "Account";
  return <>
    <div className={styles.space} aria-hidden="true" />
    <nav className={styles.nav} aria-label="Main navigation">
      {areas.map(area => <a key={area.label} href={area.href}
        aria-current={current === area.label ? "page" : undefined}>{area.label}</a>)}
    </nav>
  </>;
}
