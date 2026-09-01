package com.hbmod.liquidglass;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * User-tunable glass configuration, persisted in the HOST app's own
 * SharedPreferences (same process => no cross-app plumbing needed).
 */
final class GlassConfig {

    private static final String PREFS = "hb_liquid_glass_cfg";

    static volatile int darkColor = 0xFF000000;
    static volatile int darkAlphaPct = 56;
    static volatile int lightColor = 0xFFFFFFFF;
    static volatile int lightAlphaPct = 64;
    static volatile boolean adaptiveChrome = true;
    static volatile boolean immersiveGestureNavigation = true;
    /** 0 = auto (hug content); otherwise explicit height in dp */
    static volatile int barHeightDp = 0;
    /** extra distance between the glass pill and the screen bottom, dp */
    static volatile int barOffsetDp = 16;
    /** tab item width scale, percent of the equal-split default (50-150) */
    static volatile int tabWidthPct = 100;
    static volatile boolean fitVisibleTabs = false;
    /** bottom bar shape: 0 = auto (odd tabs -> circle), 1 = classic centered
     *  plus, 2 = circular plus button on the right */
    static volatile int barLayoutMode = 0;

    private GlassConfig() {
    }

    static int darkTint() {
        return compose(darkColor, darkAlphaPct);
    }

    static int lightTint() {
        return compose(lightColor, lightAlphaPct);
    }

    private static int compose(int rgb, int pct) {
        int a = Math.round(255f * Math.max(5, Math.min(pct, 98)) / 100f);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    static void load(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
            immersiveGestureNavigation = p.getBoolean("immersive", true);
            darkColor = p.getInt("darkColor", darkColor);
            darkAlphaPct = p.getInt("darkAlphaPct", darkAlphaPct);
            lightColor = p.getInt("lightColor", lightColor);
            lightAlphaPct = p.getInt("lightAlphaPct", lightAlphaPct);
            adaptiveChrome = p.getBoolean("adaptiveChrome", true);
            barHeightDp = p.getInt("barHeightDp", barHeightDp);
            barOffsetDp = p.getInt("barOffsetDp", barOffsetDp);
            tabWidthPct = p.getInt("tabWidthPct", tabWidthPct);
            fitVisibleTabs = p.getBoolean("fitVisibleTabs", false);
            barLayoutMode = p.getInt("barLayoutMode", barLayoutMode);
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("config load failed", t);
        }
    }

    static void save(Context ctx) {
        try {
            SharedPreferences.Editor e =
                    ctx.getSharedPreferences(PREFS, 0).edit();
            e.putBoolean("immersive", immersiveGestureNavigation);
            e.putInt("darkColor", darkColor);
            e.putInt("darkAlphaPct", darkAlphaPct);
            e.putInt("lightColor", lightColor);
            e.putInt("lightAlphaPct", lightAlphaPct);
            e.putBoolean("adaptiveChrome", adaptiveChrome);
            e.putInt("barHeightDp", barHeightDp);
            e.putInt("barOffsetDp", barOffsetDp);
            e.putInt("tabWidthPct", tabWidthPct);
            e.putBoolean("fitVisibleTabs", fitVisibleTabs);
            e.putInt("barLayoutMode", barLayoutMode);
            e.apply();
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("config save failed", t);
        }
    }

    static void resetDefaults() {
        darkColor = 0xFF000000;
        darkAlphaPct = 56;
        lightColor = 0xFFFFFFFF;
        lightAlphaPct = 64;
        adaptiveChrome = true;
        barHeightDp = 0;
        barOffsetDp = 16;
        tabWidthPct = 100;
        fitVisibleTabs = false;
        barLayoutMode = 0;
        immersiveGestureNavigation = true;
    }
}
