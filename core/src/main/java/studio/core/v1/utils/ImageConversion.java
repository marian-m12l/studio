/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ImageConversion {

    private static final String BITMAP_FORMAT = "BMP";
    private static final String PNG_FORMAT = "PNG";

    public static byte[] pngToBitmap(byte[] pngData) throws IOException {
        return convertImage(pngData, BITMAP_FORMAT);
    }

    public static byte[] jpegToBitmap(byte[] jpegData) throws IOException {
        return convertImage(jpegData, BITMAP_FORMAT);
    }

    public static byte[] bitmapToPng(byte[] bmpData) throws IOException {
        return convertImage(bmpData, PNG_FORMAT);
    }

    public static byte[] convertImage(byte[] data, String format) throws IOException {
        BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(data));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(inputImage, format, output);
        return output.toByteArray();
    }
}
