import { redirect } from "next/navigation";
import { normalizeReferralCode } from "../lib/referral-code";

export const metadata = { robots: { index: false, follow: false } };

export default async function InvitePage({ searchParams }: { searchParams: Promise<{ ref?: string | string[] }> }) {
  const { ref } = await searchParams;
  const code = typeof ref === "string" ? normalizeReferralCode(ref) : undefined;
  redirect(code ? "/?ref=" + code : "/");
}
