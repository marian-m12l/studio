/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import javax.imageio.ImageIO;
import java.awt.*;
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
        // Redraw image to remove potential alpha channel
        BufferedImage redrawn = redrawImage(inputImage);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(redrawn, format, output);
        if (output.size() == 0) {
            throw new IOException("Failed to convert image");
        }
        return output.toByteArray();
    }

    private static BufferedImage redrawImage(BufferedImage inputImage) {
        BufferedImage redrawn = new BufferedImage(inputImage.getWidth(), inputImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = redrawn.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, redrawn.getWidth(), redrawn.getHeight());
        g2d.drawImage(inputImage, 0, 0, null);
        g2d.dispose();
        return redrawn;
    }
}
