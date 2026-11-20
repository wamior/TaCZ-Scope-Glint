package ru.wamior.taczscopeglint.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScopeModeTest {
    @Test void highAdsZoomDoesNotTurnCollimatorsIntoScopes() {
        assertFalse(ScopeMode.canGlint(false, true, new float[]{2.5f}, new int[]{1}, 0, 1, v -> true));
    }

    @Test void highZoomHybridRedDotNeverGlintsEvenBelowTheThreshold() {
        float[] zooms = {4, 2.5f};
        int[] views = {2, 1};
        assertTrue(ScopeMode.canGlint(true, true, zooms, views, 0, 1, v -> v == 2));
        assertFalse(ScopeMode.canGlint(true, true, zooms, views, 1, 1, v -> v == 2));
    }

    @Test void opticalLensMayBeViewOneInThirdPartyPacks() {
        assertTrue(ScopeMode.canGlint(true, true, new float[]{3}, new int[]{1}, 0, 1.5, v -> v == 1));
    }

    @Test void cyclingAndSharedViewsFollowTaczSelection() {
        float[] zooms = {5, 25, 2};
        int[] views = {2, 2, 1};
        assertTrue(ScopeMode.canGlint(true, true, zooms, views, 4, 1.5, v -> v == 2));
        assertFalse(ScopeMode.canGlint(true, true, zooms, views, 5, 1.5, v -> v == 2));
    }

    @Test void builtInOpticsNeedNoHybridMetadata() {
        assertTrue(ScopeMode.canGlint(true, false, new float[]{4}, null, 0, 1.5, v -> false));
    }

    @Test void ambiguousOrMissingHybridLensesAreExcluded() {
        assertFalse(ScopeMode.canGlint(true, true, new float[]{4}, null, 0, 1.5, v -> true));
        assertFalse(ScopeMode.canGlint(true, true, new float[]{4}, new int[]{2}, 0, 1.5, v -> false));
        assertFalse(ScopeMode.canGlint(true, true, new float[]{4}, new int[]{0}, 0, 1.5, v -> true));
    }

    @Test void invalidPackValuesDoNotCrashOrCreateGlints() {
        assertFalse(ScopeMode.canGlint(true, false, null, null, 0, 1.5, v -> true));
        assertFalse(ScopeMode.canGlint(true, false, new float[]{}, null, 0, 1.5, v -> true));
        assertFalse(ScopeMode.canGlint(true, false, new float[]{4}, null, -1, 1.5, v -> true));
        for (float zoom : new float[]{Float.NaN, Float.POSITIVE_INFINITY, 1.5f, 1}) {
            assertFalse(ScopeMode.canGlint(true, false, new float[]{zoom}, null, 0, 1.5, v -> true));
        }
    }
}
