package studio.database;

import java.net.URL;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.google.gson.annotations.SerializedName;

public class LocalizedInfos extends Thumbnailable {
	
	protected class JsonImage {
		@SerializedName("image_url")
		private String url;
		
		public String getUrl() {
			return url;
		}
		
	}
	
	@SerializedName("description")
	private String description;
	
	@SerializedName("image")
	private JsonImage image;
	
	@SerializedName("subtitle")
	private String subtitle;
	
	@SerializedName("title")
	private String title;
	
	@SerializedName("previews")
	private List<String> previews;

	public String getDescription() {
		return description;
	}

	public String getImageUrl() {
		return image.getUrl();
	}

	public String getSubtitle() {
		return subtitle;
	}

	public String getTitle() {
		return title;
	}

	public List<String> getPreviews() {
		return previews;
	}
	
	private byte[] imageData;
	
	
	public boolean hasFetchImage() {
		return imageData != null;
	}
	
	public byte[] getImage() {
		if ( this.imageData != null ) return imageData;
		try {
			
			URL url = new URL(Library.RESOURCE_URL + this.getImageUrl().replaceAll("\\s", "%20"));
			return imageData = IOUtils.toByteArray(url);
		} catch (Exception e) {
		}
		return new byte[0];
		
	}
	
}