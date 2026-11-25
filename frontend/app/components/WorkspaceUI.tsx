import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from "react";

export function WorkspaceButton({className="", ...props}: ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button {...props} className={`workspaceButton ${className}`}/>;
}
export function WorkspaceNotice({tone="neutral", children, className="", ...props}: HTMLAttributes<HTMLDivElement> & {tone?: "neutral" | "error" | "success"; children: ReactNode}) {
  return <div {...props} role={tone==="error" ? "alert" : "status"} className={`workspaceNotice workspaceNotice-${tone} ${className}`}>{children}</div>;
}
export function WorkspaceBadge({children}: {children: ReactNode}) {
  return <span className="workspaceBadge">{children}</span>;
}
export function WorkspaceSkeleton() {
  return <span className="workspaceSkeleton" aria-hidden="true"/>;
}
export function WorkspaceCard({className="", ...props}: HTMLAttributes<HTMLElement>) {
  return <section {...props} className={`workspaceCard ${className}`}/>;
}
