package studio.database;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class JsonPack {
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof JsonPack && uuid.equals(((JsonPack)obj).uuid);
	}
	
	@SerializedName("uuid")
	private String uuid;
	
	@SerializedName("age_max")
	private int ageMax;
	
	@SerializedName("age_min")
	private int ageMin;
	
	@SerializedName("creation_date")
	private Date creationDate;
	
	@SerializedName("slug")
	private String slug; 
	
	@SerializedName("modification_date")
	private Date modificationDate;
	
	@SerializedName("duration")
	private int duration;
	
	@SerializedName("is_factory")
	private boolean isFactory;
	
	@SerializedName("title")
	private String title;
	
	@SerializedName("subtitle")
	private String subtitle;
	
	@SerializedName("night_mode_playable")
	private boolean nightMode;
	
	@SerializedName("keywords")
	private String keywords;
	
	@SerializedName("authors")
	private Map<String, Person> authors;
	
	@SerializedName("previews")
	private List<String> previews;
	
	@SerializedName("tellers")
	private Map<String, Person> tellers;

	@SerializedName("localized_infos")
	private Map<String, LocalizedInfos> localizedInfos = new HashMap<>();
	
	@SerializedName("size")
	private long size;
	
	
	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public String getSlug() {
		return slug;
	}
	
	public String getUuid() {
		return uuid;
	}

	public int getAgeMax() {
		return ageMax;
	}

	public int getAgeMin() {
		return ageMin;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public Date getModificationDate() {
		return modificationDate;
	}

	public int getDuration() {
		return duration;
	}

	public boolean isFactory() {
		return isFactory;
	}

	public String getTitle() {
		return title;
	}

	public String getSubtitle() {
		return subtitle;
	}

	public boolean isNightMode() {
		return nightMode;
	}

	public String getKeywords() {
		return keywords;
	}

	public Collection<Person> getAuthors() {
		return authors.values();
	}

	public List<String> getPreviews() {
		return previews;
	}

	public Collection<Person> getTellers() {
		return tellers.values();
	}
	
	public Map<String, LocalizedInfos> getLocalizedInfos() {
		return localizedInfos;
	}
	
	
	public String getResolvedTitle(Locale l) {
		String locale = l.toLanguageTag().replace("-", "_");
		return getTitle() != null ? getTitle() : 
			getLocalizedInfos().get(locale) != null ? localizedInfos.get(locale).getTitle() : 
				localizedInfos.values().isEmpty() ? localizedInfos.values().iterator().next().getTitle() : "";
	}
}