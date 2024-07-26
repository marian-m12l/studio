package studio.database;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

public abstract class Thumbnailable {

	public abstract byte[] getImage();
	
	private byte[] thumbnailImage;
	
	public byte[] getThumbnail() {
		if ( thumbnailImage != null ) return thumbnailImage;
		

		try {
			BufferedImage source = ImageIO.read(new ByteArrayInputStream(getImage()));
			BufferedImage bimage = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
	
		    // Draw the image on to the buffered image
		    Graphics2D bGr = bimage.createGraphics();
		    bGr.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
		    	    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		    bGr.drawImage(source, 0, 0, 64, 64, 0, 0, source.getWidth(null), source.getHeight(null), null);
		    bGr.dispose();
	
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(bimage, "png", baos);
			
			thumbnailImage = baos.toByteArray();
		} catch (IOException e) {
			thumbnailImage = new byte[0];
		}
	    
		return thumbnailImage;
		
	}
	
}
