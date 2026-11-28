const key = "stoxsim-referral";

export function normalizeReferralCode(value: string | null | undefined): string | undefined {
  const code = value?.trim().toUpperCase();
  return code && /^[A-Z2-7]{16}$/.test(code) ? code : undefined;
}

export function savedReferralCode(): string | undefined {
  return normalizeReferralCode(sessionStorage.getItem(key));
}

export function saveReferralCode(value?: string) {
  const code = normalizeReferralCode(value);
  if (code) sessionStorage.setItem(key, code);
  else sessionStorage.removeItem(key);
}
