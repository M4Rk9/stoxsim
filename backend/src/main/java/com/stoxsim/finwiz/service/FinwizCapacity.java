package com.stoxsim.finwiz.service;

import com.stoxsim.finwiz.config.FinwizProperties;

/** Divides the configured upstream budget without borrowing between plans. */
final class FinwizCapacity {
    private FinwizCapacity() {}

    static int share(FinwizProperties properties, int limit, FinwizDepth depth) {
        int plus = properties.getPlusCapacityPercent();
        int pro = properties.getProCapacityPercent();
        if (plus < 0 || pro < 0 || (long) plus + pro > 100) {
            throw new IllegalArgumentException("Finwiz Plus and Pro capacity shares must be nonnegative and total at most 100");
        }
        if (limit <= 0) return 0;
        int proSlots = pro == 0 ? 0 : Math.max(1, floor(limit, pro));
        int plusSlots = plus == 0 ? 0 : Math.min(limit - proSlots, Math.max(1, floor(limit, plus)));
        int remainder = limit - proSlots - plusSlots;
        // A very small shared quota is reserved for Pro first, then Plus.
        // Unallocated rounding capacity goes to Free if it has a configured share.
        boolean freeEnabled = plus + pro < 100;
        if (!freeEnabled) {
            if (pro > 0) proSlots += remainder;
            else plusSlots += remainder;
            remainder = 0;
        }
        return switch (depth) {
            case BASIC -> remainder;
            case EXPANDED -> plusSlots;
            case FULL -> proSlots;
        };
    }

    static int providerShare(FinwizProperties properties, int limit, FinwizDepth depth, boolean paid) {
        if (!paid) return share(properties, limit, depth);
        return switch (depth) {
            case BASIC -> 0;
            case EXPANDED -> limit / 2;
            case FULL -> limit - limit / 2;
        };
    }

    private static int floor(int limit, int percent) {
        return (int)((long) limit * percent / 100);
    }
}
