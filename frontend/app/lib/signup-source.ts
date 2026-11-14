// Broad, user-reported acquisition categories only. Never persist URLs or search terms.
export function signupSource(): string {
  if (typeof window === "undefined") return "UNKNOWN";
  try {
    const saved = sessionStorage.getItem("stoxsim-signup-source");
    if (saved && ["DIRECT", "SEARCH", "SOCIAL", "REFERRAL", "UNKNOWN"].includes(saved)) return saved;
    const campaign = new URLSearchParams(window.location.search).get("utm_source")?.toLowerCase();
    const host = document.referrer ? new URL(document.referrer).hostname.toLowerCase() : "";
    const social = ["reddit", "instagram", "facebook", "linkedin", "youtube", "twitter", "x"];
    const search = ["google", "bing", "duckduckgo", "yahoo"];
    const matches = (names: string[]) => names.some(name => campaign === name || host === `${name}.com` || host.endsWith(`.${name}.com`));
    const source = matches(social) ? "SOCIAL" : matches(search) ? "SEARCH"
      : campaign || (host && host !== location.hostname) ? "REFERRAL" : "DIRECT";
    sessionStorage.setItem("stoxsim-signup-source", source);
    return source;
  } catch { return "UNKNOWN"; }
}
