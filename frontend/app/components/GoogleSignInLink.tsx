"use client";

import { useEffect, useState } from "react";

export function GoogleSignInLink({ manage = false }: { manage?: boolean }) {
  const [enabled, setEnabled] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    void fetch(`${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}/api/v1/auth/google/config`, {
      method: "POST", cache: "no-store", signal: controller.signal,
    }).then(response => { if (response.ok) setEnabled(true); }).catch(() => undefined);
    return () => controller.abort();
  }, []);
  return enabled ? <a className="authHelpLink" href={manage ? "/google-sign-in/manage" : "/google-sign-in"}>
    {manage ? "Manage Google sign-in" : "Sign in with your linked Google account"}
  </a> : null;
}
