/*
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.jhlabs.image;

import java.awt.Rectangle;

/**
 * A filter which quantizes an image to a set number of colors - useful for
 * producing images which are to be encoded using an index color model. The
 * filter can perform Floyd-Steinberg error-diffusion dithering if required. At
 * present, the quantization is done using an octtree algorithm but I eventually
 * hope to add more quantization methods such as median cut. Note: at present,
 * the filter produces an image which uses the RGB color model (because the
 * application it was written for required it). I hope to extend it to produce
 * an IndexColorModel by request.
 */
public class QuantizeFilter extends WholeImageFilter {

    /**
     * Floyd-Steinberg dithering matrix.
     */
    private static final int[] MATRIX = { //
            0, 0, 0, //
            0, 0, 7, //
            3, 5, 1, //
    };

    private static final int MATRIX_SUM = 16; // sum of all element

    private boolean dither;
    private boolean serpentine = true;
    private int numColors = 256;

    private static class QuantizerWrapper {
        Quantizer quantizer;
        int[] inPixels;
        int[] outPixels;
        int width;
        int height;
    }

    /**
     * Set the number of colors to quantize to.
     * 
     * @param numColors the number of colors. The default is 256.
     */
    public void setNumColors(int numColors) {
        this.numColors = Math.min(Math.max(numColors, 8), 256);
    }

    /**
     * Set whether to use dithering or not. If not, the image is posterized.
     * 
     * @param dither true to use dithering
     */
    public void setDither(boolean dither) {
        this.dither = dither;
    }

    /**
     * Set whether to use a serpentine pattern for return or not. This can reduce
     * 'avalanche' artifacts in the output.
     * 
     * @param serpentine true to use serpentine pattern
     */
    public void setSerpentine(boolean serpentine) {
        this.serpentine = serpentine;
    }

    @Override
    protected int[] filterPixels(int width, int height, int[] inPixels, Rectangle transformedSpace) {
        return quantize(inPixels, width, height);
    }

    private static boolean isOutOfBound(int i, int max) {
        return i < 0 || i >= max;
    }

    private static int move(int i, boolean reverse) {
        return reverse ? -i : i;
    }

    private static int moveToBound(int max, boolean reverse) {
        return reverse ? max - 1 : 0;
    }

    private int[] quantize(int[] inPixels, int width, int height) {
        int count = width * height;
        Quantizer quantizer = new OctTreeQuantizer();
        quantizer.setup(numColors);
        quantizer.addPixels(inPixels, 0, count);

        QuantizerWrapper qw = new QuantizerWrapper();
        qw.quantizer = quantizer;
        qw.inPixels = inPixels;
        qw.outPixels = new int[count];
        qw.width = width;
        qw.height = height;

        int[] table = quantizer.buildColorTable();
        if (!dither) {
            for (int i = 0; i < count; i++) {
                qw.outPixels[i] = table[quantizer.getIndexForColor(inPixels[i])];
            }
        } else {
            for (int y = 0; y < height; y++) {
                quantizeLine(qw, table, serpentine, y);
            }
        }
        return qw.outPixels;
    }

    private static void quantizeLine(QuantizerWrapper qw, int[] table, boolean serpentine, int y) {
        boolean reverse = serpentine && (y & 1) == 1;
        int index = y * qw.width + moveToBound(qw.width, reverse);
        for (int x = 0; x < qw.width; x++) {
            int rgb1 = qw.inPixels[index];
            int rgb2 = table[qw.quantizer.getIndexForColor(rgb1)];

            qw.outPixels[index] = rgb2;

            int r1 = (rgb1 >> 16) & 0xff;
            int g1 = (rgb1 >> 8) & 0xff;
            int b1 = rgb1 & 0xff;

            int r2 = (rgb2 >> 16) & 0xff;
            int g2 = (rgb2 >> 8) & 0xff;
            int b2 = rgb2 & 0xff;

            int er = r1 - r2;
            int eg = g1 - g2;
            int eb = b1 - b2;

            for (int i = -1; i <= 1; i++) {
                if (isOutOfBound(i + y, qw.height)) {
                    continue;
                }
                for (int j = -1; j <= 1; j++) {
                    int w = MATRIX[(i + 1) * 3 + 1 + move(j, reverse)];
                    if (w == 0 || isOutOfBound(j + x, qw.width)) {
                        continue;
                    }
                    int k = index + move(j, reverse);
                    rgb1 = qw.inPixels[k];
                    r1 = (rgb1 >> 16) & 0xff;
                    g1 = (rgb1 >> 8) & 0xff;
                    b1 = rgb1 & 0xff;
                    r1 += er * w / MATRIX_SUM;
                    g1 += eg * w / MATRIX_SUM;
                    b1 += eb * w / MATRIX_SUM;
                    qw.inPixels[k] = (PixelUtils.clamp(r1) << 16) | (PixelUtils.clamp(g1) << 8) | PixelUtils.clamp(b1);
                }
            }
            index += move(1, reverse); // add direction
        }
    }
}