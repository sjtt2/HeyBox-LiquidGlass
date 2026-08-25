package com.hbmod.liquidglass;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

/**
 * Injects a "液态玻璃" row into heybox's main settings list
 * (SettingActivity -> common_user_setting_items, anchored after
 * vg_general_settings). The row opens the in-process settings dialog.
 */
final class SettingsInjector {

    private static final String ROW_TAG = "hb_lg_settings_row";

    private SettingsInjector() {
    }

    static void inject(final Activity activity) {
        try {
            ViewGroup container = find(activity, "common_user_setting_items");
            View anchor = find(activity, "vg_general_settings");
            if (container == null || anchor == null
                    || anchor.getParent() != container) {
                HeyBoxLiquidGlassModule.log(android.util.Log.WARN,
                        "settings row anchors not found");
                return;
            }
            if (container.findViewWithTag(ROW_TAG) != null) {
                return; // already injected
            }

            ClassLoader cl = activity.getClassLoader();
            Class<?> sivCls = Class.forName(
                    "com.max.xiaoheihe.module.account.component.SettingItemView",
                    true, cl);
            final View row = (View) sivCls
                    .getConstructor(android.content.Context.class)
                    .newInstance(activity);
            row.setTag(ROW_TAG);

            Method setTitle = sivCls.getMethod("setTitle", String.class);
            setTitle.invoke(row, "液态玻璃");

            Class<?> typeCls = Class.forName(
                    "com.max.xiaoheihe.module.account.component.SettingItemView$Type",
                    true, cl);
            Object arrow = Enum.valueOf(
                    (Class<? extends Enum>) typeCls.asSubclass(Enum.class), "Arrow");
            Method setRightType = sivCls.getMethod("setRightType", typeCls);
            setRightType.invoke(row, arrow);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SettingsDialog.show(activity);
                }
            });

            int index = container.indexOfChild(anchor) + 1;
            ViewGroup.LayoutParams src = anchor.getLayoutParams();
            ViewGroup.MarginLayoutParams mlp = new ViewGroup.MarginLayoutParams(
                    src.width, src.height);
            if (src instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams sm =
                        (ViewGroup.MarginLayoutParams) src;
                mlp.setMargins(sm.leftMargin, sm.topMargin,
                        sm.rightMargin, sm.bottomMargin);
            }
            container.addView(row, index, mlp);

            HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                    "settings row injected at index " + index);
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("settings row injection failed", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends View> T find(Activity activity, String name) {
        int id = activity.getResources()
                .getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : (T) activity.findViewById(id);
    }
}
