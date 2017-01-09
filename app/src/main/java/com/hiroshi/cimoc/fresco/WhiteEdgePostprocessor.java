package com.hiroshi.cimoc.fresco;

import android.graphics.Bitmap;

import com.facebook.cache.common.CacheKey;
import com.facebook.cache.common.SimpleCacheKey;
import com.facebook.common.references.CloseableReference;
import com.facebook.imagepipeline.bitmaps.PlatformBitmapFactory;
import com.facebook.imagepipeline.request.BasePostprocessor;

/**
 * Created by Hiroshi on 2017/1/7.
 */

public class WhiteEdgePostprocessor extends BasePostprocessor {

    private String url;

    public WhiteEdgePostprocessor(String url) {
        this.url = url;
    }

    @Override
    public CloseableReference<Bitmap> process(Bitmap sourceBitmap, PlatformBitmapFactory bitmapFactory) {
        int width = sourceBitmap.getWidth();
        int height = sourceBitmap.getHeight();

        int y1, y2, x1, x2;
        int[] pixels = new int[(width > height ? width : height) * 10];

        for (y1 = 0; y1 < height; ++y1) {
            // 确定上线 y1
            sourceBitmap.getPixels(pixels, 0, width, 0, y1, width, 1);
            if (!oneDimensionScan(pixels, width)) {
                sourceBitmap.getPixels(pixels, 0, width, 0, y1, width, 10);
                if (!twoDimensionScan(pixels, width, false, false)) {
                    break;
                }
                y1 += 9;
            }
        }

        if (y1 == height) {
            return super.process(sourceBitmap, bitmapFactory);
        }

        for (y2 = height - 1; y2 > y1; --y2) {
            // 确定下线 y2
            sourceBitmap.getPixels(pixels, 0, width, 0, y2, width, 1);
            if (!oneDimensionScan(pixels, width)) {
                sourceBitmap.getPixels(pixels, 0, width, 0, y2 - 9, width, 10);
                if (!twoDimensionScan(pixels, width, false, true)) {
                    break;
                }
                y2 -= 9;
            }
        }

        int h = y2 - y1 + 1;

        for (x1 = 0; x1 < width; ++x1) {
            // 确定左线 x1
            sourceBitmap.getPixels(pixels, 0, 1, x1, y1, 1, h);
            if (!oneDimensionScan(pixels, h)) {
                sourceBitmap.getPixels(pixels, 0, 10, x1, y1, 10, h);
                if (!twoDimensionScan(pixels, h, true, false)) {
                    break;
                }
                x1 += 9;
            }
        }

        for (x2 = width - 1; x2 > x1; --x2) {
            // 确定右线 x2
            sourceBitmap.getPixels(pixels, 0, 1, x2, y1, 1, h);
            if (!oneDimensionScan(pixels, h)) {
                sourceBitmap.getPixels(pixels, 0, 10, x2 - 9, y1, 10, h);
                if (!twoDimensionScan(pixels, h, true, true)) {
                    break;
                }
                x2 -= 9;
            }
        }

        pixels = new int[(y2 - y1) * (x2 - x1)];

        CloseableReference<Bitmap> reference = bitmapFactory.createBitmap((x2 - x1), (y2 - y1), Bitmap.Config.RGB_565);
        try {
            Bitmap bitmap = reference.get();
            sourceBitmap.getPixels(pixels, 0, x2 - x1, x1, y1, x2 - x1, y2 - y1);
            bitmap.setPixels(pixels, 0, x2 - x1, 0, 0, x2 - x1, y2 - y1);
            return CloseableReference.cloneOrNull(reference);
        } finally {
            CloseableReference.closeSafely(reference);
        }
    }

    @Override
    public CacheKey getPostprocessorCacheKey() {
        return new SimpleCacheKey(url.concat("white"));
    }

    /**
     * 一维扫描
     * @return false if contain a pixel whose color is black else true
     */
    private boolean oneDimensionScan(int[] pixels, int length) {
        for (int i = 0; i < length; ++i) {
            if (!isWhite(pixels[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 二维扫描
     * 10 * 10 方格 按 3:4:3 划分为三个区域 权值分别为 1 2 3
     * L = 30, M = 40, R = 30
     * 加权值 = NUM_L * 1 + NUM_M * 2 + NUM_R * 3
     * @return 加权值 > 30 代表有效信息 即不裁剪
     */
    private boolean twoDimensionScan(int[] pixels, int length, boolean vertical, boolean reverse) {
        if (length < 10) {
            return false;
        }

        int[] value = new int[10];
        int result = 0;
        for (int i = 10; i < length - 10; ++i) {
            if (result > 30) {
                return false;
            }
            result -= value[i % 10];
            value[i % 10] = 0;
            for (int j = 0; j < 10; ++j) {
                int k = vertical ? (i * 10 + j) : (j * length + i);
                value[i % 10] += getValue(isWhite(pixels[k]), reverse, j);
            }
            result += value[i % 10];
        }

        return true;
    }

    /**
     * 根据方向位置计算权值
     */
    private int getValue(boolean white, boolean reverse, int pos) {
        if (white) {
            return 0;
        }
        if (pos < 3) {
            return reverse ? 3 : 1;
        } else if (pos > 6) {
            return reverse ? 1 : 3;
        }
        return 2;
    }

    /**
     * 固定阈值 根据灰度判断黑白
     */
    private boolean isWhite(int pixel) {
        int red = ((pixel & 0x00FF0000) >> 16);
        int green = ((pixel & 0x0000FF00) >> 8);
        int blue = (pixel & 0x000000FF);
        int gray = red * 30 + green * 59 + blue * 11;
        return gray > 21500;
    }

}
