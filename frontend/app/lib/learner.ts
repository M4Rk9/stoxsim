export interface LearnerProfile {
  experienceLevel: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | null;
  learningObjective: "BASICS" | "FUNDAMENTALS" | "DIVERSIFICATION" | "DISCIPLINE" | null;
  preferredMarket: "INDIA" | "UNITED_STATES" | null;
  updatedAt: string | null;
}
export interface Recommendation { id: string; title: string; description: string; href: string; actionLabel: string }
export interface LearnerHome { profile: LearnerProfile; recommendation: Recommendation }

/** Server text is displayed as text; links must remain within the learning app. */
export function safeLearningLink(value: string): string | null {
  if (!value || value.startsWith("//") || value.includes("\\")) return null;
  try {
    const url = new URL(value, "https://stoxsim.com");
    if (url.origin !== "https://stoxsim.com" || url.username || url.password) return null;
    const allowed = ["/", "/learn", "/progress", "/finwiz", "/scenario-lab", "/competitions", "/campus", "/portfolio", "/settings", "/billing"];
    return allowed.includes(url.pathname) ? url.pathname + url.search + url.hash : null;
  } catch { return null; }
}
