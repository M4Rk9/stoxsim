/** Providers already scale financial values. Format their declared units; never scale again. */
export function financialValue(value: number | undefined, unitsIn?: string) {
  if (value == null || !Number.isFinite(value)) return "—";
  const units = unitsIn?.trim() ?? "";
  const normalized = units.toLowerCase().replace(/\s+/g, " ");
  const formatted = new Intl.NumberFormat(normalized.startsWith("usd") ? "en-US" : "en-IN", { maximumFractionDigits: 2 }).format(value);
  if (normalized === "usd million") return `$${formatted}M`;
  if (normalized === "inr crore" || normalized === "crore") return `₹${formatted} Cr`;
  return units ? `${formatted} ${units}` : formatted;
}
