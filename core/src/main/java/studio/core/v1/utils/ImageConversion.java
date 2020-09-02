/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ImageConversion {

    private static final String BITMAP_FORMAT = "BMP";
    private static final String PNG_FORMAT = "PNG";

    private static final String BITMAP_RLE4_COMPRESSION = "BI_RLE4";

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

    public static byte[] anyToRLECompressedBitmap(byte[] data) throws IOException {
        BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(data));
        // Redraw image to remove potential alpha channel, and to used indexed colors
        BufferedImage redrawn = redrawIndexedImage(inputImage);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName(BITMAP_FORMAT).next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionType(BITMAP_RLE4_COMPRESSION);
        writer.setOutput(ImageIO.createImageOutputStream(output));
        writer.write(null, new IIOImage(redrawn, null, null), writeParam);
        if (output.size() == 0) {
            throw new IOException("Failed to convert image");
        }
        return output.toByteArray();
    }

    private static BufferedImage redrawIndexedImage(BufferedImage inputImage) {
        // TODO Use the known color palette from the existing pack's base image
        int[] cmap = new int[16];
        cmap[0] = 0x00ffffff;
        cmap[1] = 0x00dddddd;
        cmap[2] = 0x00bbbbbb;
        cmap[3] = 0x00999999;
        cmap[4] = 0x00777777;
        cmap[5] = 0x00555555;
        cmap[6] = 0x00333333;
        cmap[7] = 0x00111111;
        cmap[8] = 0x00000000;
        cmap[9] = 0x00ffffff;
        cmap[10] = 0x00ffffff;
        cmap[11] = 0x00ffffff;
        cmap[12] = 0x00ffffff;
        cmap[13] = 0x00ffffff;
        cmap[14] = 0x00ffffff;
        cmap[15] = 0x00ffffff;
        BufferedImage redrawn = new BufferedImage(inputImage.getWidth(), inputImage.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel(4, 16, cmap, 0, false, -1, 0));
        Graphics2D g2d = redrawn.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, redrawn.getWidth(), redrawn.getHeight());
        g2d.drawImage(inputImage, 0, 0, null);
        g2d.dispose();
        return redrawn;
    }
}
