package ru.wamior.taczscopeglint.client;

import com.tacz.guns.client.resource.pojo.model.BonesItem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Finds the lens on the selected view's axis using TaCZ's original, unanimated model transforms. */
public final class HybridLens {
    private static final Pattern OCULAR = Pattern.compile("ocular(?:_scope|_sight)?(?:_\\d+)?");
    // Half a model pixel off-axis is allowed; uncertain or overlapping lenses fail closed.
    private static final float MAX_OFFSET_SQUARED = 0.25f;

    public static boolean isOptical(Map<String, BonesItem> bones, int view) {
        String name = "scope_view" + (view == 1 ? "" : "_" + view);
        Matrix4f camera = transform(bones, name, new HashSet<>());
        if (camera == null) return false;
        camera.invert();
        float bestOptic = Float.POSITIVE_INFINITY;
        float bestSight = Float.POSITIVE_INFINITY;
        for (String node : bones.keySet()) {
            if (!OCULAR.matcher(node).matches()) continue;
            Matrix4f lens = transform(bones, node, new HashSet<>());
            if (lens == null) continue;
            Vector3f position = lens.getTranslation(new Vector3f()).mulPosition(camera);
            if (!position.isFinite() || position.z >= 0) continue;
            float distance = position.x * position.x + position.y * position.y;
            if (node.equals("ocular_scope") || node.startsWith("ocular_scope_")) bestOptic = Math.min(bestOptic, distance);
            else bestSight = Math.min(bestSight, distance);
        }
        return bestOptic <= MAX_OFFSET_SQUARED && bestOptic + 0.000001f < bestSight;
    }

    private static Matrix4f transform(Map<String, BonesItem> bones, String name, Set<String> path) {
        BonesItem bone = bones.get(name);
        if (bone == null || !vector(bone.getPivot()) || !path.add(name)) return null;
        List<Float> pivot = bone.getPivot();
        Matrix4f matrix;
        String parent = bone.getParent();
        if (parent != null) {
            matrix = transform(bones, parent, path);
            BonesItem parentBone = bones.get(parent);
            if (matrix == null || parentBone == null || !vector(parentBone.getPivot())) return null;
            List<Float> base = parentBone.getPivot();
            matrix.translate(pivot.get(0) - base.get(0), base.get(1) - pivot.get(1), pivot.get(2) - base.get(2));
        } else {
            matrix = new Matrix4f().translation(pivot.get(0), 24 - pivot.get(1), pivot.get(2));
        }
        if (bone.getRotation() != null) {
            if (!vector(bone.getRotation())) return null;
            List<Float> rotation = bone.getRotation();
            float radians = (float) (Math.PI / 180);
            matrix.rotateZYX(rotation.get(2) * radians, rotation.get(1) * radians, rotation.get(0) * radians);
        }
        return matrix;
    }

    private static boolean vector(List<Float> value) {
        return value != null && value.size() >= 3 && value.subList(0, 3).stream()
                .allMatch(component -> component != null && Float.isFinite(component));
    }

    private HybridLens() {
    }
}
