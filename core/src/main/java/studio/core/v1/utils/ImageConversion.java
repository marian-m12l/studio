/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import com.jhlabs.image.QuantizeFilter;

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
import java.util.Arrays;

public class ImageConversion {

    private static final String BITMAP_FORMAT = "BMP";
    private static final String PNG_FORMAT = "PNG";

    private static final String BITMAP_RLE4_COMPRESSION = "BI_RLE4";

    public static byte[] anyToBitmap(byte[] data) throws IOException {
        return convertImage(data, BITMAP_FORMAT);
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
        // Quantize image to 16 colors max
        QuantizeFilter quantizeFilter = new QuantizeFilter();
        quantizeFilter.setNumColors(16);
        quantizeFilter.setDither(true);
        quantizeFilter.setSerpentine(true);
        BufferedImage outputImage = quantizeFilter.filter(inputImage, null);

        // Extract palette from quantized image
        int[] rgb = outputImage.getRGB(0, 0, outputImage.getWidth(), outputImage.getHeight(), null, 0, outputImage.getWidth());
        int[] cmap = Arrays.stream(rgb).distinct().toArray();

        // Create indexed image with palette
        BufferedImage redrawn = new BufferedImage(outputImage.getWidth(), outputImage.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel(4, cmap.length, cmap, 0, false, -1, 0));
        Graphics2D g2d = redrawn.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, redrawn.getWidth(), redrawn.getHeight());
        g2d.drawImage(outputImage, 0, 0, null);
        g2d.dispose();
        return redrawn;
    }
}
