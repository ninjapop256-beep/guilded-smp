package com.guildedsmp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.guildedsmp.items.CustomItem;
import com.guildedsmp.pack.SoundService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static validation of the resource pack in /resourcepack (no game client involved). */
class ResourcePackTest {

    private static final Path PACK = Path.of("resourcepack");
    private static final Path ASSETS = PACK.resolve("assets");

    private static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** "ns:path" -> assets/ns/<folder>/path<ext> */
    private static Path resource(String id, String folder, String ext) {
        String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return ASSETS.resolve(ns).resolve(folder).resolve(path + ext);
    }

    @Test
    void everyJsonFileParses() throws IOException {
        try (Stream<Path> files = Files.walk(PACK)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json") || f.toString().endsWith(".mcmeta")).toList()) {
                JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void packMetaTargetsMinecraft262() throws IOException {
        JsonObject pack = json(PACK.resolve("pack.mcmeta")).getAsJsonObject("pack");
        JsonArray min = pack.getAsJsonArray("min_format");
        assertEquals(88, min.get(0).getAsInt(), "resource pack format 88 = Java Edition 26.2");
        assertEquals(88, pack.get("max_format").getAsInt());
        assertFalse(pack.has("pack_format"), "only needed for pre-1.21.9 clients");
        JsonArray desc = pack.getAsJsonArray("description");
        assertEquals("#00FFFF", desc.get(0).getAsJsonObject().get("color").getAsString());
        assertEquals("#000000", desc.get(2).getAsJsonObject().get("color").getAsString());
        BufferedImage icon = ImageIO.read(PACK.resolve("pack.png").toFile());
        assertEquals(icon.getWidth(), icon.getHeight());
    }

    @Test
    void everyCustomItemIsWiredThroughToAnOriginalTexture() throws IOException {
        for (CustomItem item : CustomItem.values()) {
            String base = item.base.getKey().getKey();
            Path definition = ASSETS.resolve("minecraft/items/" + base + ".json");
            assertTrue(Files.isRegularFile(definition), "missing " + definition);
            JsonObject model = json(definition).getAsJsonObject("model");
            assertEquals("minecraft:select", model.get("type").getAsString());
            assertEquals("minecraft:custom_model_data", model.get("property").getAsString());
            assertEquals("minecraft:item/" + base, model.getAsJsonObject("fallback").get("model").getAsString(),
                    "vanilla look for normal items and players without the pack");
            String modelId = null;
            for (JsonElement c : model.getAsJsonArray("cases")) {
                if (c.getAsJsonObject().get("when").getAsString().equals(item.modelKey())) {
                    modelId = c.getAsJsonObject().getAsJsonObject("model").get("model").getAsString();
                }
            }
            assertNotNull(modelId, item.modelKey() + " not selected in " + definition);
            Path modelFile = resource(modelId, "models", ".json");
            assertTrue(Files.isRegularFile(modelFile), "missing " + modelFile);
            String texture = json(modelFile).getAsJsonObject("textures").get("layer0").getAsString();
            Path png = resource(texture, "textures", ".png");
            BufferedImage img = ImageIO.read(png.toFile());
            assertNotNull(img, "unreadable " + png);
            assertEquals(16, img.getWidth(), png.toString());
            assertEquals(0, img.getHeight() % 16, png.toString());
            if (img.getHeight() > 16) {
                assertTrue(Files.isRegularFile(Path.of(png + ".mcmeta")), "animated texture needs .mcmeta: " + png);
            }
            String parent = json(modelFile).get("parent").getAsString();
            assertEquals(item.legendary ? "minecraft:item/handheld" : "minecraft:item/generated", parent);
        }
    }

    @Test
    void everySoundCueExistsAsMonoOggVorbisWithASubtitle() throws IOException {
        JsonObject sounds = json(ASSETS.resolve("guildedsmp/sounds.json"));
        JsonObject lang = json(ASSETS.resolve("guildedsmp/lang/en_us.json"));
        for (SoundService.Cue cue : SoundService.Cue.values()) {
            String event = cue.customKey.substring("guildedsmp:".length());
            JsonObject entry = sounds.getAsJsonObject(event);
            assertNotNull(entry, "sounds.json lacks " + event);
            assertTrue(lang.has(entry.get("subtitle").getAsString()), "subtitle text for " + event);
            for (JsonElement s : entry.getAsJsonArray("sounds")) {
                String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                Path ogg = resource(name, "sounds", ".ogg");
                byte[] data = Files.readAllBytes(ogg);
                assertEquals("OggS", new String(data, 0, 4, StandardCharsets.US_ASCII), ogg.toString());
                int vorbis = indexOf(data, new byte[]{0x01, 'v', 'o', 'r', 'b', 'i', 's'});
                assertTrue(vorbis > 0, "Vorbis stream: " + ogg);
                assertEquals(1, data[vorbis + 11], "mono (required for positional sounds): " + ogg);
            }
        }
    }

    @Test
    void noOrphanOrMissingReferences() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ASSETS.resolve("guildedsmp/textures"))) {
            for (Path png : files.filter(f -> f.toString().endsWith(".png")).toList()) {
                String id = png.getFileName().toString().replace(".png", "").toLowerCase(Locale.ROOT);
                if (CustomItem.byId(id).isEmpty()) {
                    problems.add("texture with no item: " + png);
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.toString());
    }

    private static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
