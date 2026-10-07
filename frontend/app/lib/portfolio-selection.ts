export interface SelectableAccount {
  id: string; marketRegion: string; accountKind: string; active: boolean; sandboxSlot: number;
}
export function marketAccount<T extends SelectableAccount>(accounts: T[], region: string): T | undefined {
  return accounts.find(a => a.marketRegion === region && a.active && a.accountKind === "SANDBOX" && a.sandboxSlot === 1)
    ?? accounts.find(a => a.marketRegion === region && a.active && a.accountKind === "STANDARD");
}
