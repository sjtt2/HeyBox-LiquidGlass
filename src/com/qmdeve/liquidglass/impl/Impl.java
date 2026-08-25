/*
 * MIT License
 *
 * Copyright (c) 2025-2026 Donny Yang
 *
 * Vendored from QmDeve/AndroidLiquidGlassView (core).
 */

package com.qmdeve.liquidglass.impl;

import android.graphics.Canvas;

public interface Impl {
    void onSizeChanged(int w, int h);
    void onPreDraw();
    void draw(Canvas c);
    default void dispose() {}
}
