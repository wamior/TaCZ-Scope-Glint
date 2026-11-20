package ru.wamior.taczscopeglint.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.client.resource.pojo.model.BonesItem;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class HybridLensTest {
    private static final Gson GSON = new Gson();

    private Map<String, BonesItem> bones(String json) {
        Map<String, BonesItem> result = new HashMap<>();
        for (var item : JsonParser.parseString(json).getAsJsonArray()) {
            // Cube UVs use TaCZ's custom serializer; this classifier only needs bone transforms.
            JsonObject transform = new JsonObject();
            for (String key : List.of("name", "parent", "pivot", "rotation")) {
                if (item.getAsJsonObject().has(key)) transform.add(key, item.getAsJsonObject().get(key));
            }
            BonesItem bone = GSON.fromJson(transform, BonesItem.class);
            result.put(bone.getName(), bone);
        }
        return result;
    }

    @Test void viewAndLensNumbersMayBeReversed() {
        var model = bones("""
                [{"name":"scope_view","pivot":[0,2,10]},
                 {"name":"scope_view_2","pivot":[0,4,10]},
                 {"name":"ocular_scope_2","pivot":[0,2,2]},
                 {"name":"ocular_sight","pivot":[0,4,2]}]
                """);
        assertTrue(HybridLens.isOptical(model, 1));
        assertFalse(HybridLens.isOptical(model, 2));
    }

    @Test void ambiguousMissingOrOffAxisDataDoesNotGlint() {
        var model = bones("""
                [{"name":"scope_view","pivot":[0,2,10]},
                 {"name":"ocular_scope","pivot":[0,2,2]},
                 {"name":"ocular_sight","pivot":[0,2,3]}]
                """);
        assertFalse(HybridLens.isOptical(model, 1));
        assertFalse(HybridLens.isOptical(model, 2));
        assertFalse(HybridLens.isOptical(bones("""
                [{"name":"scope_view","pivot":[0,2,10]},
                 {"name":"ocular_scope","pivot":[4,2,2]}]
                """), 1));
    }

    @Test void parentRotationIsIncluded() {
        var model = bones("""
                [{"name":"root","pivot":[0,0,0],"rotation":[0,0,45]},
                 {"name":"scope_view","parent":"root","pivot":[0,2,10]},
                 {"name":"ocular_scope","parent":"root","pivot":[0,2,2]},
                 {"name":"ocular_sight","parent":"root","pivot":[0,4,2]}]
                """);
        assertTrue(HybridLens.isOptical(model, 1));
    }

    @Test void malformedHierarchyFailsClosed() {
        assertFalse(HybridLens.isOptical(bones("""
                [{"name":"scope_view","parent":"scope_view","pivot":[0,2,10]}]
                """), 1));
    }

    @Test void actualUpstreamGunpackHybridModes() throws Exception {
        Map<String, List<Boolean>> expected = Map.of(
                "scope_amg_10", List.of(true, true, false),
                "scope_hamr", List.of(true, false),
                "scope_mk5hd", List.of(true, true, false),
                "scope_vudu", List.of(true, false),
                "scope_xm157", List.of(true, true));
        int jars = 0;
        try (var paths = Files.list(Path.of(System.getProperty("scopeglint.libs")))) {
            for (Path file : paths.filter(p -> p.getFileName().toString().endsWith("1.2.0-hotfix.jar")).toList()) {
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    for (var entry : expected.entrySet()) {
                        String display = "/display/attachments/" + entry.getKey() + "_display.json";
                        var displayEntry = zip.stream().filter(e -> e.getName().endsWith(display)).findFirst().orElseThrow();
                        JsonObject data = JsonParser.parseString(new String(zip.getInputStream(displayEntry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        String[] modelId = data.get("model").getAsString().split(":");
                        String suffix = "assets/" + modelId[0] + "/geo_models/" + modelId[1] + ".json";
                        var modelEntry = zip.stream().filter(e -> e.getName().endsWith(suffix)).findFirst().orElseThrow();
                        JsonObject model = JsonParser.parseString(new String(zip.getInputStream(modelEntry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        var modelBones = bones(model.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().get("bones").toString());
                        var views = data.getAsJsonArray("views");
                        assertEquals(entry.getValue().size(), views.size());
                        for (int mode = 0; mode < views.size(); mode++) {
                            assertEquals(entry.getValue().get(mode), HybridLens.isOptical(modelBones, views.get(mode).getAsInt()),
                                    file.getFileName() + ": " + entry.getKey() + " mode " + mode);
                        }
                    }
                }
                jars++;
            }
        }
        assertTrue(jars > 0, "The target's TaCZ dependency must be downloaded before tests");
    }
}
