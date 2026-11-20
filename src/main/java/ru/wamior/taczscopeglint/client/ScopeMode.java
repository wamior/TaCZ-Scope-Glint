package ru.wamior.taczscopeglint.client;

import java.util.function.IntPredicate;

/** Classifies the selected mode, including hybrids whose red dot has a high ADS zoom. */
public final class ScopeMode {
    public static boolean canGlint(boolean scope, boolean sight, float[] zooms, int[] views,
                                   int mode, double minZoom, IntPredicate opticalView) {
        if (!scope || mode < 0 || zooms == null || zooms.length == 0) return false;
        float zoom = zooms[mode % zooms.length];
        if (!Float.isFinite(zoom) || zoom <= minZoom) return false;
        if (!sight) return true;
        // Older TaCZ has no per-mode lens metadata. An ambiguous hybrid never glints.
        if (views == null || views.length == 0) return false;
        int view = views[mode % views.length];
        return view > 0 && opticalView.test(view);
    }

    private ScopeMode() {
    }
}
