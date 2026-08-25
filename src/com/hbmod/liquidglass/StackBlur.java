package com.hbmod.liquidglass;

import android.graphics.Bitmap;

final class StackBlur {

    private StackBlur() {
    }

    static void blur(Bitmap bmp, int radius) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w <= 0 || h <= 0 || radius < 1) {
            return;
        }
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);

        int[] temp = new int[w * h];
        int wm = w - 1;
        int hm = h - 1;
        int div = radius + radius + 1;

        for (int pass = 0; pass < 3; pass++) {
            boxBlurH(pixels, temp, w, h, radius, wm, div);
            boxBlurV(temp, pixels, w, h, radius, hm, div);
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private static void boxBlurH(int[] src, int[] dst, int w, int h,
                                 int radius, int wm, int div) {
        for (int y = 0; y < h; y++) {
            int rowStart = y * w;
            int aSum = 0, rSum = 0, gSum = 0, bSum = 0;
            for (int i = -radius; i <= radius; i++) {
                int p = src[rowStart + Math.min(wm, Math.max(0, i))];
                aSum += (p >>> 24) & 0xFF;
                rSum += (p >>> 16) & 0xFF;
                gSum += (p >>> 8) & 0xFF;
                bSum += p & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                dst[rowStart + x] = (aSum / div << 24)
                        | (rSum / div << 16)
                        | (gSum / div << 8)
                        | (bSum / div);
                int outX = Math.max(x - radius, 0);
                int inX = Math.min(x + radius + 1, wm);
                int pOut = src[rowStart + outX];
                int pIn = src[rowStart + inX];
                aSum += ((pIn >>> 24) & 0xFF) - ((pOut >>> 24) & 0xFF);
                rSum += ((pIn >>> 16) & 0xFF) - ((pOut >>> 16) & 0xFF);
                gSum += ((pIn >>> 8) & 0xFF) - ((pOut >>> 8) & 0xFF);
                bSum += (pIn & 0xFF) - (pOut & 0xFF);
            }
        }
    }

    private static void boxBlurV(int[] src, int[] dst, int w, int h,
                                 int radius, int hm, int div) {
        for (int x = 0; x < w; x++) {
            int aSum = 0, rSum = 0, gSum = 0, bSum = 0;
            for (int i = -radius; i <= radius; i++) {
                int p = src[Math.min(hm, Math.max(0, i)) * w + x];
                aSum += (p >>> 24) & 0xFF;
                rSum += (p >>> 16) & 0xFF;
                gSum += (p >>> 8) & 0xFF;
                bSum += p & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = (aSum / div << 24)
                        | (rSum / div << 16)
                        | (gSum / div << 8)
                        | (bSum / div);
                int outY = Math.max(y - radius, 0);
                int inY = Math.min(y + radius + 1, hm);
                int pOut = src[outY * w + x];
                int pIn = src[inY * w + x];
                aSum += ((pIn >>> 24) & 0xFF) - ((pOut >>> 24) & 0xFF);
                rSum += ((pIn >>> 16) & 0xFF) - ((pOut >>> 16) & 0xFF);
                gSum += ((pIn >>> 8) & 0xFF) - ((pOut >>> 8) & 0xFF);
                bSum += (pIn & 0xFF) - (pOut & 0xFF);
            }
        }
    }
}
