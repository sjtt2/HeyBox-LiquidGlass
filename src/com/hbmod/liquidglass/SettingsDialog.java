package com.hbmod.liquidglass;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Programmatic settings dialog rendered inside the host app process.
 * Sections: dark tint (color+opacity), light tint (color+opacity),
 * adaptive-chrome toggle, restore defaults. Every change persists and
 * applies immediately.
 */
final class SettingsDialog {

    private static final int[] DARK_PRESETS = {
            0xFF000000, 0xFF1C1C1E, 0xFF2C2C2E, 0xFF10141A
    };
    private static final int[] LIGHT_PRESETS = {
            0xFFFFFFFF, 0xFFF7F5F0, 0xFFEDEDED, 0xFFE8EEF4
    };

    private SettingsDialog() {
    }

    static void show(final Activity act) {
        try {
            float den = act.getResources().getDisplayMetrics().density;
            int pad = Math.round(20f * den);
            int gapS = Math.round(8f * den);
            int gapM = Math.round(14f * den);

            final LinearLayout root = new LinearLayout(act);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(pad, pad, pad, Math.round(10f * den));

            buildTintSection(act, root, "暗色模式底色", DARK_PRESETS, true,
                    den, gapS, gapM);
            buildTintSection(act, root, "亮色模式底色", LIGHT_PRESETS, false,
                    den, gapS, gapM);
            buildLayoutSection(act, root, den, gapS, gapM);

            TextView chromeTitle = new TextView(act);
            chromeTitle.setText("文字图标");
            chromeTitle.setTextSize(13f);
            chromeTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            root.addView(chromeTitle);

            Switch adapt = new Switch(act);
            adapt.setText("根据背景亮度切换黑白");
            adapt.setChecked(GlassConfig.adaptiveChrome);
            adapt.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton b, boolean c) {
                            GlassConfig.adaptiveChrome = c;
                            persistAndRefresh(act);
                        }
                    });
            root.addView(adapt);

            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = Math.round(18f * den);
            Button reset = new Button(act);
            reset.setText("恢复默认");
            reset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    GlassConfig.resetDefaults();
                    persistAndRefresh(act);
                    Object dlg = root.getTag();
                    if (dlg instanceof AlertDialog) {
                        ((AlertDialog) dlg).dismiss();
                    }
                    show(act);
                }
            });
            root.addView(reset, bp);

            AlertDialog dlg = new AlertDialog.Builder(act)
                    .setTitle("液态玻璃设置")
                    .setView(root)
                    .setPositiveButton("完成", null)
                    .show();
            root.setTag(dlg);
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("settings dialog failed", t);
        }
    }

    /** 布局：玻璃条高度（0=自适应）与距屏幕底部的悬浮距离 */
    private static void buildLayoutSection(final Activity act, LinearLayout root,
                                           float den, int gapS, int gapM) {
        root.addView(sectionLabel(act, "布局"));

        final TextView hLabel = new TextView(act);
        hLabel.setTextSize(12f);
        hLabel.setText(heightText(GlassConfig.barHeightDp));
        SeekBar hSeek = new SeekBar(act);
        hSeek.setMax(48); // progress 0 = 自适应, 1..48 -> 52..99dp
        hSeek.setProgress(Math.max(0, Math.min(GlassConfig.barHeightDp - 52, 48)));
        hSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                GlassConfig.barHeightDp = p == 0 ? 0 : p + 51;
                hLabel.setText(heightText(GlassConfig.barHeightDp));
                persistAndRefresh(act);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(hLabel);
        root.addView(hSeek);
        addSpacing(root, gapS);

        final TextView oLabel = new TextView(act);
        oLabel.setTextSize(12f);
        oLabel.setText("距屏幕底部：" + GlassConfig.barOffsetDp + "dp");
        SeekBar oSeek = new SeekBar(act);
        oSeek.setMax(40); // 0..40dp
        oSeek.setProgress(Math.max(0, Math.min(GlassConfig.barOffsetDp, 40)));
        oSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                GlassConfig.barOffsetDp = p;
                oLabel.setText("距屏幕底部：" + p + "dp");
                persistAndRefresh(act);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(oLabel);
        root.addView(oSeek);
        addSpacing(root, gapM);
    }

    private static String heightText(int dp) {
        return dp <= 0 ? "高度：自适应" : "高度：" + dp + "dp";
    }

    private static TextView sectionLabel(android.content.Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return tv;
    }

    private static void addSpacing(LinearLayout root, int px) {
        if (px <= 0) {
            px = 1;
        }
        View sp = new View(root.getContext());
        root.addView(sp, new LinearLayout.LayoutParams(1, px));
    }

    private static void buildTintSection(final Activity act, LinearLayout root,
                                         String title, final int[] presets,
                                         final boolean isDark,
                                         float den, int gapS, int gapM) {
        root.addView(sectionLabel(act, title));

        LinearLayout rowColors = new LinearLayout(act);
        rowColors.setOrientation(LinearLayout.HORIZONTAL);
        rowColors.setPadding(0, gapS, 0, 0);
        final View[] swatches = new View[presets.length];
        for (int i = 0; i < presets.length; i++) {
            final int idx = i;
            View sw = new View(act);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Math.round(38f * den), Math.round(38f * den));
            lp.rightMargin = gapS;
            sw.setLayoutParams(lp);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setPreset(isDark, presets[idx]);
                    persistAndRefresh(act);
                    markSelection(swatches, presets, isDark);
                }
            });
            rowColors.addView(sw);
            swatches[i] = sw;
        }
        root.addView(rowColors);
        markSelection(swatches, presets, isDark);

        final TextView pctLabel = new TextView(act);
        pctLabel.setTextSize(12f);
        pctLabel.setText(currentOpacityText(isDark));
        SeekBar seek = new SeekBar(act);
        seek.setMax(85); // opacity 10..95
        seek.setProgress(opacityToProgress(isDark));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                setOpacity(isDark, p + 10);
                pctLabel.setText(currentOpacityText(isDark));
                persistAndRefresh(act);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(pctLabel);
        root.addView(seek);
        addSpacing(root, gapM);
    }

    private static void markSelection(View[] swatches, int[] presets,
                                      boolean isDark) {
        int current = isDark ? GlassConfig.darkColor : GlassConfig.lightColor;
        for (int i = 0; i < swatches.length; i++) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(presets[i]);
            boolean selected =
                    (presets[i] & 0xFFFFFF) == (current & 0xFFFFFF);
            gd.setStroke(Math.round(selected ? 4f : 1f),
                    selected ? 0xFF2196F3 : 0x44000000);
            swatches[i].setBackground(gd);
        }
    }

    private static void setPreset(boolean dark, int color) {
        if (dark) {
            GlassConfig.darkColor = color;
        } else {
            GlassConfig.lightColor = color;
        }
    }

    private static void setOpacity(boolean dark, int pct) {
        if (dark) {
            GlassConfig.darkAlphaPct = pct;
        } else {
            GlassConfig.lightAlphaPct = pct;
        }
    }

    private static int opacityToProgress(boolean dark) {
        int pct = dark ? GlassConfig.darkAlphaPct : GlassConfig.lightAlphaPct;
        return Math.max(0, Math.min(pct - 10, 85));
    }

    private static String currentOpacityText(boolean dark) {
        int pct = dark ? GlassConfig.darkAlphaPct : GlassConfig.lightAlphaPct;
        return "不透明度：" + pct + "%";
    }

    private static void persistAndRefresh(android.content.Context ctx) {
        GlassConfig.save(ctx);
        LiquidGlassInstaller.refreshGlass();
    }
}
