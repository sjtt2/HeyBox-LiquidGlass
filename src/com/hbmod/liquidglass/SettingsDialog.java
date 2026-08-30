package com.hbmod.liquidglass;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Programmatic settings sheet rendered inside the host app process, styled
 * after heybox's own sheets: rounded bottom corners, grabber, grouped cards
 * on a tinted page, brand-blue accents, day/night aware.
 * Anchored to the top of the screen and capped in height so the glass bar
 * and system navigation stay visible underneath while values are tuned;
 * undimmed and non-modal for the same reason.
 * Sections: dark tint, light tint, layout, system navigation, chrome.
 * Every change persists and applies immediately.
 */
final class SettingsDialog {

    private static final int ACCENT_DAY = 0xFF2B7FFF;
    private static final int ACCENT_NIGHT = 0xFF4A93FF;

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
            final Palette p = new Palette(isNight(act));
            final float den = act.getResources().getDisplayMetrics().density;

            final Dialog dlg = new Dialog(act, p.night
                    ? android.R.style.Theme_Material_Dialog_NoActionBar
                    : android.R.style.Theme_Material_Light_Dialog_NoActionBar);

            LinearLayout sheet = new LinearLayout(act);
            sheet.setOrientation(LinearLayout.VERTICAL);
            sheet.setBackground(sheetBackground(p, den));
            sheet.setPadding(0, statusInset(act), 0, 0);
            sheet.addView(header(act, p, den, dlg));

