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
        sHostRef = host;

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
                        InWindowTipWatcher.start(activity);
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
    private static final float FIT_TAB_MAX_WIDTH_DP = 96f;
    private static final float SELECTED_TAB_WEIGHT = 1.4f;
    private static final float OTHER_TAB_WEIGHT = 0.9f;
    /** Width transitions borrow LiquidGlassTabBar's own droplet settle timing
     *  (380ms / OvershootInterpolator(1.1)) so labels and droplet travel as
     *  one system instead of two overlapping animations. */
    private static final long FIT_ANIM_MS = 380L;
    private static final float FIT_ANIM_TENSION = 1.1f;
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
            WindowImmersiveController.refresh();
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
            sRadioBarRef = new java.lang.ref.WeakReference<>(bar);
            sContentViewRef = new java.lang.ref.WeakReference<>(content);
            installTitleBarEntry(activity);
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
            resetWidthAnimState();
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
                        if (applyTabWidths(tabBar.getSelectedIndex())) {
                            reanimateDroplet(tabBar);
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
            installRepeatClickRefresh(tabBar, visibleButtons);

            // initial selection from current checked state
            int checked = bar.getCheckedRadioButtonId();
            int selected = 0;
            for (int i = 0; i < visibleButtons.size(); i++) {
                if (visibleButtons.get(i).getId() == checked) {
                    selected = i;
                    break;
                }
            }
            applyTabWidths(selected);
            tabBar.setSelectedIndex(selected);

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
            sMidTabRef = new java.lang.ref.WeakReference<>(
                    midTab != null ? midTab : centerHost);
            if (midTab != null) {
                midTab.getViewTreeObserver().addOnPreDrawListener(
                        new android.view.ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                try {
                                    if (sPlusHidden) {
                                        if (midTab.getVisibility()
                                                != View.GONE) {
                                            midTab.setVisibility(View.GONE);
                                        }
                                    } else {
                                        if (midTab.getVisibility()
                                                != View.VISIBLE) {
                                            midTab.setVisibility(View.VISIBLE);
                                        }
                                        restoreChildren(midTab);
                                    }
                                } catch (Throwable ignored) {
                                }
                                return true;
                            }
                        });
            }

            // keep red-dot tips above everything else
            if (tips != null && tips.getParent() == host) {
                host.removeView(tips);
                host.addView(tips, host.getChildCount());
            }

            // size & position the center button over the spacer column once
            // the tab bar has been laid out
            placeCenterNow(host, tabBar, centerRef, 0);
            applyBarGeometry();
            syncPlusButton(bar, tabBar);

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
            startTabVisibilitySync(bar, tabBar, visibleButtons);

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
    private static void insertCenterGap(Context context, ViewGroup tabBar) {
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
            android.widget.Space spacer = new android.widget.Space(context);
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
        if (center == null || sPlusHidden || sCircleMode || attempt > 10) {
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
            if (center.getWidth() == spacer.getWidth()
                    && center.getHeight() == tabBar.getHeight()
                    && center.getLeft() == left
                    && center.getTop() == tabBar.getTop()
                    && center.getVisibility() == View.VISIBLE) {
                return;
            }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    spacer.getWidth(), tabBar.getHeight(),
                    android.view.Gravity.TOP | android.view.Gravity.START);
            lp.leftMargin = left;
            lp.topMargin = tabBar.getTop();
            center.setLayoutParams(lp);
        } catch (Throwable ignored) {
        }
    }

    /**
     * The library adds its tabsRow with a bare FrameLayout.LayoutParams
     * (MATCH_PARENT, WRAP_CONTENT), which lands at TOP|START. Once the bar
     * runs at a fixed height the row sticks to the top instead of sitting
     * next to the centered publish button, so pin it to CENTER_VERTICAL.
     */
    private static void centerTabsRow(ViewGroup tabBar) {
        try {
            if (tabBar.getChildCount() == 0) {
                return;
            }
            View row = tabBar.getChildAt(0);
            if (row == null || !(row.getLayoutParams()
                    instanceof FrameLayout.LayoutParams)) {
                return;
            }
            FrameLayout.LayoutParams rlp =
                    (FrameLayout.LayoutParams) row.getLayoutParams();
            int want = android.view.Gravity.CENTER_VERTICAL
                    | android.view.Gravity.FILL_HORIZONTAL;
            if (rlp.gravity == want) {
                return;
            }
            rlp.gravity = want;
            row.setLayoutParams(rlp);
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("center tabs row failed", t);
        }
    }

    /** Applies user height/offset config to the live bar (settings dialog). */
    static void applyBarGeometry() {        try {
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

            centerTabsRow((ViewGroup) barV);

            int off = Math.max(0, GlassConfig.barOffsetDp);
            host.setPadding(host.getPaddingLeft(), host.getPaddingTop(),
                    host.getPaddingRight(), sBasePadBottom
                            + Math.round(off * den));

            boolean tabLayoutChanged = applyTabWidths();

            host.requestLayout();
            if (tabLayoutChanged
                    && barV instanceof com.example.liquidglass.LiquidGlassTabBar) {
                reanimateDroplet(
                        (com.example.liquidglass.LiquidGlassTabBar) barV);
            }
            final ViewGroup h2 = host;
            final ViewGroup b2 = (ViewGroup) barV;
            host.post(() -> placeCenterNow(h2, b2, center, 0));
            android.widget.RadioGroup radio = sRadioBarRef.get();
            if (radio != null
                    && barV instanceof com.example.liquidglass.LiquidGlassTabBar) {
                applyBarMode(radio,
                        (com.example.liquidglass.LiquidGlassTabBar) barV);
            }
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("applyBarGeometry failed", t);
        }
    }

    private static boolean applyTabWidths() {
        View barV = sTabBarRef.get();
        int selected = barV instanceof com.example.liquidglass.LiquidGlassTabBar
                ? ((com.example.liquidglass.LiquidGlassTabBar) barV).getSelectedIndex()
                : 0;
        return applyTabWidths(selected);
    }

    private static boolean applyTabWidths(int selectedIndex) {
        boolean changed = false;
        try {
            View barV = sTabBarRef.get();
            if (!(barV instanceof ViewGroup)
                    || ((ViewGroup) barV).getChildCount() == 0
                    || !(((ViewGroup) barV).getChildAt(0) instanceof ViewGroup)) {
                return false;
            }
            ViewGroup row = (ViewGroup) ((ViewGroup) barV).getChildAt(0);
            int tabs = 0;
            boolean hasGap = false;
            for (int i = 0; i < row.getChildCount(); i++) {
                if (row.getChildAt(i) instanceof android.widget.LinearLayout) {
                    tabs++;
                } else if (row.getChildAt(i) instanceof android.widget.Space) {
                    hasGap = true;
                }
            }
            if (tabs == 0) {
                return false;
            }
            float f = Math.max(50, Math.min(GlassConfig.tabWidthPct, 150)) / 100f;
            boolean fit = fitVisibleTabsEffective(tabs);
            // Only the adaptive mode animates; the tab-width slider must stay
            // instant or every drag tick would queue a 380ms wobble.
            boolean glide = fit || sFitActive;
            sFitActive = fit;
            float[] before = glide ? captureTabCenters(row) : null;
            int selected = Math.max(0, Math.min(selectedIndex, tabs - 1));
            float gap = fit ? CENTER_GAP_WEIGHT
                    : Math.max(0.3f, (tabs + CENTER_GAP_WEIGHT) / f - tabs);
            int tabIndex = 0;
            for (int i = 0; i < row.getChildCount(); i++) {
                View c = row.getChildAt(i);
                if (!(c.getLayoutParams()
                        instanceof android.widget.LinearLayout.LayoutParams)) {
                    continue;
                }
                android.widget.LinearLayout.LayoutParams lp =
                        (android.widget.LinearLayout.LayoutParams) c.getLayoutParams();
                float w;
                if (c instanceof android.widget.Space) {
                    w = gap;
                } else if (c instanceof android.widget.LinearLayout) {
                    w = fit
                            ? (tabIndex == selected
                            ? SELECTED_TAB_WEIGHT : OTHER_TAB_WEIGHT)
                            : f;
                    tabIndex++;
                } else {
                    continue;
                }
                if (Math.abs(lp.weight - w) > 0.001f) {
                    lp.weight = w;
                    c.setLayoutParams(lp);
                    changed = true;
                }
            }
            float totalWeight = fit
                    ? OTHER_TAB_WEIGHT * tabs
                    + (SELECTED_TAB_WEIGHT - OTHER_TAB_WEIGHT)
                    + (hasGap ? CENTER_GAP_WEIGHT : 0f)
                    : 0f;
            changed |= applyFitBarWidth(barV, totalWeight, fit, f);
            if (changed && before != null) {
                scheduleTabGlide(row, before);
            }
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("apply tab widths failed", t);
        }
        return changed;
    }

    /** A re-installed bar is a different view: drop any animation still
     *  running against the old one, so its pending target cannot suppress the
     *  first width applied to the new bar. */
    private static void resetWidthAnimState() {
        if (sTabShiftAnimator != null) {
            sTabShiftAnimator.cancel();
            sTabShiftAnimator = null;
        }
        if (sBarWidthAnimator != null) {
            sBarWidthAnimator.cancel();
            sBarWidthAnimator = null;
        }
        if (sDropletSizeAnimator != null) {
            sDropletSizeAnimator.cancel();
            sDropletSizeAnimator = null;
        }
        sBarTargetWidth = Integer.MIN_VALUE;
        sBarTargetLeft = Integer.MIN_VALUE;
        sBarTargetGravity = Integer.MIN_VALUE;
        sFitActive = false;
    }

    /** `animateDropletTo` sizes the droplet to the incoming tab in one step
     *  before it starts travelling, so the glass pops from the narrow width to
     *  the wide one — the single most visible part of the change. The bar
     *  leaves the size alone for the rest of the settle (`onLayout` only
     *  re-syncs when its own animator is idle), so we can widen it ourselves
     *  over the same beat. */
    private static void scheduleDropletGrow(
            final com.example.liquidglass.LiquidGlassTabBar tabBar,
            final int fromWidth) {
        try {
            if (fromWidth <= 0) {
                return;
            }
            final View droplet = findDroplet(tabBar);
            if (droplet == null) {
                return;
            }
            ViewTreeObserver vto = tabBar.getViewTreeObserver();
            if (vto == null || !vto.isAlive()) {
                return;
            }
            vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    ViewTreeObserver live = tabBar.getViewTreeObserver();
                    if (live != null && live.isAlive()) {
                        live.removeOnPreDrawListener(this);
                    }
                    // false: the start width is a layout property, so the
                    // frame has to be re-run rather than drawn at the end size
                    return !growDroplet(droplet, fromWidth);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static boolean growDroplet(View droplet, int fromWidth) {
        try {
            if (!(droplet.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                return false;
            }
            final FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) droplet.getLayoutParams();
            final int toWidth = lp.width;
            if (toWidth <= 0 || Math.abs(toWidth - fromWidth) < 2) {
                return false;
            }
            if (sDropletSizeAnimator != null) {
                sDropletSizeAnimator.cancel();
            }
            final int startWidth = fromWidth;
            lp.width = startWidth;
            droplet.setLayoutParams(lp);
            android.animation.ValueAnimator anim =
                    android.animation.ValueAnimator.ofFloat(0f, 1f);
            // lands just inside the bar's own 380ms settle, so the first
            // layout after the settle finds the final width already in place
            anim.setDuration(Math.max(0L, FIT_ANIM_MS - 40L));
            anim.setInterpolator(
                    new android.view.animation.DecelerateInterpolator(1.6f));
            anim.addUpdateListener(a -> {
                float t = (Float) a.getAnimatedValue();
                lp.width = Math.round(startWidth + (toWidth - startWidth) * t);
                droplet.setLayoutParams(lp);
            });
            final boolean[] cancelled = {false};
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(android.animation.Animator a) {
                    cancelled[0] = true;
                }

                @Override
                public void onAnimationEnd(android.animation.Animator a) {
                    if (cancelled[0]) {
                        return;
                    }
                    lp.width = toWidth;
                    droplet.setLayoutParams(lp);
                }
            });
            sDropletSizeAnimator = anim;
            anim.start();
            return true;
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("droplet grow failed", t);
            return false;
        }
    }

    /** Snapshot of where each tab currently *appears* (layout position plus
     *  any glide still in flight), so the layout change about to happen can be
     *  replayed as motion. Null until the row has been laid out. */
    private static float[] captureTabCenters(ViewGroup row) {
        if (row.getWidth() <= 0) {
            return null;
        }
        float[] centers = new float[row.getChildCount()];
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (c.getWidth() <= 0) {
                return null;
            }
            centers[i] = c.getLeft() + c.getWidth() / 2f + c.getTranslationX();
        }
        return centers;
    }

    /** Defers the glide to the pre-draw pass: the new weights are already in
     *  place by then (the droplet needs the final layout to aim at), and
     *  offsetting before the frame is drawn means no flash of the end state. */
    private static void scheduleTabGlide(final ViewGroup row,
            final float[] before) {
        try {
            ViewTreeObserver vto = row.getViewTreeObserver();
            if (vto == null || !vto.isAlive()) {
                return;
            }
            vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    ViewTreeObserver live = row.getViewTreeObserver();
                    if (live != null && live.isAlive()) {
                        live.removeOnPreDrawListener(this);
                    }
                    glideTabsFrom(row, before);
                    return true;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** A tab slot is transparent — all the user sees is the centred icon+label
     *  stack. So the width change is animated by leaving the layout at its
     *  final state (which `syncDroplet`/`animateDropletTo` read) and sliding
     *  the content back to where it was, then home. */
    private static void glideTabsFrom(ViewGroup row, float[] before) {
        try {
            if (before == null || row.getChildCount() != before.length) {
                return;
            }
            if (sBarWidthAnimator != null && sBarWidthAnimator.isRunning()) {
                // the pill is already animating its own real layout; the tabs
                // ride along with it and must not be offset on top of that
                return;
            }
            final View[] kids = new View[before.length];
            final float[] shift = new float[before.length];
            boolean any = false;
            for (int i = 0; i < before.length; i++) {
                View c = row.getChildAt(i);
                if (c.getWidth() <= 0) {
                    return;
                }
                kids[i] = c;
                shift[i] = before[i] - (c.getLeft() + c.getWidth() / 2f);
                any |= Math.abs(shift[i]) > 0.5f;
            }
            if (!any) {
                return;
            }
            if (sTabShiftAnimator != null) {
                sTabShiftAnimator.cancel();
            }
            for (int i = 0; i < kids.length; i++) {
                kids[i].setTranslationX(shift[i]);
            }
            android.animation.ValueAnimator anim =
                    android.animation.ValueAnimator.ofFloat(1f, 0f);
            anim.setDuration(FIT_ANIM_MS);
            anim.setInterpolator(
                    new android.view.animation.OvershootInterpolator(
                            FIT_ANIM_TENSION));
            anim.addUpdateListener(a -> {
                float t = (Float) a.getAnimatedValue();
                for (int i = 0; i < kids.length; i++) {
                    kids[i].setTranslationX(shift[i] * t);
                }
            });
            final boolean[] cancelled = {false};
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(android.animation.Animator a) {
                    cancelled[0] = true;
                }

                @Override
                public void onAnimationEnd(android.animation.Animator a) {
                    if (cancelled[0]) {
                        return;
                    }
                    for (View kid : kids) {
                        kid.setTranslationX(0f);
                    }
                }
            });
            sTabShiftAnimator = anim;
            anim.start();
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("tab glide failed", t);
        }
    }

    private static boolean applyFitBarWidth(View barV, float totalWeight,
            boolean fit, float widthScale) {
        if (!(barV.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return false;
        }
        FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) barV.getLayoutParams();
        ViewGroup host = sHostRef;
        float den = sDensity > 0 ? sDensity
                : barV.getResources().getDisplayMetrics().density;
        int hostWidth = host != null ? host.getWidth() : 0;
        if (hostWidth <= 0) {
            hostWidth = barV.getResources().getDisplayMetrics().widthPixels
                    - Math.round(20f * den);
        }
        int available = Math.max(0, hostWidth - lp.rightMargin);
        if (!fit) {
            return setBarWidth(barV, lp, ViewGroup.LayoutParams.MATCH_PARENT, 0,
                    android.view.Gravity.TOP
                            | android.view.Gravity.FILL_HORIZONTAL, available);
        }
        if (available <= 0 || totalWeight <= 0f) {
            return false;
        }
        int innerAvailable = Math.max(1, available - Math.round(8f * den));
        int perWeight = Math.min(
                Math.round(FIT_TAB_MAX_WIDTH_DP * widthScale * den),
                Math.round(innerAvailable / totalWeight));
        int width = Math.min(available,
                Math.round(totalWeight * perWeight) + Math.round(8f * den));
        int left = Math.max(0, (available - width) / 2);
        return setBarWidth(barV, lp, width, left,
                android.view.Gravity.TOP | android.view.Gravity.START,
                available);
    }

    /** The pill's own width is a visible edge, so unlike the tab slots it has
     *  to be animated through real layout. Callers compare against the pending
     *  target rather than the live params, so the 500ms visibility poll cannot
     *  fight an animation that is already heading to the same place. */
    private static boolean setBarWidth(View barV, FrameLayout.LayoutParams lp,
            int width, int left, int gravity, int available) {
        boolean animating = sBarWidthAnimator != null
                && sBarWidthAnimator.isRunning();
        if (animating && width == sBarTargetWidth && left == sBarTargetLeft
                && gravity == sBarTargetGravity) {
            return false;
        }
        if (!animating && lp.width == width && lp.gravity == gravity
                && lp.leftMargin == left) {
            sBarTargetWidth = width;
            sBarTargetLeft = left;
            sBarTargetGravity = gravity;
            return false;
        }
        sBarTargetWidth = width;
        sBarTargetLeft = left;
        sBarTargetGravity = gravity;
        final int startWidth = barV.getWidth();
        final int startLeft = lp.leftMargin;
        int endPx = width == ViewGroup.LayoutParams.MATCH_PARENT
                ? available : width;
        if (animating) {
            sBarWidthAnimator.cancel();
        }
        if (startWidth <= 0 || endPx <= 0 || startWidth == endPx) {
            lp.width = width;
            lp.leftMargin = left;
            lp.gravity = gravity;
            barV.setLayoutParams(lp);
            return true;
        }
        final FrameLayout.LayoutParams flp = lp;
        final View target = barV;
        final int endWidth = endPx;
        final int endLeft = left;
        final int endGravity = gravity;
        final int finalWidth = width;
        // fixed width + FILL_HORIZONTAL would stretch back to full, so the
        // pill is pinned to START for the duration and restored on end
        flp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        android.animation.ValueAnimator anim =
                android.animation.ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(FIT_ANIM_MS);
        anim.setInterpolator(
                new android.view.animation.DecelerateInterpolator(1.6f));
        anim.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            flp.width = Math.round(startWidth + (endWidth - startWidth) * t);
            flp.leftMargin = Math.round(startLeft + (endLeft - startLeft) * t);
            target.setLayoutParams(flp);
        });
        final boolean[] cancelled = {false};
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(android.animation.Animator a) {
                cancelled[0] = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (cancelled[0]) {
                    return;
                }
                flp.width = finalWidth;
                flp.leftMargin = endLeft;
                flp.gravity = endGravity;
                target.setLayoutParams(flp);
            }
        });
        sBarWidthAnimator = anim;
        anim.start();
        return true;
    }


    private static void startTabVisibilitySync(
            final android.widget.RadioGroup bar,
            final com.example.liquidglass.LiquidGlassTabBar tabBar,
            final java.util.List<android.widget.RadioButton> buttons) {
        bar.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean changed = bar.isAttachedToWindow()
                            && syncTabSet(bar, tabBar, buttons);
                    if (bar.isAttachedToWindow()
                            && syncPlusButton(bar, tabBar) && !changed) {
                        HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                                "center gap toggled by plus visibility");
                    }
                } catch (Throwable t) {
                    HeyBoxLiquidGlassModule.logErr("tab visibility sync failed", t);
                }
                bar.postDelayed(this, 500L);
            }
        }, 1500L);
    }

    private static boolean syncPlusButton(android.widget.RadioGroup bar,
            com.example.liquidglass.LiquidGlassTabBar tabBar) {
        return applyBarMode(bar, tabBar);
    }

    private static boolean applyBarMode(android.widget.RadioGroup bar,
            com.example.liquidglass.LiquidGlassTabBar tabBar) {
        View barV = sTabBarRef.get();
        ViewGroup host = sHostRef;
        View center = sCenterRefStatic;
        View mid = sMidTabRef == null ? null : sMidTabRef.get();
        if (barV == null || host == null || center == null) {
            return false;
        }
        int visibleTabs = 0;
        for (int i = 0; i < bar.getChildCount(); i++) {
            View c = bar.getChildAt(i);
            if (c instanceof android.widget.RadioButton
                    && c.getVisibility() == View.VISIBLE) {
                visibleTabs++;
            }
        }
        if (visibleTabs == sLastTabs) {
            sStableTabs = visibleTabs;
        }
        sLastTabs = visibleTabs;
        int stableTabs = sStableTabs >= 0 ? sStableTabs : visibleTabs;
        boolean bhHideAdd = betterHeyboxHideAdd(tabBar.getContext());
        boolean circle;
        boolean wantHidden;
        if (bhHideAdd) {
            circle = false;
            wantHidden = true;
        } else {
            int mode = GlassConfig.barLayoutMode;
            if (mode == 0) {
                mode = stableTabs % 2 == 1 ? 2 : 1;
            }
            circle = mode == 2;
            wantHidden = !circle && (stableTabs % 2 == 1 || stableTabs == 0);
        }
        sCircleMode = circle;
        boolean changed = false;
        if (mid != null) {
            boolean hiddenNow = mid.getVisibility() == View.GONE
                    || allChildrenGone(mid);
            if (circle || !wantHidden) {
                if (hiddenNow) {
                    mid.setVisibility(View.VISIBLE);
                    restoreChildren(mid);
                    changed = true;
                }
            } else if (!hiddenNow) {
                mid.setVisibility(View.GONE);
                changed = true;
            }
        }
        sPlusHidden = wantHidden;
        if (tabBar.getChildCount() == 0
                || !(tabBar.getChildAt(0) instanceof ViewGroup)) {
            return true;
        }
        ViewGroup row = (ViewGroup) tabBar.getChildAt(0);
        boolean hasGap = false;
        for (int i = 0; i < row.getChildCount(); i++) {
            if (row.getChildAt(i) instanceof android.widget.Space) {
                hasGap = true;
                break;
            }
        }
        boolean wantGap = !circle && !wantHidden;
        if (wantGap && !hasGap) {
            insertCenterGap(tabBar.getContext(), tabBar);
            changed = true;
        } else if (!wantGap && hasGap) {
            for (int i = row.getChildCount() - 1; i >= 0; i--) {
                if (row.getChildAt(i) instanceof android.widget.Space) {
                    row.removeViewAt(i);
                }
            }
            changed = true;
        }
        float den = sDensity > 0 ? sDensity : 3f;
        int barH = barV.getHeight();
        int circleSize = barH > 0 ? barH : Math.round(56 * den);
        int circleGap = Math.round(8 * den);
        if (barV.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams blp =
                    (FrameLayout.LayoutParams) barV.getLayoutParams();
            int wantMargin = circle ? circleSize + circleGap : 0;
            if (blp.rightMargin != wantMargin) {
                blp.rightMargin = wantMargin;
                barV.setLayoutParams(blp);
                changed = true;
            }
        }
        boolean tabLayoutChanged = applyTabWidths();
        changed |= tabLayoutChanged;
        if (tabLayoutChanged) {
            reanimateDroplet(tabBar);
        }
        if (circle) {
            View glass = sGlassCircleRef == null ? null : sGlassCircleRef.get();
            if (glass != null && glass.getParent() != center) {
                glass = null;
            }
            if (glass == null && center instanceof ViewGroup) {
                glass = buildGlassCircle(tabBar.getContext());
                if (glass != null) {
                    sGlassCircleRef = new java.lang.ref.WeakReference<>(glass);
                    ((ViewGroup) center).addView(glass, 0,
                            new FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT));
                    changed = true;
                }
            }
            if (glass != null && glass.getVisibility() != View.VISIBLE) {
                glass.setVisibility(View.VISIBLE);
                changed = true;
            }
            if (center.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams clp =
                        (FrameLayout.LayoutParams) center.getLayoutParams();
                int wantGravity = android.view.Gravity.END
                        | android.view.Gravity.CENTER_VERTICAL;
                if (clp.width != circleSize || clp.height != circleSize
                        || clp.gravity != wantGravity || clp.leftMargin != 0
                        || clp.topMargin != 0 || clp.rightMargin != 0
                        || clp.bottomMargin != 0) {
                    clp.width = circleSize;
                    clp.height = circleSize;
                    clp.gravity = wantGravity;
                    clp.leftMargin = 0;
                    clp.topMargin = 0;
                    clp.rightMargin = 0;
                    clp.bottomMargin = 0;
                    center.setLayoutParams(clp);
                    changed = true;
                }
            }
            if (center.getVisibility() != View.VISIBLE) {
                center.setVisibility(View.VISIBLE);
                changed = true;
            }
        } else {
            View glass = sGlassCircleRef == null ? null : sGlassCircleRef.get();
            if (glass != null && glass.getVisibility() != View.GONE) {
                glass.setVisibility(View.GONE);
                changed = true;
            }
            int wantVis = wantHidden ? View.GONE : View.VISIBLE;
            if (center.getVisibility() != wantVis) {
                center.setVisibility(wantVis);
                changed = true;
            }
        }
        if (changed) {
            row.requestLayout();
            host.requestLayout();
        }
        if (!circle) {
            final ViewGroup h2 = host;
            final View c2 = center;
            final com.example.liquidglass.LiquidGlassTabBar t2 = tabBar;
            host.post(() -> placeCenterNow(h2, t2, c2, 0));
        }
        return changed;
    }

    private static final String BH_PREFS = "betterheybox";
    private static final String BH_PENDING_PREFS = "betterheybox_pending";
    private static final String BH_KEY_HIDE_ADD = "hide_add";

    private static boolean fitVisibleTabsEffective(int visibleTabs) {
        if (!GlassConfig.fitVisibleTabs) {
            return false;
        }
        try {
            android.widget.RadioGroup bar = sRadioBarRef.get();
            int named = 0;
            if (bar != null) {
                for (int i = 0; i < bar.getChildCount(); i++) {
                    View child = bar.getChildAt(i);
                    if (!(child instanceof android.widget.RadioButton)) {
                        continue;
                    }
                    CharSequence title = ((android.widget.RadioButton) child).getText();
                    if (title == null || title.toString().trim().isEmpty()) {
                        continue;
                    }
                    named++;
                }
            }
            return visibleTabs > 0 && visibleTabs < named;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Mirrors BetterHeybox's own three-tier key resolution so the two
     *  modules agree on whether the plus button is explicitly hidden. */
    private static boolean betterHeyboxHideAdd(Context context) {
        return betterHeyboxBoolean(context, BH_KEY_HIDE_ADD);
    }

    private static boolean betterHeyboxBoolean(Context context, String key) {
        try {
            android.content.SharedPreferences pending = context
                    .getSharedPreferences(BH_PENDING_PREFS, 0);
            if (pending != null && pending.contains(key)) {
                return pending.getBoolean(key, false);
            }
            android.content.SharedPreferences local = context
                    .getSharedPreferences(BH_PREFS, 0);
            if (local != null && local.contains(key)) {
                return local.getBoolean(key, false);
            }
            android.content.SharedPreferences remote =
                    HeyBoxLiquidGlassModule.remotePrefs(BH_PREFS);
            if (remote != null) {
                return remote.getBoolean(key, false);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static View buildGlassCircle(Context context) {        try {
            View content = sContentViewRef == null ? null : sContentViewRef.get();
            com.example.liquidglass.LiquidGlassView glass =
                    new com.example.liquidglass.LiquidGlassView(context, null, 0);
            glass.setCornerRadius(999f);
            glass.setEnableDynamicBackground(true);
            if (content != null) {
                glass.setBackdropSource(content);
            }
            glass.setMaterial(com.example.liquidglass.GlassMaterial.REGULAR);
            float den = sDensity > 0 ? sDensity : 3f;
            glass.setRefractionHeight(28f * den);
            glass.setBevelWidth(10f * den);
            glass.setDispersionStrength(0.12f);
            glass.setEnableSensorHighlight(true);
            glass.setEnableAdaptiveTint(false);
            return glass;
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("glass circle build failed", t);
            return null;
        }
    }

    private static boolean allChildrenGone(View view) {
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        if (group.getChildCount() == 0) {
            return false;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getVisibility() != View.GONE) {
                return false;
            }
        }
        return true;
    }

    private static void restoreChildren(View view) {
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View c = group.getChildAt(i);
            if (c.getVisibility() == View.GONE) {
                c.setVisibility(View.VISIBLE);
            }
        }
    }

    private static boolean syncTabSet(android.widget.RadioGroup bar,
            com.example.liquidglass.LiquidGlassTabBar tabBar,
            java.util.List<android.widget.RadioButton> buttons) {
        java.util.List<android.widget.RadioButton> now = new java.util.ArrayList<>();
        for (int i = 0; i < bar.getChildCount(); i++) {
            View c = bar.getChildAt(i);
            if (c instanceof android.widget.RadioButton
                    && c.getVisibility() == View.VISIBLE) {
                now.add((android.widget.RadioButton) c);
            }
        }
        StringBuilder sigBuilder = new StringBuilder();
        for (android.widget.RadioButton rb : now) {
            sigBuilder.append(rb.getId()).append(',');
        }
        String sig = sigBuilder.toString();
        if (!sig.equals(sLastRadioSig)) {
            sLastRadioSig = sig;
            return false;
        }
        boolean same = !now.isEmpty() && now.size() == buttons.size();
        if (same) {
            for (int i = 0; i < now.size(); i++) {
                if (now.get(i) != buttons.get(i)) {
                    same = false;
                    break;
                }
            }
        }
        if (same) {
            return false;
        }
        java.util.List<com.example.liquidglass.LiquidGlassTabBar.TabItem> items =
                new java.util.ArrayList<>();
        for (android.widget.RadioButton rb : now) {
            CharSequence title = rb.getText();
            Drawable icon = rb.getCompoundDrawables()[1];
            if (icon != null) {
                icon.mutate();
            }
            items.add(new com.example.liquidglass.LiquidGlassTabBar.TabItem(
                    title, icon));
        }
        buttons.clear();
        buttons.addAll(now);
        tabBar.setTabs(items);
        if (!sPlusHidden) {
            insertCenterGap(tabBar.getContext(), tabBar);
        }
        int checked = bar.getCheckedRadioButtonId();
        int selected = 0;
        for (int i = 0; i < now.size(); i++) {
            if (now.get(i).getId() == checked) {
                selected = i;
                break;
            }
        }
        applyTabWidths(selected);
        tabBar.setSelectedIndex(selected);
        applyTabBarOverLight(tabBar, sChromeLight);
        tabBar.requestLayout();
        syncPlusButton(bar, tabBar);
        ViewGroup host = sHostRef;
        if (host != null) {
            final ViewGroup h2 = host;
            final com.example.liquidglass.LiquidGlassTabBar t2 = tabBar;
            host.post(() -> placeCenterNow(h2, t2, sCenterRefStatic, 0));
        }
        return true;
    }

    private static void installRepeatClickRefresh(
            final com.example.liquidglass.LiquidGlassTabBar tabBar,
            final java.util.List<android.widget.RadioButton> buttons) {
        if (tabBar == null || buttons == null) {
            return;
        }
        final float[] down = new float[2];
        final int[] selectedBefore = new int[1];
        final boolean[] moved = new boolean[1];
        tabBar.setOnTouchListener((view, event) -> {
            try {
                int action = event.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    down[0] = event.getX();
                    down[1] = event.getY();
                    selectedBefore[0] = tabBar.getSelectedIndex();
                    moved[0] = false;
                } else if (action == android.view.MotionEvent.ACTION_MOVE) {
                    float slop = android.view.ViewConfiguration.get(view.getContext())
                            .getScaledTouchSlop();
                    float dx = event.getX() - down[0];
                    float dy = event.getY() - down[1];
                    moved[0] = moved[0] || dx * dx + dy * dy > slop * slop;
                } else if (action == android.view.MotionEvent.ACTION_UP) {
                    if (moved[0]) {
                        int near = nearestTabIndex(tabBar);
                        if (near >= 0) {
                            prepareSelectionLayout(tabBar, near);
                        }
                    } else {
                        int target = findTabIndexAt(tabBar, event.getX());
                        if (target >= 0) {
                            prepareSelectionLayout(tabBar, target);
                        }
                        final int before = selectedBefore[0];
                        tabBar.post(() -> {
                            try {
                                // Forward the tap the glass bar swallowed and
                                // let the app decide what a re-tap means: the
                                // old-style layout renames its tabs, so any
                                // title whitelist here silently drops refresh
                                // on whichever skin it was not written for.
                                if (target != before || target != tabBar.getSelectedIndex()
                                        || target < 0 || target >= buttons.size()) {
                                    return;
                                }
                                android.widget.RadioButton button = buttons.get(target);
                                if (button != null) {
                                    button.performClick();
                                }
                            } catch (Throwable t) {
                                HeyBoxLiquidGlassModule.logErr(
                                        "repeat tab refresh failed", t);
                            }
                        });
                    }
                } else if (action == android.view.MotionEvent.ACTION_CANCEL) {
                    moved[0] = false;
                }
            } catch (Throwable t) {
                HeyBoxLiquidGlassModule.logErr("repeat tab touch failed", t);
            }
            return false;
        });
    }

    private static void prepareSelectionLayout(
            com.example.liquidglass.LiquidGlassTabBar tabBar,
            int selectedIndex) {
        try {
            // Taken before the bar re-selects: the droplet still has the
            // outgoing tab's width, and animateDropletTo is about to snap it
            // to the incoming one.
            View droplet = findDroplet(tabBar);
            int dropletWidth = droplet == null ? 0 : droplet.getWidth();
            if (!applyTabWidths(selectedIndex)) {
                return;
            }
            scheduleDropletGrow(tabBar, dropletWidth);
            int width = tabBar.getMeasuredWidth();
            int height = tabBar.getMeasuredHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            int widthSpec = View.MeasureSpec.makeMeasureSpec(
                    width, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(
                    height, View.MeasureSpec.EXACTLY);
            tabBar.measure(widthSpec, heightSpec);
            tabBar.layout(tabBar.getLeft(), tabBar.getTop(),
                    tabBar.getRight(), tabBar.getBottom());
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("tab selection layout failed", t);
        }
    }

    private static int nearestTabIndex(
            com.example.liquidglass.LiquidGlassTabBar tabBar) {
        try {
            if (tabBar.getChildCount() <= 0
                    || !(tabBar.getChildAt(0) instanceof android.widget.LinearLayout)) {
                return -1;
            }
            View droplet = findDroplet(tabBar);
            if (droplet == null) {
                return -1;
            }
            float centerX = droplet.getX() + droplet.getWidth() / 2f;
            int best = tabBar.getSelectedIndex();
            float bestDistance = Float.MAX_VALUE;
            int index = 0;
            android.widget.LinearLayout row =
                    (android.widget.LinearLayout) tabBar.getChildAt(0);
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                if (!(child instanceof android.widget.LinearLayout)) {
                    continue;
                }
                float tabCenter = child.getLeft() + child.getWidth() / 2f;
                float distance = Math.abs(tabCenter - centerX);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = index;
                }
                index++;
            }
            return best;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void reanimateDroplet(
            final com.example.liquidglass.LiquidGlassTabBar tabBar) {
        try {
            if (sBarWidthAnimator != null && sBarWidthAnimator.isRunning()) {
                // every frame of that animation is a layout pass, and the bar
                // re-syncs the droplet on layout whenever its own settle
                // animator is idle — re-arming one here would freeze the
                // droplet at a mid-animation target and snap at the end
                return;
            }
            tabBar.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            tabBar.getViewTreeObserver()
                                    .removeOnGlobalLayoutListener(this);
                            try {
                                tabBar.setSelectedIndex(tabBar.getSelectedIndex());
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private static int findTabIndexAt(
            com.example.liquidglass.LiquidGlassTabBar tabBar, float x) {
        if (tabBar == null || tabBar.getChildCount() <= 0
                || !(tabBar.getChildAt(0) instanceof android.view.ViewGroup)) {
            return -1;
        }
        android.view.ViewGroup row = (android.view.ViewGroup) tabBar.getChildAt(0);
        float localX = x - row.getLeft();
        int index = 0;
        for (int childIndex = 0; childIndex < row.getChildCount(); childIndex++) {
            View child = row.getChildAt(childIndex);
            if (!(child instanceof android.widget.LinearLayout)) {
                continue;
            }
            if (localX >= child.getLeft() && localX <= child.getRight()) {
                return index;
            }
            index++;
        }
        return -1;
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
                                prepareSelectionLayout(tabBar, i);
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
    private static final java.lang.ref.WeakReference<View> EMPTY_MID_REF =
            new java.lang.ref.WeakReference<>(null);
    private static volatile java.lang.ref.WeakReference<View> sMidTabRef =
            EMPTY_MID_REF;
    private static volatile boolean sPlusHidden;
    private static final java.lang.ref.WeakReference<android.widget.RadioGroup>
            EMPTY_RADIO_REF = new java.lang.ref.WeakReference<>(null);
    private static volatile java.lang.ref.WeakReference<android.widget.RadioGroup>
            sRadioBarRef = EMPTY_RADIO_REF;
    private static volatile java.lang.ref.WeakReference<View> sGlassCircleRef;
    private static volatile java.lang.ref.WeakReference<View> sContentViewRef;
    private static volatile boolean sCircleMode;
    private static volatile int sLastTabs = -1;
    private static volatile int sStableTabs = -1;
    private static volatile String sLastRadioSig = "";
    private static volatile float sDensity;
    private static int sBasePadBottom;
    private static final java.lang.ref.WeakReference<View> EMPTY_BAR_REF =
            new java.lang.ref.WeakReference<>(null);
    private static volatile java.lang.ref.WeakReference<View> sTabBarRef = EMPTY_BAR_REF;
    // Width-transition state. UI thread only: every writer runs on a touch,
    // layout, settings or postDelayed callback.
    private static android.animation.ValueAnimator sTabShiftAnimator;
    private static android.animation.ValueAnimator sBarWidthAnimator;
    private static android.animation.ValueAnimator sDropletSizeAnimator;
    private static int sBarTargetWidth = Integer.MIN_VALUE;
    private static int sBarTargetLeft = Integer.MIN_VALUE;
    private static int sBarTargetGravity = Integer.MIN_VALUE;
    private static boolean sFitActive;

    static int dbgTintCalls() {
        return sDbgTintCalls;
    }

    static View activeGlassHost() {
        ViewGroup host = sHostRef;
        if (host == null || !host.isAttachedToWindow() || host.getHeight() <= 0) {
            return null;
        }
        return host;
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

    /** 长按“我”页右上角齿轮图标打开液态玻璃设置（入口之一）。 */
    private static volatile boolean sTitleHookInstalled;
    private static final java.util.Map<View, Boolean> sGearPredraw =
            java.util.Collections.synchronizedMap(
                    new java.util.WeakHashMap<View, Boolean>());

    private static void installTitleBarEntry(Activity act) {
        if (sTitleHookInstalled) {
            return;
        }
        sTitleHookInstalled = true;
        try {
            Class<?> htb = Class.forName(
                    "com.max.hbcommon.component.HomeTitleBar", true,
                    act.getClassLoader());
            java.lang.reflect.Method g = null;
            for (java.lang.reflect.Method mm : htb.getDeclaredMethods()) {
                if (mm.getName().equals("getIv_home_search")
                        && mm.getParameterCount() == 0) {
                    g = mm;
                    break;
                }
            }
            if (g == null) {
                HeyBoxLiquidGlassModule.log(android.util.Log.WARN,
                        "getIv_home_search not found on HomeTitleBar");
                return;
            }
            HeyBoxLiquidGlassModule.hookExecutable(g, chain -> {
                Object r = chain.proceed();
                try {
                    if (r instanceof View) {
                        View icon = (View) r;
                        android.content.Context cx = icon.getContext();
                        if (cx instanceof Activity) {
                            final Activity a = (Activity) cx;
                            final View.OnLongClickListener ours = v -> {
                                SettingsDialog.show(a);
                                return true;
                            };
                            icon.setOnLongClickListener(ours);
                            if (!sGearPredraw.containsKey(icon)) {
                                sGearPredraw.put(icon, Boolean.TRUE);
                                icon.getViewTreeObserver()
                                        .addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                                            @Override
                                            public boolean onPreDraw() {
                                                try {
                                                    if (icon.getVisibility() == View.VISIBLE) {
                                                        icon.setOnLongClickListener(ours);
                                                    }
                                                } catch (Throwable ignored) {
                                                }
                                                return true;
                                            }
                                        });
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
                return r;
            });
            HeyBoxLiquidGlassModule.log(android.util.Log.INFO,
                    "title-bar glass entry hooked (long-press gear)");
        } catch (Throwable t) {
            HeyBoxLiquidGlassModule.logErr("title-bar entry hook failed", t);
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
