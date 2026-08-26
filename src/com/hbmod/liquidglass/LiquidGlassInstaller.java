package com.hbmod.liquidglass;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

final class LiquidGlassInstaller {

    private static final String TAG = "HeyBoxLiquidGlass";

    private static final String ID_ROOT = "vg_main_root";
    private static final String ID_BAR = "rg_main";
    private static final String ID_TIPS = "vg_tips";
    private static final String ID_MID_TAB = "vg_mid_tab";
    private static final String ID_CONTENT = "fl_container";
    private static final String ID_VIDEO_FULL = "vg_fullscreen_video_container";

    private LiquidGlassInstaller() {
    }

    static void scheduleInstall(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                ViewGroup root = findViewByName(activity, ID_ROOT);
                if (root == null) {
                    HeyBoxLiquidGlassModule.log(android.util.Log.WARN,
                            "root view " + ID_ROOT + " not found, retry in 200ms");
                    decor.postDelayed(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            ViewGroup r = findViewByName(activity, ID_ROOT);
                            if (r != null) {
                                install(activity, r);
                            }
                        }
                    }, 200L);
                    return;
                }
                install(activity, root);
            } catch (Throwable t) {
                HeyBoxLiquidGlassModule.logErr("scheduleInstall failed", t);
            }
        });
    }

    private static void install(Activity activity, ViewGroup root) {
        if (root.findViewWithTag(LiquidGlassHostLayout.GLASS_TAG) != null) {
            return;
        }
        ViewGroup bar = findViewByName(activity, ID_BAR);
        if (bar == null || bar.getParent() != root) {
            HeyBoxLiquidGlassModule.log(android.util.Log.WARN,
                    "nav bar " + ID_BAR + " not found (bar=" + (bar != null)
                            + ", parentMatch="
                            + (bar != null && bar.getParent() == root) + ")");
            return;
        }
        ViewGroup tips = findViewByName(activity, ID_TIPS);
        View midTab = findViewByName(activity, ID_MID_TAB);
        ViewGroup content = findViewByName(activity, ID_CONTENT);
        View videoFull = findViewByName(activity, ID_VIDEO_FULL);

        Context ctx = root.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        int sideMargin = Math.round(density * 10f);

        RelativeLayout.LayoutParams barLp = (RelativeLayout.LayoutParams) bar.getLayoutParams();
        RelativeLayout.LayoutParams hostLp = new RelativeLayout.LayoutParams(
                barLp.width, RelativeLayout.LayoutParams.WRAP_CONTENT);
        copyMargins(barLp, hostLp);
        hostLp.leftMargin += sideMargin;
        hostLp.rightMargin += sideMargin;
        hostLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        int[] hostRules = hostLp.getRules();
        for (int i = 0; i < hostRules.length; i++) {
            if (i != RelativeLayout.ALIGN_PARENT_BOTTOM) {
                hostRules[i] = 0;
            }
        }

        hideLegacyShadow(root, bar.getId());

        int barHeightSpec = bar.getLayoutParams().height;
        int navPad = computeNavInsetPadding(activity, root, bar);
        int origBarPadBottom = bar.getPaddingBottom();

        LiquidGlassHostLayout host = new LiquidGlassHostLayout(ctx, root, bar);

        root.removeView(bar);
        if (tips != null && tips.getParent() == root) {
            root.removeView(tips);
        }
        if (midTab != null && midTab.getParent() == root) {
            root.removeView(midTab);
        }

        int insertAt = videoFull != null && videoFull.getParent() == root
                ? root.indexOfChild(videoFull)
                : root.getChildCount();
        root.addView(host, insertAt, hostLp);

        FrameLayout.LayoutParams barFlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                barHeightSpec > 0 ? barHeightSpec : ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP | android.view.Gravity.FILL_HORIZONTAL);
        bar.setBackground(null);
        bar.setPadding(
                bar.getPaddingLeft(), bar.getPaddingTop(),
                bar.getPaddingRight(), 0);
        host.addView(bar, 0, barFlp);

        if (tips != null) {
            FrameLayout.LayoutParams tipsFlp = new FrameLayout.LayoutParams(
                    tips.getLayoutParams().width,
                    tips.getLayoutParams().height,
                    android.view.Gravity.TOP | android.view.Gravity.FILL_HORIZONTAL);
            host.addView(tips, 1, tipsFlp);
        }
        if (midTab != null) {
            RelativeLayout.LayoutParams midLp =
                    (RelativeLayout.LayoutParams) midTab.getLayoutParams();
            FrameLayout.LayoutParams midFlp = new FrameLayout.LayoutParams(
                    midLp.width, midLp.height,
                    android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.BOTTOM);
            midFlp.leftMargin = midLp.leftMargin;
            midFlp.topMargin = midLp.topMargin;
            midFlp.rightMargin = midLp.rightMargin;
            midFlp.bottomMargin = midLp.bottomMargin;
            host.addView(midTab, host.getChildCount(), midFlp);
        }

        host.setPadding(host.getPaddingLeft(), host.getPaddingTop(),
                host.getPaddingRight(), origBarPadBottom + navPad);

        attachQmRenderer(activity, host, bar, tips, midTab, content, barHeightSpec, navPad);

        if (content != null && content.getParent() == root) {
            RelativeLayout.LayoutParams clp =
                    (RelativeLayout.LayoutParams) content.getLayoutParams();
            clp.getRules()[RelativeLayout.ABOVE] = 0;
            content.setLayoutParams(clp);
        }

        root.requestLayout();
        root.invalidate();

        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    private boolean attached;
                    @Override
                    public void onGlobalLayout() {
                        if (attached) {
                            return;
                        }
                        attached = true;
                        root.getViewTreeObserver()
                                .removeOnGlobalLayoutListener(this);
                        host.attach();
                        if (!sTabBarActive) {
                            setupTabPopAnimation(bar);
                        }
                        HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                                "liquid glass installed: hostW=" + host.getWidth()
                                        + " hostH=" + host.getHeight()
                                        + " navPad=" + navPad
                                        + " barH=" + bar.getHeight()
                                        + " children=" + host.getChildCount());
                    }
                });
    }

    /** Active renderer switch. QmDeve track frozen per user decision. */
    private static final boolean USE_QWEA0_RENDERER = true;
    /** QWEA0 mode: replace the visible RadioGroup with LiquidGlassTabBar
     *  (built-in glass droplet selection animation). */
    private static final boolean USE_QWEA0_TABBAR = true;
    /** Weight of the empty center column reserved for the publish button,
     *  relative to a normal tab's weight of 1. */
    private static final float CENTER_GAP_WEIGHT = 1.3f;
    /** Set true to show the on-screen diagnosis HUD (test builds). */
    private static final boolean USE_DEBUG_HUD = false;
    private static volatile boolean sTabBarActive;

    /**
     * API 33+: mounts the vendored QmDeve AndroidLiquidGlassView renderer as
     * child index 0 of the host (beneath bar/tips/mid-tab), sampling fl_container.
     * Failure falls back to the internal legacy frost path.
     */
    private static void attachQmRenderer(Activity activity, ViewGroup host,
                                         ViewGroup bar, ViewGroup tips, View midTab,
                                         ViewGroup content, int barHeightSpec, int navPad) {
        if (Build.VERSION.SDK_INT < 33 || content == null) {
            return;
        }
        if (USE_QWEA0_RENDERER) {
            if (USE_QWEA0_TABBAR && bar instanceof android.widget.RadioGroup) {
                attachQwea0TabBar(activity, host,
                        (android.widget.RadioGroup) bar, tips, midTab,
                        content, barHeightSpec, navPad);
            } else {
                attachQwea0Renderer(activity, host, content, barHeightSpec);
            }
            return;
        }
        try {
            float density = host.getResources().getDisplayMetrics().density;
            Boolean detected = com.hbmod.liquidglass.LiquidGlassHostLayout
                    .detectDarkFromText(bar);
            final boolean dark = detected != null ? detected : false;

            final com.qmdeve.liquidglass.Config qm = new com.qmdeve.liquidglass.Config();
            applyQmParams(qm, dark, density);

            final com.qmdeve.liquidglass.LiquidGlass glass =
                    new com.qmdeve.liquidglass.LiquidGlass(activity, qm);
            host.addView(glass, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            glass.init(content);

            ((LiquidGlassHostLayout) host).setGlassTuner(
                    new LiquidGlassHostLayout.GlassTuner() {
                        @Override
                        public void onSize(int w, int h, float cornerRadius) {
                            qm.WIDTH = w;
                            qm.HEIGHT = h;
                            qm.CORNER_RADIUS_PX = cornerRadius;
                            glass.updateParameters();
                        }

                        @Override
                        public void onTheme(boolean d) {
                            applyQmTint(qm, d);
                            glass.updateParameters();
                        }
                    });

            // Critical: sync initial size NOW. The host was laid out before the
            // renderer was attached, so onSizeChanged will NOT fire again.
            // Without this, WIDTH/HEIGHT stay 0 and the shader produces NaN -> black.
            int hw = host.getWidth(), hh = host.getHeight();
            if (hw > 0 && hh > 0) {
                qm.WIDTH = hw;
                qm.HEIGHT = hh;
                qm.CORNER_RADIUS_PX = Math.min(hh * 0.46f, 30f * density);
                glass.updateParameters();
            }

            HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                    "renderer=AndroidLiquidGlassView (vendored QmDeve, refraction+dispersion)");
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("vendored renderer unavailable, frost fallback", t);
        }
    }

    /**
     * QWEA0/Liquid-Glass-Android renderer: FrameLayout subclass, GPU lens
     * pipeline (SDF refraction + dispersion + sensor specular + adaptive tint).
     * Samples backdropSource directly via RenderNode recording 閳?no bitmaps.
     */
    static void injectSettingsRow(Activity activity) {
        try {
            GlassConfig.load(activity);
            SettingsInjector.inject(activity);
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("injectSettingsRow failed", t);
        }
    }

    /** Re-applies current config to the live glass views (settings dialog). */
    static void refreshGlass() {
        try {
            applyBarGeometry();
            View bar = sTabBarRef.get();
            if (bar instanceof ViewGroup) {
                bar.invalidate();
                View droplet = findDroplet((ViewGroup) bar);
                if (droplet != null) {
                    droplet.invalidate();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static View findDroplet(ViewGroup tabBar) {
        for (int i = 0; i < tabBar.getChildCount(); i++) {
            View c = tabBar.getChildAt(i);
            if (c instanceof com.example.liquidglass.LiquidGlassView
                    && !(c instanceof com.example.liquidglass.LiquidGlassTabBar)) {
                return c;
            }
        }
        return null;
    }

    private static void attachQwea0Renderer(Activity activity, ViewGroup host,
                                            ViewGroup content, int barHeightSpec) {
        try {
            float density = host.getResources().getDisplayMetrics().density;
            com.example.liquidglass.LiquidGlassView glass =
                    new com.example.liquidglass.LiquidGlassView(activity, null, 0);
            glass.setCornerRadius(999f);
            glass.setEnableDynamicBackground(true);
            glass.setBackdropSource(content);
            glass.setMaterial(com.example.liquidglass.GlassMaterial.REGULAR);
            glass.setRefractionHeight(60f * density);
            glass.setBevelWidth(16f * density);
            glass.setDispersionStrength(0.12f);
            glass.setEnableSensorHighlight(true);
            glass.setEnableAdaptiveTint(true);

            host.addView(glass, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    barHeightSpec > 0 ? barHeightSpec : ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP | android.view.Gravity.FILL_HORIZONTAL));

            HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                    "renderer=QWEA0 LiquidGlassView (lens+dispersion+sensor specular)");
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("qwea0 renderer unavailable, frost fallback", t);
        }
    }

    /**
     * QWEA0 LiquidGlassTabBar: replaces the visible RadioGroup entirely.
     * The tab bar IS a LiquidGlassView (own refraction/dispersion/specular),
     * and its selection indicator is a glass droplet that slides/stretches
     * with liquid animation. The original RadioGroup stays in the tree but
     * invisible, so the app's own tab-switching logic keeps working:
     *   tabBar tap  -> rb.performClick() -> app switches fragment
     *   app check   -> our RadioGroup listener wrapper -> tabBar.setSelectedIndex
     */
    private static void attachQwea0TabBar(Activity activity, ViewGroup host,
                                          android.widget.RadioGroup bar,
                                          ViewGroup tips, View midTab,
                                          ViewGroup content, int barHeightSpec,
                                          int navPad) {
        try {
            GlassConfig.load(activity);
            sHostRef = host;
            sDensity = host.getResources().getDisplayMetrics().density;
            // tab-bar mode: offset 0 must be FLUSH to the physical screen
            // bottom, so strip the navigation-inset padding that the generic
            // path added (slider adds on top of this zero baseline)
            int flushPad = Math.max(host.getPaddingBottom() - navPad, 0);
            host.setPadding(host.getPaddingLeft(), host.getPaddingTop(),
                    host.getPaddingRight(), flushPad);
            sBasePadBottom = flushPad;
            sCenterRefStatic = null;
            final com.example.liquidglass.LiquidGlassTabBar tabBar =
                    new com.example.liquidglass.LiquidGlassTabBar(activity, null, 0);
            sTabBarRef = new java.lang.ref.WeakReference<>(tabBar);
            float density = host.getResources().getDisplayMetrics().density;

            java.util.List<com.example.liquidglass.LiquidGlassTabBar.TabItem> items =
                    new java.util.ArrayList<>();
            final java.util.List<android.widget.RadioButton> visibleButtons =
                    new java.util.ArrayList<>();
            for (int i = 0; i < bar.getChildCount(); i++) {
                View child = bar.getChildAt(i);
                if (child instanceof android.widget.RadioButton
                        && child.getVisibility() == View.VISIBLE) {
                    android.widget.RadioButton rb = (android.widget.RadioButton) child;
                    CharSequence title = rb.getText();
                    Drawable icon = rb.getCompoundDrawables()[1];
                    if (icon != null) {
                        icon.mutate();
                    }
                    items.add(new com.example.liquidglass.LiquidGlassTabBar.TabItem(
                            title, icon));
                    visibleButtons.add(rb);
                }
            }
            if (items.isEmpty()) {
                HeyBoxLiquidGlassModule.log(android.util.Log.WARN,
                        "tabbar: no radio buttons found");
                return;
            }
            tabBar.setTabs(items);
            insertCenterGap(activity, tabBar);
            tabBar.setCornerRadius(999f);
            // Theme following is driven by us (app skin probe), not by the
            // library's built-in luminance meter which is unreliable here.
            tabBar.setEnableAdaptiveTint(false);
            tabBar.setEnableDynamicBackground(true);
            tabBar.setBackdropSource(content);
            tabBar.setMaterial(com.example.liquidglass.GlassMaterial.REGULAR);
            tabBar.setRefractionHeight(60f * density);
            tabBar.setBevelWidth(16f * density);
            tabBar.setDispersionStrength(0.12f);
            tabBar.setEnableSensorHighlight(true);
            // Per-pixel adaptive tint OFF: it counter-tints the glass body
            // (dark on bright backdrops) and bypasses our hook. The body is
            // themed via the currentTintColor override instead; label colors
            // are driven by OUR standalone BackdropLuminanceMeter below.
            tabBar.setEnableAdaptiveTint(false);
            installTintOverride();

            // bar -> app: forward taps to the hidden radio buttons
            tabBar.setOnTabSelected(new kotlin.jvm.functions.Function1<Integer, kotlin.Unit>() {
                @Override
                public kotlin.Unit invoke(Integer index) {
                    try {
                        if (index == null || index < 0 || index >= visibleButtons.size()) {
                            return kotlin.Unit.INSTANCE;
                        }
                        android.widget.RadioButton rb = visibleButtons.get(index);
                        if (rb != null && !rb.isChecked()) {
                            rb.performClick();
                        }
                    } catch (Throwable t) {
                        HeyBoxLiquidGlassModule.logErr("tabbar->app failed", t);
                    }
                    return kotlin.Unit.INSTANCE;
                }
            });

            // initial selection from current checked state
            int checked = bar.getCheckedRadioButtonId();
            for (int i = 0; i < visibleButtons.size(); i++) {
                if (visibleButtons.get(i).getId() == checked) {
                    tabBar.setSelectedIndex(i);
                    break;
                }
            }

            // initial theme: the app resolves day/night via standard
            // values-night qualifiers, so the activity uiMode IS the truth.
            // (text-color probing proved unreliable across skin updates)
            final boolean startDark = isSystemNight(activity);
            Boolean skinProbe = com.hbmod.liquidglass.LiquidGlassHostLayout
                    .detectDarkFromText(bar);
            applyTabBarOverLight(tabBar, startDark);
            HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                    "tabbar theme: dark=" + startDark
                            + " source=uiMode probe=" + skinProbe);

            // Height adapts to the tab content (icon+label stack); the pill hugs
            // its rows instead of forcing a taller fixed box.
            host.addView(tabBar, host.getChildCount(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP | android.view.Gravity.FILL_HORIZONTAL));

            // Publish (+) button: the middle radio slot is hidden in this app,
            // so reserve the center gap and host vg_mid_tab there 鈥?OUTSIDE the
            // tab bar, because LiquidGlassTabBar intercepts ALL touches and
            // would swallow the button's clicks.
            View centerHost = null;
            if (midTab != null && midTab.getParent() == host) {
                host.removeView(midTab);
                midTab.setVisibility(View.VISIBLE);

                final FrameLayout center =
                        new FrameLayout(activity);
                center.setClickable(true);
                // the CardView keeps its natural wrapped size & rounded
                // corners; the transparent center container covers the whole
                // gap so taps anywhere there reach the publish action
                center.addView(midTab, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.Gravity.CENTER));
                center.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        midTab.performClick();
                    }
                });
                center.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        SettingsDialog.show(activity);
                        return true;
                    }
                });
                host.addView(center, host.getChildCount());
                centerHost = center;
            }
            final View centerRef = centerHost;
            sCenterRefStatic = centerHost;

            // keep red-dot tips above everything else
            if (tips != null && tips.getParent() == host) {
                host.removeView(tips);
                host.addView(tips, host.getChildCount());
            }

            // size & position the center button over the spacer column once
            // the tab bar has been laid out
            placeCenterNow(host, tabBar, centerRef, 0);
            applyBarGeometry();

            // Label colors follow the REAL backdrop via a standalone luminance
            // meter (library's own class, decoupled from its view pipeline).
            final java.util.concurrent.atomic.AtomicReference<
                    com.example.liquidglass.BackdropLuminanceMeter> meterHolder =
                    new java.util.concurrent.atomic.AtomicReference<>();
            com.example.liquidglass.BackdropLuminanceMeter meter =
                    new com.example.liquidglass.BackdropLuminanceMeter(tabBar,
                            new kotlin.jvm.functions.Function1<Float, kotlin.Unit>() {
                                @Override
                                public kotlin.Unit invoke(Float luma) {
                                    sDbgLastLuma = luma == null ? 0f : luma;
                                    try {
                                        if (!GlassConfig.adaptiveChrome) {
                                            // user disabled backdrop flipping:
                                            // pin labels to the app theme
                                            boolean uiDark = tabBar.getResources()
                                                    .getConfiguration().uiMode
                                                    % 2 == 1;
                                            if (sChromeLight != !uiDark
                                                    || sChromeForced) {
                                                sChromeForced = true;
                                                sChromeLight = !uiDark;
                                                final boolean d = uiDark;
                                                tabBar.post(() -> {
                                                    try {
                                                        applyTabBarOverLight(
                                                                tabBar, d);
                                                    } catch (Throwable ignored) {
                                                    }
                                                });
                                            }
                                            return kotlin.Unit.INSTANCE;
                                        }
                                        sChromeForced = false;
                                        com.example.liquidglass.BackdropLuminanceMeter
                                                m0 = meterHolder.get();
                                        boolean overLight =
                                                m0 != null && m0.isOverLight();
                                        if (overLight != sChromeLight) {
                                            sChromeLight = overLight;
                                            final boolean flip = overLight;
                                            tabBar.post(() -> {
                                                try {
                                                    applyTabBarOverLight(tabBar,
                                                            !flip);
                                                    HeyBoxLiquidGlassModule.log(
                                                            android.util.Log.INFO,
                                                            "backdrop flip: overLight="
                                                                    + flip);
                                                } catch (Throwable ignored) {
                                                }
                                            });
                                        }
                                    } catch (Throwable ignored) {
                                    }
                                    return kotlin.Unit.INSTANCE;
                                }
                            });
            meterHolder.set(meter);
            tabBar.addOnAttachStateChangeListener(
                    new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            try {
                                meter.start();
                            } catch (Throwable ignored) {
                            }
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {
                            try {
                                meter.stop();
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            // the bar may ALREADY be attached (host was laid out first),
            // so the listener above would never fire - start directly
            if (tabBar.isAttachedToWindow()) {
                try {
                    meter.start();
                    HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                            "backdrop meter started (already attached)");
                } catch (Throwable ignored) {
                }
            }

            // hide the original radio row visually (keeps state mechanics alive)
            bar.setVisibility(View.INVISIBLE);

            // app -> bar: extend the existing checked-listener wrapper
            setupTabSelectionSync(bar, tabBar, visibleButtons);

            // uiMode push seeds the initial state only; after the luminance
            // meter warms up it owns chrome colors (per-backdrop adaptation).
            final com.example.liquidglass.LiquidGlassTabBar tabBarRef = tabBar;
            ((LiquidGlassHostLayout) host).setGlassTuner(
                    new LiquidGlassHostLayout.GlassTuner() {
                        @Override
                        public void onSize(int w, int h, float cornerRadius) {
                        }

                        @Override
                        public void onTheme(boolean dark) {
                            HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                                    "app theme changed: dark=" + dark
                                            + " (backdrop meter now drives chrome)");
                        }
                    });

            HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                    "renderer=QWEA0 LiquidGlassTabBar (glass droplet selection)");
            sTabBarActive = true;
            if (USE_DEBUG_HUD
                    && tabBar instanceof com.example.liquidglass.LiquidGlassView) {
                DebugOverlay.install(activity, host,
                        (com.example.liquidglass.LiquidGlassView) tabBar);
            }
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("qwea0 tabbar unavailable", t);
        }
    }

    /**
     * Pushes theme into the QWEA0 tab bar FOREGROUND only: label/icon colors
     * flip between white (dark bar) and black (light bar). Glass body keeps
     * the constant white REGULAR tint in both themes.
     *
     * Primary path writes colors directly into the library's TabHolder views;
     * the private updateTabStyles() is also attempted as a bonus refresh.
     */
    private static void applyTabBarOverLight(
            com.example.liquidglass.LiquidGlassTabBar tabBar, boolean darkBar) {
        int selectedColor = darkBar ? 0xFFFFFFFF : 0xE6000000;
        int normalColor = darkBar ? 0xB8FFFFFF : 0x8C000000;
        int applied = 0;
        try {
            java.lang.reflect.Field tf = com.example.liquidglass.LiquidGlassTabBar.class
                    .getDeclaredField("tabs");
            tf.setAccessible(true);
            Object tabsObj = tf.get(tabBar);
            if (tabsObj instanceof java.util.List) {
                int selIdx = tabBar.getSelectedIndex();
                java.util.List<?> tabs = (java.util.List<?>) tabsObj;
                for (int i = 0; i < tabs.size(); i++) {
                    Object holder = tabs.get(i);
                    if (holder == null) {
                        continue;
                    }
                    int color = i == selIdx ? selectedColor : normalColor;
                    for (java.lang.reflect.Field hf : holder.getClass().getDeclaredFields()) {
                        hf.setAccessible(true);
                        Object val = hf.get(holder);
                        if (val instanceof android.widget.TextView) {
                            ((android.widget.TextView) val)
                                    .setTextColor(color);
                            applied++;
                        } else if (val instanceof android.widget.ImageView) {
                            ((android.widget.ImageView) val)
                                    .setImageTintList(
                                            android.content.res.ColorStateList
                                                    .valueOf(color));
                            applied++;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("direct chrome recolor failed", t);
        }
        try {
            java.lang.reflect.Field af = com.example.liquidglass.LiquidGlassTabBar.class
                    .getDeclaredField("overLightAppearance");
            af.setAccessible(true);
            af.setBoolean(tabBar, !darkBar);

            java.lang.reflect.Method uts = com.example.liquidglass.LiquidGlassTabBar.class
                    .getDeclaredMethod("updateTabStyles");
            uts.setAccessible(true);
            uts.invoke(tabBar);
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.log(android.util.Log.WARN,
                    "updateTabStyles refresh failed: " + t);
        }
        tabBar.invalidate();
        HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                "chrome theme applied: dark=" + darkBar + " targets=" + applied);
    }

    private static boolean isSystemNight(Activity activity) {
        int mode = activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Wraps the RadioGroup's existing OnCheckedChangeListener (via reflection)
     * so programmatic/checked changes also drive the glass droplet.
     */
    private static void insertCenterGap(Activity activity, ViewGroup tabBar) {
        try {
            if (tabBar.getChildCount() == 0) {
                return;
            }
            View row = tabBar.getChildAt(0);
            if (!(row instanceof android.widget.LinearLayout)) {
                return;
            }
            android.widget.LinearLayout ll = (android.widget.LinearLayout) row;
            int count = ll.getChildCount();
            if (count < 2) {
                return;
            }
            android.widget.Space spacer = new android.widget.Space(activity);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.MATCH_PARENT, CENTER_GAP_WEIGHT);
            ll.addView(spacer, count / 2, lp);
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("center gap failed", t);
        }
    }

    /** Places the center button host over the spacer column (retryable). */
    private static void placeCenterNow(ViewGroup host, ViewGroup tabBar,
                                       View center, int attempt) {
        if (center == null || attempt > 10) {
            return;
        }
        try {
            View row = tabBar.getChildAt(0);
            if (!(row instanceof android.widget.LinearLayout)) {
                return;
            }
            android.widget.LinearLayout ll = (android.widget.LinearLayout) row;
            int n = ll.getChildCount();
            if (n < 3) {
                return;
            }
            View spacer = ll.getChildAt(n / 2);
            if (spacer.getWidth() == 0) {
                final ViewGroup h2 = host, t2 = tabBar;
                final View c2 = center;
                final int a2 = attempt + 1;
                center.post(() -> placeCenterNow(h2, t2, c2, a2));
                return;
            }
            int left = tabBar.getLeft() + row.getLeft() + spacer.getLeft();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    spacer.getWidth(), tabBar.getHeight(),
                    android.view.Gravity.TOP | android.view.Gravity.START);
            lp.leftMargin = left;
            lp.topMargin = tabBar.getTop();
            center.setLayoutParams(lp);
        } catch (Throwable ignored) {
        }
    }

    /** Applies user height/offset config to the live bar (settings dialog). */
    static void applyBarGeometry() {
        try {
            View barV = sTabBarRef.get();
            ViewGroup host = sHostRef;
            View center = sCenterRefStatic;
            if (!(barV instanceof ViewGroup) || host == null
                    || !(barV.getLayoutParams()
                            instanceof FrameLayout.LayoutParams)) {
                return;
            }
            float den = sDensity > 0 ? sDensity : 3f;
            int hDp = GlassConfig.barHeightDp;
            FrameLayout.LayoutParams blp =
                    (FrameLayout.LayoutParams) barV.getLayoutParams();
            blp.height = hDp <= 0
                    ? ViewGroup.LayoutParams.WRAP_CONTENT
                    : Math.round(hDp * den);
            barV.setLayoutParams(blp);

            int off = Math.max(0, GlassConfig.barOffsetDp);
            host.setPadding(host.getPaddingLeft(), host.getPaddingTop(),
                    host.getPaddingRight(), sBasePadBottom
                            + Math.round(off * den));

            host.requestLayout();
            final ViewGroup h2 = host;
            final ViewGroup b2 = (ViewGroup) barV;
            host.post(() -> placeCenterNow(h2, b2, center, 0));
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("applyBarGeometry failed", t);
        }
    }

    private static void setupTabSelectionSync(final android.widget.RadioGroup bar,
                                              final com.example.liquidglass.LiquidGlassTabBar tabBar,
                                              final java.util.List<android.widget.RadioButton> order) {
        try {
            java.lang.reflect.Field f = android.widget.RadioGroup.class
                    .getDeclaredField("mOnCheckedChangeListener");
            f.setAccessible(true);
            final Object original = f.get(bar);
            bar.setOnCheckedChangeListener(new android.widget.RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.RadioGroup group, int checkedId) {
                    if (original instanceof android.widget.RadioGroup.OnCheckedChangeListener) {
                        ((android.widget.RadioGroup.OnCheckedChangeListener) original)
                                .onCheckedChanged(group, checkedId);
                    }
                    try {
                        for (int i = 0; i < order.size(); i++) {
                            if (order.get(i).getId() == checkedId
                                    && tabBar.getSelectedIndex() != i) {
                                tabBar.setSelectedIndex(i);
                                break;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.log(android.util.Log.WARN,
                    "tab selection sync unavailable: " + t);
        }
    }

    /** Installed once per process: theme-driven glass body tint. */
    private static volatile boolean sTintHookInstalled;
    private static volatile boolean sLastMaterialLight;
    private static volatile int sDbgTintCalls;
    private static volatile int sDbgLumWrites;
    private static volatile int sDbgLastTint;
    private static volatile float sDbgLastLuma;
    private static volatile boolean sChromeLight;
    private static volatile boolean sChromeForced;
    private static volatile ViewGroup sHostRef;
    private static volatile View sCenterRefStatic;
    private static volatile float sDensity;
    private static int sBasePadBottom;
    private static final java.lang.ref.WeakReference<View> EMPTY_BAR_REF =
            new java.lang.ref.WeakReference<>(null);
    private static volatile java.lang.ref.WeakReference<View> sTabBarRef = EMPTY_BAR_REF;

    static int dbgTintCalls() {
        return sDbgTintCalls;
    }

    static int dbgLumWrites() {
        return sDbgLumWrites;
    }

    static int dbgLastTint() {
        return sDbgLastTint;
    }

    static boolean dbgMatLight() {
        return sLastMaterialLight;
    }

    static float dbgLastLuma() {
        return sDbgLastLuma;
    }

    /**
     * Overrides the renderer's currentTintColor() so the glass BODY follows the
     * app theme instead of counter-tinting against the backdrop:
     *   dark theme -> subtle white tint (original REGULAR look)
     *   light theme -> high-opacity white frosted tint
     * Chrome (icon/label) adaptation from the luminance meter is untouched.
     */
    private static void installTintOverride() {
        if (sTintHookInstalled) {
            return;
        }
        sTintHookInstalled = true;
        try {
            java.lang.reflect.Method m = com.example.liquidglass.LiquidGlassView.class
                    .getDeclaredMethod("currentTintColor");
            HeyBoxLiquidGlassModule.hookExecutable(m, chain -> {
                int tint = 0x30FFFFFF;
                try {
                    Object thiz = chain.getThisObject();
                    int mode = 0;
                    boolean isBarView = false;
                    if (thiz instanceof View) {
                        mode = ((View) thiz).getResources()
                                .getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                        // the droplet stacks ON TOP of the already-tinted bar,
                        // so it gets a lighter wash to avoid double-weighting
                        isBarView = thiz == sTabBarRef.get();
                    }
                    boolean light =
                            mode != android.content.res.Configuration.UI_MODE_NIGHT_YES;
                    if (light) {
                        tint = GlassConfig.lightTint();
                    } else {
                        tint = GlassConfig.darkTint();
                    }
                    if (!isBarView) {
                        // lighten stacked layers to ~60% of the bar's opacity
                        int a = tint >>> 24;
                        int na = Math.round(a * 0.6f);
                        tint = (na << 24) | (tint & 0x00FFFFFF);
                    }
                } catch (Throwable ignored) {
                }
                sDbgTintCalls++;
                sDbgLastTint = tint;
                return tint;
            });
            HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                    "currentTintColor override hooked (theme body)");
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("currentTintColor hook failed", t);
        }
    }

    private static void applyQmParams(com.qmdeve.liquidglass.Config cfg,
                                      boolean dark, float density) {
        cfg.REFRACTION_HEIGHT = 20f * density;
        cfg.REFRACTION_OFFSET = -40f * density;
        cfg.DISPERSION = 0.35f;
        cfg.BLUR_RADIUS = 1.2f;
        cfg.DEPTH_EFFECT = 0.3f;
        cfg.CHROMA_MULTIPLIER = 1.0f;
        cfg.CONTRAST = 0f;
        cfg.WHITE_POINT = 0f;
        applyQmTint(cfg, dark);
    }

    private static void applyQmTint(com.qmdeve.liquidglass.Config cfg, boolean dark) {
        if (dark) {
            cfg.TINT_COLOR_RED = 0f;
            cfg.TINT_COLOR_GREEN = 0f;
            cfg.TINT_COLOR_BLUE = 0f;
            cfg.TINT_ALPHA = 0.12f;
        } else {
            cfg.TINT_COLOR_RED = 1f;
            cfg.TINT_COLOR_GREEN = 1f;
            cfg.TINT_COLOR_BLUE = 1f;
            cfg.TINT_ALPHA = 0.10f;
        }
    }

    private static void setupTabPopAnimation(ViewGroup bar) {
        if (!(bar instanceof android.widget.RadioGroup)) {
            return;
        }
        try {
            java.lang.reflect.Field f = android.widget.RadioGroup.class
                    .getDeclaredField("mOnCheckedChangeListener");
            f.setAccessible(true);
            final Object original = f.get(bar);
            android.widget.RadioGroup group = (android.widget.RadioGroup) bar;
            group.setOnCheckedChangeListener((rg, checkedId) -> {
                if (original instanceof android.widget.RadioGroup.OnCheckedChangeListener) {
                    ((android.widget.RadioGroup.OnCheckedChangeListener) original)
                            .onCheckedChanged(rg, checkedId);
                }
                if (rg.getParent() instanceof LiquidGlassHostLayout) {
                    ((LiquidGlassHostLayout) rg.getParent())
                            .popChild(rg.findViewById(checkedId));
                }
            });
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.log(android.util.Log.WARN,
                    "tab pop animation unavailable: " + t);
        }
    }

    private static void hideLegacyShadow(ViewGroup root, int barId) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child.getClass() == View.class && child.getBackground() != null) {
                ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (lp instanceof RelativeLayout.LayoutParams) {
                    if (((RelativeLayout.LayoutParams) lp)
                            .getRules()[RelativeLayout.ABOVE] == barId) {
                        child.setVisibility(View.GONE);
                        return;
                    }
                }
            }
        }
    }

    private static void copyMargins(RelativeLayout.LayoutParams src,
                                    RelativeLayout.LayoutParams dst) {
        dst.leftMargin = src.leftMargin;
        dst.topMargin = src.topMargin;
        dst.rightMargin = src.rightMargin;
        dst.bottomMargin = src.bottomMargin;
    }

    private static int computeNavInsetPadding(Activity activity,
                                              ViewGroup root, ViewGroup bar) {
        try {
            WindowInsets wi = root.getRootWindowInsets();
            int nav = 0;
            if (wi == null) {
                return 0;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                nav = wi.getInsets(WindowInsets.Type.navigationBars()).bottom;
            } else {
                nav = wi.getSystemWindowInsetBottom();
            }
            if (nav <= 0) {
                return 0;
            }
            View decor = activity.getWindow().getDecorView();
            int[] loc = new int[2];
            decor.getLocationOnScreen(loc);
            int decorBottom = loc[1] + decor.getHeight();
            int realBottom = getRealDisplayBottom(activity);
            if (realBottom <= 0) {
                return 0;
            }
            int gap = realBottom - decorBottom;
            int extra = Math.max(nav - gap, 0);
            float density = root.getResources().getDisplayMetrics().density;
            int capped = (int) Math.min(extra, density * 56f);
            int existing = Math.max(bar.getPaddingBottom(), root.getPaddingBottom());
            return Math.max(capped - existing, 0);
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("computeNavInsetPadding failed", t);
            return 0;
        }
    }

    private static int getRealDisplayBottom(Activity activity) {
        try {
            WindowManager wm = (WindowManager)
                    activity.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return 0;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return wm.getMaximumWindowMetrics().getBounds().bottom;
            }
            Display display = wm.getDefaultDisplay();
            if (display == null) {
                return 0;
            }
            DisplayMetrics dm = new DisplayMetrics();
            display.getRealMetrics(dm);
            return dm.heightPixels;
        } catch (Throwable t) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends View> T findViewByName(Activity activity, String name) {
        try {
            int id = activity.getResources()
                    .getIdentifier(name, "id", activity.getPackageName());
            if (id == 0) {
                return null;
            }
            return (T) activity.findViewById(id);
        } catch (Throwable t) {
            return null;
        }
    }
}
