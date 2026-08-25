package com.hbmod.liquidglass;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.liquidglass.LiquidGlassView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/**
 * On-screen debug panel for theme/tint diagnosis (TEST builds only).
 * Shows live values so state can be verified without logcat.
 */
final class DebugOverlay {

    private static TextView sView;
    private static WeakReference<com.example.liquidglass.LiquidGlassView> sBarRef;
    private static boolean sDirty;

    private DebugOverlay() {
    }

    static void markDirty() {
        sDirty = true;
    }

    static void install(Activity activity, ViewGroup root,
                        com.example.liquidglass.LiquidGlassView bar) {
        try {
            sBarRef = new WeakReference<>(bar);
            if (sView != null) {
                ViewGroup old = (ViewGroup) sView.getParent();
                if (old != null) {
                    old.removeView(sView);
                }
            }
            TextView tv = new TextView(activity);
            tv.setTextColor(Color.GREEN);
            tv.setBackgroundColor(0xD2101010);
            tv.setTextSize(9f);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setPadding(10, 10, 10, 10);
            RelativeLayoutProxy.addTopStart(root, tv);
            sView = tv;
            pump(tv);
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("debug overlay failed", t);
        }
    }

    private static void pump(final TextView tv) {
        if (sRunning()) {
            return;
        }
        setRunning(true);
        final android.os.Handler h = new android.os.Handler(
                android.os.Looper.getMainLooper());
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    View self = tv;
                    if (self == null || !self.isAttachedToWindow()) {
                        return;
                    }
                    StringBuilder sb = new StringBuilder(256);
                    sb.append("-- LG DEBUG --\n");
                    int mode = self.getResources().getConfiguration().uiMode
                            & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                    boolean sysDark =
                            mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                    sb.append("sysNight : ").append(sysDark).append('\n');
                    com.example.liquidglass.LiquidGlassView bar =
                            sBarRef != null ? sBarRef.get() : null;
                    if (bar != null) {
                        sb.append("overLight: ").append(bar.isOverLightBackground())
                                .append('\n');
                    }
                    sb.append("luma     : ")
                            .append(String.format("%.2f",
                                    LiquidGlassInstaller.dbgLastLuma()))
                            .append('\n');
                    sb.append("tintNow  : ")
                            .append(hex(LiquidGlassInstaller.dbgLastTint()))
                            .append('\n');
                    sb.append("tintCalls: ").append(LiquidGlassInstaller.dbgTintCalls())
                            .append('\n');
                    sb.append("lumWrites: ").append(LiquidGlassInstaller.dbgLumWrites())
                            .append('\n');
                    sb.append("innerShdw: ").append(readInnerShadow()).append('\n');
                    sb.append("matLight : ").append(LiquidGlassInstaller.dbgMatLight());
                    tv.setText(sb.toString());
                    if (sDirty) {
                        sDirty = false;
                    }
                } catch (Throwable ignored) {
                } finally {
                    h.postDelayed(this, 400L);
                }
            }
        };
        h.postDelayed(r, 300L);
    }

    private static volatile boolean sRunningFlag;

    private static boolean sRunning() {
        return sRunningFlag;
    }

    private static void setRunning(boolean v) {
        sRunningFlag = v;
    }

    private static String hex(int c) {
        return String.format("#%08X", c);
    }

    private static String readInnerShadow() {
        try {
            java.lang.reflect.Field f = com.example.liquidglass.GlassMaterial.class
                    .getDeclaredField("innerShadow");
            f.setAccessible(true);
            return String.valueOf(f.getFloat(com.example.liquidglass.GlassMaterial.REGULAR));
        } catch (Throwable t) {
            return "?";
        }
    }

    /** tiny shim so this file stays decoupled from RelativeLayout import noise */
    private static final class RelativeLayoutProxy {
        static void addTopStart(ViewGroup parent, View child) {
            android.widget.RelativeLayout.LayoutParams lp =
                    new android.widget.RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
            lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
            int dp8 = Math.round(parent.getResources().getDisplayMetrics().density * 8f);
            lp.setMargins(dp8, dp8, dp8, dp8);
            parent.addView(child, lp);
        }
    }
}
