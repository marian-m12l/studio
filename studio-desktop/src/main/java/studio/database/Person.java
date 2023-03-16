package studio.database;

import java.net.URL;

import org.apache.commons.io.IOUtils;

import com.google.gson.annotations.SerializedName;

public class Person extends Thumbnailable {
	@SerializedName("gender")
	private String gender;
	
	@SerializedName("name")
	private String name;
	
	@SerializedName("image")
	private String image;

	public String getGender() {
		return gender;
	}

	public String getName() {
		return name;
	}

	public String getImageUrl() {
		return image;
	}
	
	private byte[] imageData;
	
	public byte[] getImage() {
		if ( this.imageData != null ) return imageData;
		try {
			URL url = new URL(Library.RESOURCE_URL + this.image);
			return imageData = IOUtils.toByteArray(url);
		} catch (Exception e) {
		}
		return new byte[0];
	}
	
}