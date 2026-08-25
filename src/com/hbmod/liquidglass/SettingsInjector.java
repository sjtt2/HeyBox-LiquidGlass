package com.hbmod.liquidglass;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

/**
 * Fallback entry: attaches a long-press listener to heybox's
 * "通用设置" (general settings) row that opens our glass settings dialog.
 * The original click-through behavior is preserved.
 */
final class SettingsInjector {

    private SettingsInjector() {
    }

    static void inject(final Activity activity) {
        try {
            GlassConfig.load(activity);
            View anchor = find(activity, "vg_general_settings");
            if (anchor == null) {
                HeyBoxLiquidGlassModule.log(android.util.Log.WARN,
                        "settings entry vg_general_settings not found");
                return;
            }
            if (MARKED.containsKey(anchor)) {
                return; // already injected
            }
            MARKED.put(anchor, Boolean.TRUE);
            anchor.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    SettingsDialog.show(activity);
                    return true;
                }
            });
            HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                    "settings entry attached (long-press 通用设置)");
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("settings entry failed", t);
        }
    }

    private static final java.util.Map<View, Boolean> MARKED =
            java.util.Collections.synchronizedMap(
                    new java.util.WeakHashMap<View, Boolean>());

    @SuppressWarnings("unchecked")
    private static <T extends View> T find(Activity activity, String name) {
        int id = activity.getResources()
                .getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : (T) activity.findViewById(id);
    }
}