            final LinearLayout content = new LinearLayout(act);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(den, 16), 0, dp(den, 16), dp(den, 16));

            final int screenH = act.getResources().getDisplayMetrics().heightPixels;
            final int reserve = Math.min(dp(den, 240),
                    Math.round(screenH * 0.42f));
            final int chrome = statusInset(act) + dp(den, 96);
            final int maxHeight = Math.max(dp(den, 200),
                    screenH - reserve - chrome);
            ScrollView scroller = new ScrollView(act) {
                @Override
                protected void onMeasure(int widthSpec, int heightSpec) {
                    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(
                            maxHeight, MeasureSpec.AT_MOST));
                }
            };
            scroller.setVerticalScrollBarEnabled(false);
            scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroller.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            sheet.addView(scroller, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            sheet.addView(grabber(act, p, den));

            buildContent(act, content, p, den);

            dlg.setContentView(sheet);
            Window w = dlg.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setGravity(Gravity.TOP);
                w.setDimAmount(0f);
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
                w.setWindowAnimations(android.R.style.Animation_Dialog);
            }
            dlg.show();
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("settings dialog failed", t);
        }
    }

    private static void buildContent(final Activity act,
                                     final LinearLayout content,
                                     final Palette p, final float den) {
        content.addView(sectionLabel(act, "外观", p, den));
        LinearLayout look = card(act, p, den);
        buildTintGroup(act, look, "暗色模式底色", DARK_PRESETS, true, p, den);
        look.addView(divider(act, p, den));
        buildTintGroup(act, look, "亮色模式底色", LIGHT_PRESETS, false, p, den);
        content.addView(look);

        content.addView(sectionLabel(act, "布局", p, den));
        content.addView(buildLayoutCard(act, p, den));

        content.addView(sectionLabel(act, "系统导航", p, den));
        LinearLayout nav = card(act, p, den);
        nav.addView(switchRow(act, "沉浸式小白条",
                "隐藏系统手势条，页面内容延伸至屏幕底部",
                GlassConfig.immersiveGestureNavigation, p, den,
                new OnToggle() {
                    @Override
                    public void onToggle(boolean checked) {
                        GlassConfig.immersiveGestureNavigation = checked;
                        persistAndRefresh(act);
                    }
                }));
        content.addView(nav);

        content.addView(sectionLabel(act, "文字图标", p, den));
        LinearLayout chrome = card(act, p, den);
        chrome.addView(switchRow(act, "自适应反色",
                "标签文字与图标随背景亮度切换黑白",
                GlassConfig.adaptiveChrome, p, den,
                new OnToggle() {
                    @Override
                    public void onToggle(boolean checked) {
                        GlassConfig.adaptiveChrome = checked;
                        persistAndRefresh(act);
                    }
                }));
        content.addView(chrome);

        TextView reset = new TextView(act);
        reset.setText("恢复默认");
        reset.setTextSize(15f);
        reset.setTextColor(p.danger);
        reset.setGravity(Gravity.CENTER);
        reset.setBackground(clickable(p, dp(den, 12), p.card));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(den, 48));
        rp.topMargin = dp(den, 20);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GlassConfig.resetDefaults();
                persistAndRefresh(act);
                content.removeAllViews();
                buildContent(act, content, p, den);
            }
        });
        content.addView(reset, rp);
    }

    private static LinearLayout buildLayoutCard(final Activity act,
                                                final Palette p, float den) {
        LinearLayout box = card(act, p, den);

        box.addView(sliderRow(act, "玻璃条高度", heightText(GlassConfig.barHeightDp),
                48, Math.max(0, Math.min(GlassConfig.barHeightDp - 51, 48)),
                0, p, den, new OnSlide() {
                    @Override
                    public String onSlide(int progress) {
                        GlassConfig.barHeightDp = progress == 0 ? 0 : progress + 51;
                        persistAndRefresh(act);
                        return heightText(GlassConfig.barHeightDp);
                    }
                }));
        box.addView(divider(act, p, den));
        box.addView(sliderRow(act, "距屏幕底部",
                GlassConfig.barOffsetDp + "dp", 40,
                Math.max(0, Math.min(GlassConfig.barOffsetDp, 40)),
                16, p, den, new OnSlide() {
                    @Override
                    public String onSlide(int progress) {
                        GlassConfig.barOffsetDp = progress;
                        persistAndRefresh(act);
                        return progress + "dp";
                    }
                }));
        box.addView(divider(act, p, den));
        box.addView(sliderRow(act, "Tab 宽度",
                GlassConfig.tabWidthPct + "%", 100,
                Math.max(0, Math.min(GlassConfig.tabWidthPct - 50, 100)),
                50, p, den, new OnSlide() {
                    @Override
                    public String onSlide(int progress) {
                        GlassConfig.tabWidthPct = progress + 50;
                        persistAndRefresh(act);
                        return GlassConfig.tabWidthPct + "%";
                    }
                }));
        box.addView(divider(act, p, den));
        box.addView(modeRow(act, p, den));
        return box;
    }

    private static void buildTintGroup(final Activity act, LinearLayout card,
                                       String title, final int[] presets,
                                       final boolean isDark, final Palette p,
                                       final float den) {
        LinearLayout group = new LinearLayout(act);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(den, 16), dp(den, 14), dp(den, 16), dp(den, 14));

        TextView label = new TextView(act);
        label.setText(title);
        label.setTextSize(15f);
        label.setTextColor(p.textPrimary);
        group.addView(label);

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(den, 12), 0, 0);
        final View[] rings = new View[presets.length];
        for (int i = 0; i < presets.length; i++) {
            final int idx = i;
            FrameLayout holder = new FrameLayout(act);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                    dp(den, 40), dp(den, 40));
            hp.rightMargin = dp(den, 12);

            View fill = new View(act);
            GradientDrawable fd = new GradientDrawable();
            fd.setShape(GradientDrawable.OVAL);
            fd.setColor(presets[i]);
            fd.setStroke(dp(den, 1), p.hairline);
            fill.setBackground(fd);
            holder.addView(fill, new FrameLayout.LayoutParams(
                    dp(den, 28), dp(den, 28), Gravity.CENTER));

            holder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setPreset(isDark, presets[idx]);
                    persistAndRefresh(act);
                    markSelection(rings, presets, isDark, p, den);
                }
            });
            row.addView(holder, hp);
            rings[i] = holder;
        }
        markSelection(rings, presets, isDark, p, den);
        group.addView(row);
        card.addView(group);

        card.addView(divider(act, p, den));
        card.addView(sliderRow(act, "不透明度", currentOpacityText(isDark), 85,
                opacityToProgress(isDark), isDark ? 46 : 54, p, den,
                new OnSlide() {
                    @Override
                    public String onSlide(int progress) {
                        setOpacity(isDark, progress + 10);
                        persistAndRefresh(act);
                        return currentOpacityText(isDark);
                    }
                }));
    }

    private static final int[] MODE_VALUES = {1, 2, 0};

    private static View modeRow(final Activity act, final Palette p,
                                final float den) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(den, 16), dp(den, 14), dp(den, 16), dp(den, 14));
        TextView label = new TextView(act);
        label.setText("底栏形态");
        label.setTextSize(15f);
        label.setTextColor(p.textPrimary);
        row.addView(label);
        LinearLayout chips = new LinearLayout(act);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(den, 10), 0, 0);
        final String[] names = {"经典居中", "右侧圆钮", "自动"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView chip = new TextView(act);
            chip.setText(names[i]);
            chip.setTextSize(13f);
            chip.setTextColor(p.textPrimary);
            chip.setPadding(dp(den, 12), dp(den, 6), dp(den, 12), dp(den, 6));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = dp(den, 8);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    GlassConfig.barLayoutMode = MODE_VALUES[idx];
                    persistAndRefresh(act);
                    markModes(chips, p, den);
                }
            });
            chips.addView(chip, cp);
        }
        markModes(chips, p, den);
        row.addView(chips);
        TextView hint = new TextView(act);
        hint.setText("自动：tab 为奇数时自动切到右侧圆钮形态");
        hint.setTextSize(12f);
        hint.setTextColor(p.textSecondary);
        hint.setPadding(0, dp(den, 6), 0, 0);
        row.addView(hint);
        return row;
    }

    private static void markModes(LinearLayout chips, Palette p, float den) {
        for (int i = 0; i < chips.getChildCount(); i++) {
            View c = chips.getChildAt(i);
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(dp(den, 14));
            boolean selected = MODE_VALUES[i] == GlassConfig.barLayoutMode;
            gd.setColor(selected ? (p.night ? 0x334A93FF : 0x142B7FFF)
                    : Color.TRANSPARENT);
            gd.setStroke(dp(den, 1), selected ? p.accent : p.hairline);
            c.setBackground(gd);
        }
    }

    private interface OnToggle {
        void onToggle(boolean checked);
    }

    private interface OnSlide {
        String onSlide(int progress);
    }

    private static View switchRow(Context ctx, String title, String subtitle,
                                  boolean checked, Palette p, float den,
                                  final OnToggle cb) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(den, 16), dp(den, 14), dp(den, 16), dp(den, 14));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextSize(15f);
        t.setTextColor(p.textPrimary);
        texts.addView(t);
        if (subtitle != null) {
            TextView s = new TextView(ctx);
            s.setText(subtitle);
            s.setTextSize(12f);
            s.setTextColor(p.textSecondary);
            s.setPadding(0, dp(den, 3), 0, 0);
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(ctx);
        sw.setChecked(checked);
        sw.setThumbTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{p.accent, p.thumbOff}));
        sw.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{p.accent, p.trackOff}));
        sw.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean c) {
                        cb.onToggle(c);
                    }
                });
        row.addView(sw);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sw.toggle();
            }
        });
        return row;
    }

    private static View sliderRow(Context ctx, String title, String value,
                                  int max, int progress, final int defProgress,
                                  Palette p, float den, final OnSlide cb) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(den, 16), dp(den, 14), dp(den, 16), dp(den, 10));

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextSize(15f);
        t.setTextColor(p.textPrimary);
        head.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView v = new TextView(ctx);
        v.setText(value);
        v.setTextSize(14f);
        v.setTextColor(p.accent);
        head.addView(v);

        TextView reset = new TextView(ctx);
        reset.setText("重置");
        reset.setTextSize(11f);
        reset.setTextColor(p.textSecondary);
        reset.setPadding(dp(den, 8), dp(den, 3), dp(den, 8), dp(den, 3));
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(den, 10));
        gd.setColor(Color.TRANSPARENT);
        gd.setStroke(dp(den, 1), p.hairline);
        reset.setBackground(gd);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.leftMargin = dp(den, 10);
        reset.setLayoutParams(rp);
        head.addView(reset);
        row.addView(head);

        final SeekBar seek = new SeekBar(ctx);
        seek.setMax(max);
        seek.setProgress(progress);
        seek.setProgressTintList(ColorStateList.valueOf(p.accent));
        seek.setThumbTintList(ColorStateList.valueOf(p.accent));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(p.trackOff));
        seek.setPadding(seek.getPaddingLeft(), dp(den, 8),
                seek.getPaddingRight(), dp(den, 4));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int pos, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                v.setText(cb.onSlide(pos));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        row.addView(seek);

        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v2) {
                v.setText(cb.onSlide(defProgress));
                seek.setProgress(defProgress);
            }
        });
        return row;
    }

    private static void markSelection(View[] rings, int[] presets,
                                      boolean isDark, Palette p, float den) {
        int current = isDark ? GlassConfig.darkColor : GlassConfig.lightColor;
        for (int i = 0; i < rings.length; i++) {
            boolean selected = (presets[i] & 0xFFFFFF) == (current & 0xFFFFFF);
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(Color.TRANSPARENT);
            ring.setStroke(dp(den, 2), selected ? p.accent : Color.TRANSPARENT);
            rings[i].setBackground(ring);
        }
    }

    private static View grabber(Context ctx, Palette p, float den) {
        View bar = new View(ctx);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(den, 2));
        gd.setColor(p.grabber);
        bar.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(den, 36), dp(den, 4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(den, 6);
        lp.bottomMargin = dp(den, 10);
        bar.setLayoutParams(lp);
        return bar;
    }

    private static View header(Context ctx, Palette p, float den,
                               final Dialog dlg) {
        FrameLayout head = new FrameLayout(ctx);
        head.setPadding(dp(den, 16), dp(den, 14), dp(den, 16), dp(den, 12));

        TextView title = new TextView(ctx);
        title.setText("液态玻璃设置");
        title.setTextSize(17f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(p.textPrimary);
        head.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        TextView done = new TextView(ctx);
        done.setText("完成");
        done.setTextSize(15f);
        done.setTextColor(p.accent);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });
        head.addView(done, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL));
        return head;
    }

    private static TextView sectionLabel(Context ctx, String text, Palette p,
                                         float den) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(p.textSecondary);
        tv.setPadding(dp(den, 4), dp(den, 18), 0, dp(den, 8));
        return tv;
    }

    private static LinearLayout card(Context ctx, Palette p, float den) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(den, 12));
        gd.setColor(p.card);
        box.setBackground(gd);
        return box;
    }

    private static View divider(Context ctx, Palette p, float den) {
        View line = new View(ctx);
        line.setBackgroundColor(p.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(den, 0.5f)));
        lp.leftMargin = dp(den, 16);
        line.setLayoutParams(lp);
        return line;
    }

    private static Drawable clickable(Palette p, int radius, int fill) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(radius);
        gd.setColor(fill);
        return new RippleDrawable(ColorStateList.valueOf(p.ripple), gd, null);
    }

    private static GradientDrawable sheetBackground(Palette p, float den) {
        float r = dp(den, 18);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(p.sheet);
        gd.setCornerRadii(new float[]{0f, 0f, 0f, 0f, r, r, r, r});
        return gd;
    }

    private static int statusInset(Activity act) {
        try {
            WindowInsets insets =
                    act.getWindow().getDecorView().getRootWindowInsets();
            if (insets == null) {
                return 0;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return insets.getInsets(WindowInsets.Type.statusBars()).top;
            }
            return insets.getSystemWindowInsetTop();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean isNight(Activity act) {
        int mode = act.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp(float den, float value) {
        return Math.round(value * den);
    }

    private static String heightText(int dp) {
        return dp <= 0 ? "自适应" : dp + "dp";
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
        return pct + "%";
    }

    private static void persistAndRefresh(Context ctx) {
        GlassConfig.save(ctx);
        LiquidGlassInstaller.refreshGlass();
    }

    /** Heybox-flavoured day/night palette for the sheet chrome. */
    private static final class Palette {
        final boolean night;
        final int sheet;
        final int card;
        final int divider;
        final int hairline;
        final int textPrimary;
        final int textSecondary;
        final int accent;
        final int danger;
        final int grabber;
        final int ripple;
        final int trackOff;
        final int thumbOff;

        Palette(boolean night) {
            this.night = night;
            if (night) {
                sheet = 0xFF1A1B1F;
                card = 0xFF25262B;
                divider = 0xFF32333A;
                hairline = 0x33FFFFFF;
                textPrimary = 0xFFECEDEF;
                textSecondary = 0xFF8B9099;
                accent = ACCENT_NIGHT;
                danger = 0xFFFF6B6B;
                grabber = 0xFF3C3D44;
                ripple = 0x1FFFFFFF;
                trackOff = 0xFF4A4B52;
                thumbOff = 0xFFBFC2C7;
            } else {
                sheet = 0xFFFFFFFF;
                card = 0xFFF6F7F9;
                divider = 0xFFEBECEF;
                hairline = 0x1F000000;
                textPrimary = 0xFF1A1B1F;
                textSecondary = 0xFF8A8F99;
                accent = ACCENT_DAY;
                danger = 0xFFF5525B;
                grabber = 0xFFDBDDE1;
                ripple = 0x14000000;
                trackOff = 0xFFC9CCD2;
                thumbOff = 0xFFFFFFFF;
            }
        }
    }
}
