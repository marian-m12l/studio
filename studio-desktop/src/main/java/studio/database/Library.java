package studio.database;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.reader.archive.ArchiveStoryPackReader;
import studio.core.v1.reader.binary.BinaryStoryPackReader;
import studio.core.v1.reader.fs.FsStoryPackReader;

public class Library {

	public static final String TOKEN_URL			= "https://server-auth-prod.lunii.com/guest/create";
	public static final String DATABASE_URL 		= "https://server-data-prod.lunii.com/v2/packs";
	public  static final String RESOURCE_URL		= "https://storage.googleapis.com/lunii-data-prod";
	
	private JsonResponse officalPacksDatabase;
		
	private static volatile Library instance;
	
	private String libraryPath;
	
	public static Library getInstance(String path) {
        if (instance != null && instance.libraryPath.equals(path)) {
            return instance;
        }
        synchronized(Library.class) {
            if (instance == null || !instance.libraryPath.equals(path)) {
                instance = new Library(path);
            }
            return instance;
        }
    }
	
	private Library(String path) {
		officalPacksDatabase = null;
		this.libraryPath = path;
	}
	
	public String getLibraryPath() {
		return libraryPath;
	}
	
	public String getFolderForUUID(String uuid) {
		try {
			return Files.list(Paths.get(libraryPath)).filter( (path) -> {
				Optional<JsonPack> res = this.readPackFile(path);
				if ( res.isPresent() && res.get().getUuid().equals(uuid)) return true;
				return false;
			}).findFirst().get().toString();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	private Optional<JsonPack> readPackFile(Path path) {
        // Handle all file formats
        if (path.toString().endsWith(".zip")) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                System.out.println("Reading archive pack metadata in folder " + path.toString());
                ArchiveStoryPackReader packReader = new ArchiveStoryPackReader();
                StoryPackMetadata meta = packReader.readMetadata(fis);
                if (meta != null) {
                	System.out.println("Pack found for UUID " + meta.getUuid());
                    //return Optional.of(new LibraryPack(path, Files.getLastModifiedTime(path).toMillis() , meta));
                	Optional<JsonPack> res = officalPacksDatabase.getPacks().stream().filter( (JsonPack p) -> p.getUuid().equalsIgnoreCase(meta.getUuid())).findFirst();
                	if ( !res.isPresent() ) {
                		System.out.println("Package not found for UUID " + meta.getUuid());
                	}
                	return res;
                }
                System.out.println("No pack found...");
                return Optional.empty();
            } catch (IOException e) {
            	System.out.println("Failed to read archive-format pack " + path.toString() + " from local library");
            	//e.printStackTrace();
                return Optional.empty();
            }
        } else if (path.toString().endsWith(".pack")) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
            	System.out.println("Reading raw pack metadata in folder " + path.toString());
                BinaryStoryPackReader packReader = new BinaryStoryPackReader();
                StoryPackMetadata meta = packReader.readMetadata(fis);
                if ( meta != null && meta.getUuid() != null ) {
                	System.out.println("Pack found for UUID " + meta.getUuid());
                	Optional<JsonPack> res = officalPacksDatabase.getPacks()
                							.stream()
                							.filter( (JsonPack p) -> 
                								p.getUuid().equalsIgnoreCase(meta.getUuid()))
                							.findFirst();
                	if ( !res.isPresent() ) {
                		System.out.println("Package not found for UUID " + meta.getUuid());
                	}
                	return res;
                }
                System.out.println("No pack found...");
                return Optional.empty();
            } catch (IOException e) {
            	System.out.println("Failed to read raw format pack " + path.toString() + " from local library");
            	//e.printStackTrace();
                return Optional.empty();
            }
        } else if (Files.isDirectory(path)) {
            try {
            	System.out.println("Reading FS pack metadata in folder " + path.toString());
                FsStoryPackReader packReader = new FsStoryPackReader();
                StoryPackMetadata meta = packReader.readMetadata(path);
                if (meta != null) {
                	System.out.println("Pack found for UUID " + meta.getUuid());
                	Optional<JsonPack> res = officalPacksDatabase.getPacks().stream().filter( (JsonPack p) -> p.getUuid().equalsIgnoreCase(meta.getUuid())).findFirst();
                	if ( !res.isPresent() ) {
                		System.out.println("Package not found for UUID " + meta.getUuid());
                	}
                	return res;
                }
                System.out.println("No pack found...");
                return Optional.empty();
            } catch (Exception e) {
            	System.out.println("Failed to read FS format pack " + path.toString() + " from local library");
            	//e.printStackTrace();
                return Optional.empty();
            }
        }

        // Ignore other files
        return Optional.empty();
    }

	
	
	public CompletableFuture<?> refreshDatabase() {		
			officalPacksDatabase = null;
			return getPacks();
	}
	
	
	public CompletableFuture<Void> fetchFromServer() {
		
			return CompletableFuture.runAsync( () -> {
				if ( officalPacksDatabase != null ) return;
				try {
				URL getToken = new URL(TOKEN_URL);
				InputStream stream = getToken.openStream();
				
				JsonObject response = new Gson().fromJson(new InputStreamReader(stream), JsonObject.class);
				
				String token = response.getAsJsonObject("response").getAsJsonObject("token").get("server").getAsString();
				
				URL getDatabase = new URL(DATABASE_URL);
				URLConnection connection = getDatabase.openConnection();
				connection.addRequestProperty("X-AUTH-TOKEN", token);
				
				GsonBuilder builder = new GsonBuilder();
				builder.registerTypeAdapter(Date.class, new JsonDeserializer<Date>() {

					@Override
					public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
							throws JsonParseException {
						return new Date(json.getAsLong());
					}
					
				});
				
				Gson gson = builder.create();
				officalPacksDatabase = gson.fromJson(new InputStreamReader(connection.getInputStream()), JsonResponse.class);
				
				System.out.println(officalPacksDatabase.getPacks().stream().map( (p) -> p.getUuid()).toList());
		} catch(NullPointerException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		});
		
		
	}
	
	public Optional<JsonPack> getPackForUUID(String uuid) {
		if ( officalPacksDatabase == null ) return null;
		return officalPacksDatabase.getPacks().stream().filter( (pack) -> pack.getUuid().equalsIgnoreCase(uuid)).findAny();
	}
	
	public CompletableFuture<List<JsonPack>> getPacks() {
		return fetchFromServer().thenComposeAsync( (Void v) -> {
			return CompletableFuture.supplyAsync(() -> {
				try {
					return Files.list(Paths.get(libraryPath))
							.map( this::readPackFile )
							.filter( Optional::isPresent )
							.map( Optional::get )
							.toList();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return new ArrayList<JsonPack>();
			});
		});
	}
	
	protected class JsonResponse {
		
		@SerializedName("response")
		private Map<String,JsonPack> packs;
		
		public Collection<JsonPack> getPacks() {
			return packs.values();
		}
		
		
	}
}
