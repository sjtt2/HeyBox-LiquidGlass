/*
 * MIT License
 *
 * Copyright (c) 2025-2026 Donny Yang
 *
 * Vendored from QmDeve/AndroidLiquidGlassView (core), stripped of
 * androidx.annotation dependencies for standalone Xposed-module use.
 */

package com.qmdeve.liquidglass;

public class Config {
    public float DISPERSION, DEPTH_EFFECT = 0.3f;
    public int WIDTH, HEIGHT;
    public volatile float CORNER_RADIUS_PX;
    public volatile float ECCENTRIC_FACTOR = 1.0f;
    public volatile float REFRACTION_HEIGHT;
    public volatile float REFRACTION_OFFSET;
    public volatile float CONTRAST;
    public volatile float WHITE_POINT;
    public volatile float CHROMA_MULTIPLIER;
    public volatile float BLUR_RADIUS;
    public float TINT_ALPHA, TINT_COLOR_RED, TINT_COLOR_GREEN, TINT_COLOR_BLUE;
}
