package com.loohp.interactivechatdiscordsrvaddon.resource.fonts;

import java.awt.geom.AffineTransform;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.loohp.interactivechat.libs.org.apache.commons.io.FileUtils;
import com.loohp.interactivechat.libs.org.json.simple.JSONArray;
import com.loohp.interactivechat.libs.org.json.simple.JSONObject;
import com.loohp.interactivechat.libs.org.json.simple.parser.JSONParser;
import com.loohp.interactivechatdiscordsrvaddon.registies.ResourceRegistry;
import com.loohp.interactivechatdiscordsrvaddon.resource.ResourceManager;
import com.loohp.interactivechatdiscordsrvaddon.resource.fonts.LegacyUnicodeFont.GlyphSize;
import com.loohp.interactivechatdiscordsrvaddon.resource.textures.GeneratedTextureResource;
import com.loohp.interactivechatdiscordsrvaddon.resource.textures.TextureResource;

public class FontManager {
	
	public static final String DEFAULT_FONT = "minecraft:default";
	
	private ResourceManager manager;
	private Map<String, FontProvider> fonts;
	private Map<String, Map<String, File>> files;
	
	public FontManager(ResourceManager manager) {
		this.manager = manager;
		this.fonts = new HashMap<>();
		this.files = new HashMap<>();
	}
	
	@SuppressWarnings("unchecked")
	public void loadDirectory(String namespace, File root) {
		if (!root.exists() || !root.isDirectory()) {
			throw new IllegalArgumentException(root.getAbsolutePath() + " is not a directory.");
		}
		Map<String, File> fileList = files.get(namespace);
		if (fileList == null) {
			files.put(namespace, fileList = new HashMap<>());
		}
		JSONParser parser = new JSONParser();
		Map<String, FontProvider> fonts = new HashMap<>();
		Collection<File> files = FileUtils.listFiles(root, null, true);
		for (File file : files) {
			fileList.put(file.getName(), file);
		}
		for (File file : files) {
			if (file.getName().endsWith("json")) {
				try {
					String key = namespace + ":" + file.getName();
					key = key.substring(0, key.lastIndexOf("."));
					InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
					JSONObject rootJson = (JSONObject) parser.parse(reader);
					reader.close();
					List<MinecraftFont> providedFonts = new ArrayList<>();
					int index = -1;
					for (Object obj : (JSONArray) rootJson.get("providers")) {
						index++;
						JSONObject fontJson = (JSONObject) obj;
						try {
							switch (fontJson.get("type").toString()) {
							case "bitmap":
								String resourceLocation = fontJson.get("file").toString();
								int height = ((Number) fontJson.getOrDefault("height", 8)).intValue();
								int ascent = ((Number) fontJson.get("ascent")).intValue();
								List<String> chars = (List<String>) ((JSONArray) fontJson.get("chars")).stream().map(each -> each.toString()).collect(Collectors.toList());
								providedFonts.add(new BitmapFont(manager, null, resourceLocation, height, ascent, chars));
								break;
							case "legacy_unicode":
								String template = fontJson.get("template").toString();
								DataInputStream sizesInput = new DataInputStream(new BufferedInputStream(new FileInputStream(getFontResource(fontJson.get("sizes").toString()).getFile())));
								Map<String, GlyphSize> sizes = new HashMap<>();
								for (int i = 0;; i++) {
									try {
										byte b = sizesInput.readByte();
										byte start = (byte) ((b >> 4) & 15);
										byte end = (byte) (b & 15);
										sizes.put(new String(Character.toChars(i)), new GlyphSize(start, end));
									} catch (EOFException e) {
										break;
									}
								}
								sizesInput.close();
								providedFonts.add(new LegacyUnicodeFont(manager, null, sizes, template));
								break;
							case "ttf":
								resourceLocation = fontJson.get("file").toString();
								JSONArray shiftArray = (JSONArray) fontJson.get("shift");
								float leftShift = ((Number) shiftArray.get(0)).floatValue();
								float downShift = ((Number) shiftArray.get(0)).floatValue();
								AffineTransform shift = AffineTransform.getTranslateInstance(leftShift, downShift);
								float size = ((Number) fontJson.get("size")).floatValue();
								float oversample = ((Number) fontJson.get("oversample")).floatValue();
								String skip = fontJson.get("skip").toString();
								providedFonts.add(new TrueTypeFont(manager, null, resourceLocation, shift, size, oversample, skip));
								break;
							}
						} catch (Exception e) {
							throw new RuntimeException("Unable to load font provider " + index + " in " + file.getAbsolutePath(), e);
						}
					}
					FontProvider provider = new FontProvider(key, providedFonts);
					for (MinecraftFont mcFont : provider.getProviders()) {
						mcFont.setProvider(provider);
					}
					fonts.put(key, provider);
				} catch (Exception e) {
					new RuntimeException("Unable to load font " + file.getAbsolutePath(), e).printStackTrace();
				}
			}
		}
		this.fonts.putAll(fonts);
	}
	
	public void reloadFonts() {
		for (FontProvider provider : fonts.values()) {
			provider.reloadFonts();
		}
	}
	
	public TextureResource getFontResource(String resourceLocation) {
		String namespace;
		String key;
		if (resourceLocation.contains(":")) {
			namespace = resourceLocation.substring(0, resourceLocation.indexOf(":"));
			key = resourceLocation.substring(resourceLocation.indexOf(":") + 1);
		} else {
			namespace = ResourceRegistry.DEFAULT_NAMESPACE;
			key = resourceLocation;
		}
		
		Map<String, File> fileList = files.get(namespace);
		if (fileList == null) {
			return null;
		}
		File current0 = fileList.get(key);
		if (current0 != null && current0.exists()) {
			return new GeneratedTextureResource(current0);
		}
		File current1 = fileList.get(key.replace("font/", ""));
		if (current1 != null && current1.exists()) {
			return new GeneratedTextureResource(current1);
		}
		TextureResource resource = manager.getTextureManager().getTexture(resourceLocation, false);
		return resource;
	}
	
	public FontProvider getFontProviders(String resourceLocation) {
		if (!resourceLocation.contains(":")) {
			resourceLocation = ResourceRegistry.DEFAULT_NAMESPACE + ":" + resourceLocation;
		}
		FontProvider providers = fonts.getOrDefault(resourceLocation, resourceLocation.equals(DEFAULT_FONT) ? null : getFontProviders(DEFAULT_FONT));
		return providers;
	}
    
}
