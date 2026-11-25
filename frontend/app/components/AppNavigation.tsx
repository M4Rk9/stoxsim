"use client";
import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import Brand from "./Brand";
import styles from "./AppNavigation.module.css";

const areas = [
  { label: "Home", href: "/", icon: "M3 10 12 3l9 7v11h-6v-7H9v7H3z" },
  { label: "Markets", href: "/#stock-search", icon: "M4 19V5m0 14h16M7 15l4-5 4 3 5-7" },
  { label: "Portfolio", href: "/portfolio", icon: "M3 7h18v14H3zM3 7l3-4h12l3 4m-4 7h4", desktop: true },
  { label: "Derivatives", href: "/markets/derivatives", icon: "M3 7h18m-4-4 4 4-4 4M21 17H3m4-4-4 4 4 4", desktop: true },
  { label: "Finwiz AI", href: "/finwiz", icon: "M12 2l3 7 7 3-7 3-3 7-3-7-7-3 7-3z", desktop: true },
  { label: "Strategy Journal", href: "/journal", icon: "M5 3h14v18H5zM9 7h6M9 11h6M9 15h4", desktop: true },
  { label: "Scenario Lab", href: "/scenario-lab", icon: "M9 3h6m-5 0v7l-6 9h16l-6-9V3M7 15h10", desktop: true },
  { label: "Learn", href: "/learn", icon: "M3 5h7l2 2 2-2h7v15h-7l-2 2-2-2H3zM12 7v15" },
  { label: "Compete", href: "/competitions", icon: "M8 3h8v7a4 4 0 0 1-8 0zM8 5H4v3a4 4 0 0 0 4 4m8-7h4v3a4 4 0 0 1-4 4m-4 2v5m-4 1h8" },
  { label: "Account", href: "/settings", icon: "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21v-2a8 8 0 0 1 16 0v2" },
] as const;

export default function AppNavigation() {
  const pathname = usePathname();
  const [hash, setHash] = useState("");
  const [collapsed, setCollapsed] = useState(false);
  useEffect(() => {
    const update = () => setHash(window.location.hash);
    update(); window.addEventListener("hashchange", update);
    return () => window.removeEventListener("hashchange", update);
  }, [pathname]);
  const current = pathname === "/" ? (hash === "#stock-search" ? "Markets" : "Home")
    : pathname.startsWith("/markets/derivatives") ? "Derivatives"
    : pathname.startsWith("/portfolio") ? "Portfolio"
    : /^(\/stocks|\/markets)/.test(pathname) ? "Markets"
    : pathname === "/journal" ? "Strategy Journal"
    : pathname === "/scenario-lab" ? "Scenario Lab"
    : /^(\/learn|\/progress)/.test(pathname) ? "Learn"
    : /^(\/competitions|\/campus)/.test(pathname) ? "Compete" : "Account";
  if (pathname === "/finwiz") return null;
  const mobileCurrent = ["Portfolio", "Derivatives"].includes(current) ? "Markets"
    : ["Strategy Journal", "Scenario Lab"].includes(current) ? "Learn" : current;
  return <>
    <div className={styles.space} aria-hidden="true" />
    <aside className={`redesignNav ${styles.rail}`} data-collapsed={collapsed}>
      <div className={styles.brandRow}>
        {!collapsed && <Brand />}
        <button type="button" className={styles.collapse} aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"} aria-expanded={!collapsed} onClick={() => setCollapsed(value => !value)}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/></svg>
        </button>
      </div>
      <nav className={styles.nav} aria-label="Main navigation">
        {areas.map(area => <a key={area.label} href={area.href} className={"desktop" in area ? styles.desktopOnly : undefined}
          aria-label={area.label} title={area.label} aria-current={current === area.label ? "page" : undefined}
          data-mobile-current={mobileCurrent === area.label ? "true" : undefined}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d={area.icon}/></svg><span>{area.label}</span>
        </a>)}
      </nav>
    </aside>
  </>;
}
