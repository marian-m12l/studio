package studio.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
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

import studio.core.v1.model.StoryPack;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.reader.archive.ArchiveStoryPackReader;
import studio.core.v1.reader.binary.BinaryStoryPackReader;
import studio.core.v1.reader.fs.FsStoryPackReader;
import studio.core.v1.utils.PackAssetsCompression;
import studio.core.v1.writer.fs.FsStoryPackWriter;

public class Library {

	public static final String TOKEN_URL			= "https://server-auth-prod.lunii.com/guest/create";
	public static final String DATABASE_URL 		= "https://server-data-prod.lunii.com/v2/packs";
	public static final String RESOURCE_URL			= "https://storage.googleapis.com/lunii-data-prod";
	
	private JsonResponse packsDatabase;
	
	private JsonResponse localDatabase;
		
	private static volatile Library instance;
	
	private static Gson gson;
	
	private String libraryPath;
	
	static {
		GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(Date.class, new JsonDeserializer<Date>() {

			@Override
			public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
					throws JsonParseException {
				return new Date(json.getAsLong());
			}
			
		});
		builder.setPrettyPrinting();
		
		
		gson = builder.create();
		
	}
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
		packsDatabase = null;
		this.libraryPath = path;
		localDatabase = new JsonResponse();
		localDatabase.packs = new HashMap<>();
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
	
	
	private JsonPack fromEnrichedData(StoryPackMetadata metaData) {
		JsonObject obj = new JsonObject();
		
		
		obj.addProperty("title", metaData.getTitle());
		obj.addProperty("description", metaData.getDescription());
		obj.addProperty("uuid", metaData.getUuid());
		obj.addProperty("version", metaData.getVersion());
		return gson.fromJson(obj, JsonPack.class);
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
                	Optional<JsonPack> res = packsDatabase.getPacks().stream().filter( (JsonPack p) -> p.getUuid().equalsIgnoreCase(meta.getUuid())).findFirst();
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
                	Optional<JsonPack> res = packsDatabase.getPacks()
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
                	Optional<JsonPack> res = packsDatabase.getPacks().stream().filter( (JsonPack p) -> p.getUuid().equalsIgnoreCase(meta.getUuid())).findFirst();
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
			packsDatabase = null;
			return getPacks();
	}
	
	
	public CompletableFuture<Void> loadDatabase() {
		
			return CompletableFuture.runAsync( () -> {
				if ( packsDatabase != null ) return;
				try {
				URL getToken = new URL(TOKEN_URL);
				InputStream stream = getToken.openStream();
				
				JsonObject response = new Gson().fromJson(new InputStreamReader(stream), JsonObject.class);
				
				String token = response.getAsJsonObject("response").getAsJsonObject("token").get("server").getAsString();
				
				URL getDatabase = new URL(DATABASE_URL);
				URLConnection connection = getDatabase.openConnection();
				connection.addRequestProperty("X-AUTH-TOKEN", token);
				
				packsDatabase = gson.fromJson(new InputStreamReader(connection.getInputStream()), JsonResponse.class);
				System.out.println();
				System.out.println("Official database from Lunii server repo contains packages : ");
				
				String locale = Locale.getDefault().toLanguageTag().replaceAll("-", "_");
				packsDatabase.getPacks().stream().forEach( pack -> {
					String packTitle;
					if ( pack.getTitle() != null ) packTitle = pack.getTitle();
					else {
						packTitle = pack.getLocalizedInfos().get(locale) != null ? pack.getLocalizedInfos().get(locale).getTitle() : null;
						Iterator<Entry<String, LocalizedInfos>> it = pack.getLocalizedInfos().entrySet().iterator();
						while ( packTitle == null && it.hasNext() ) {
							Entry<String, LocalizedInfos> entry = it.next();
							packTitle = entry.getValue().getTitle() + " - (" + entry.getKey() + ")";
						}
					}
					System.out.println(pack.getUuid() + " - " + packTitle);
				});
				
				try(FileInputStream fis = new FileInputStream(Path.of(this.libraryPath, "unofficials.json").toFile())) {
					localDatabase = gson.fromJson(new InputStreamReader(fis), JsonResponse.class);
					if (localDatabase == null || localDatabase.packs == null ) {
						localDatabase = new JsonResponse();
						localDatabase.packs = new HashMap<>();
					}
					
					System.out.println("Unofficial database from Lunii server repo contains packages : ");
					localDatabase.getPacks().stream().forEach( pack -> {
						String packTitle;
						if ( pack.getTitle() != null ) packTitle = pack.getTitle();
						else {
							packTitle = pack.getLocalizedInfos().get(locale) != null ? pack.getLocalizedInfos().get(locale).getTitle() : null;
							Iterator<Entry<String, LocalizedInfos>> it = pack.getLocalizedInfos().entrySet().iterator();
							while ( packTitle == null && it.hasNext() ) {
								Entry<String, LocalizedInfos> entry = it.next();
								packTitle = entry.getValue().getTitle() + " - (" + entry.getKey() + ")";
							}
						}
						System.out.println(pack.getUuid() + " - " + packTitle);
					});
					
					localDatabase.packs.entrySet().forEach( (Entry<String, JsonPack> entry) -> {
						packsDatabase.packs.put(entry.getKey(), entry.getValue());
					});
				} catch(IOException e) {
					e.printStackTrace();
					
				}
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
		if ( packsDatabase == null ) return null;
		return packsDatabase.getPacks().stream().filter( (pack) -> pack.getUuid().equalsIgnoreCase(uuid)).findAny();
	}
	
	
	public CompletableFuture<List<JsonPack>> getAllLuniiPacks() {
		return loadDatabase().thenComposeAsync( (Void v) -> CompletableFuture.supplyAsync( () -> packsDatabase.getPacks().stream().toList()));
	}
	
	public CompletableFuture<List<JsonPack>> getPacks() {
		return loadDatabase().thenComposeAsync( (Void v) -> {
			return CompletableFuture.supplyAsync(() -> {
				try {
					return Files.list(Paths.get(libraryPath))
							.map( this::readPackFile )
							.filter( Optional::isPresent )
							.map( Optional::get )
							.sorted((o1, o2) -> o1.getResolvedTitle(Locale.getDefault()).compareTo(o2.getResolvedTitle(Locale.getDefault())))
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
	

	public boolean tryToImport(Path path) {
		if ( path.toString().endsWith(".zip") ) {
			ArchiveStoryPackReader packReader = new ArchiveStoryPackReader();
			try(FileInputStream fis = new FileInputStream(path.toFile())) {
				StoryPack pack = packReader.read(fis);
				fis.close();
				
				//StoryPack packWithPreparedAssets = PackAssetsCompression.withPreparedAssetsFirmware2dot4(pack);
				
				
				Optional<JsonPack> jsonPack = this.getPackForUUID(pack.getUuid());
				if ( jsonPack.isEmpty() ) {
					String packTitle = pack.getEnriched() != null ? pack.getEnriched().getTitle().toLowerCase() : null;
					if ( packTitle == null ) {
						return false;
					}
					
					jsonPack = this.packsDatabase.getPacks().parallelStream().filter( (final JsonPack p) ->{
						System.out.println("Current pack main title : " + p.getTitle());
						System.out.println("All titles :");
						String concat = p.getLocalizedInfos().values().stream().map((locinfo) -> locinfo.getTitle()).reduce( (val1, val2) -> val1 + ", " + val2).orElseGet(() -> "");
						System.out.println(concat);
						//String concat = String.join("," , titles);
						return 
								(p.getTitle() != null && p.getTitle().toLowerCase().contains(packTitle)) ||
								p.getLocalizedInfos().values().stream().anyMatch( (locInfo) -> {
									return locInfo.getTitle() != null && locInfo.getTitle().toLowerCase().contains(packTitle);
								});
					}
//						
						//p.getLocalizedInfos().entrySet().stream().anyMatch( (entry) -> entry.getValue().getTitle() != null && entry.getValue().toString().equalsIgnoreCase(pack.getEnriched().getTitle()))
						
						
					).findAny();
				}
				if ( jsonPack.isPresent() ) {
					pack.setUuid(jsonPack.get().getUuid());
				} else {
					
					try(FileInputStream fis2 = new FileInputStream(path.toFile())) {
						StoryPackMetadata metaData = packReader.readMetadata(fis2);
						JsonPack customJsonPack = fromEnrichedData(metaData);
						String random = metaData.getUuid();
						this.localDatabase.packs.put(random, customJsonPack);
						CompletableFuture.runAsync( () -> {
							try {
								File unofficialsFile = Path.of(this.libraryPath, "unofficials.json").toFile();
								FileWriter fileWriter = new FileWriter(unofficialsFile);
								gson.toJson(this.localDatabase, fileWriter);
								fileWriter.flush();
								fileWriter.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						});
						this.packsDatabase.packs.put(random, customJsonPack);
					}catch(Exception e) {
						e.printStackTrace();
					}
				}
				
				if ( Path.of(this.libraryPath, pack.getUuid()).toFile().exists() ) {
					return false;
				}
				
				//this.officalPacksDatabase.getPacks().stream().filter((JsonPack p) -> p.getUuid().equalsIgnoreCase(meta.getUuid())).findFirst();
				FsStoryPackWriter writer = new FsStoryPackWriter();
				
				Path outputPath = Path.of(this.libraryPath, pack.getUuid());
				writer.write(pack, outputPath);
				
				
				
				return true;
				
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if ( path.toString().endsWith(".pack") ) {
			BinaryStoryPackReader packReader = new BinaryStoryPackReader();
            FileInputStream fis;
			try {
				fis = new FileInputStream(path.toFile());

	            StoryPack storyPack = packReader.read(fis);
	            fis.close();
	
	            // Prepare assets (RLE-encoded BMP, audio must already be MP3)
	            StoryPack packWithPreparedAssets = PackAssetsCompression.withPreparedAssetsFirmware2dot4(storyPack);
	
	            FsStoryPackWriter writer = new FsStoryPackWriter();
	            Path outputPath = Path.of(this.libraryPath, storyPack.getUuid());
                       	
            	writer.write(packWithPreparedAssets, outputPath);
            	return true;
            } catch (Exception e) {
            	e.printStackTrace();
            }
            
		} else if ( Files.isDirectory(path) ) {
			FsStoryPackReader packReader = new FsStoryPackReader();
			StoryPack pack;
			try {
				pack = packReader.read(path);
				Path outputPath = Path.of(this.libraryPath, pack.getUuid());
				Files.move(path, outputPath);
				return true;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return false;
		
	}
}
