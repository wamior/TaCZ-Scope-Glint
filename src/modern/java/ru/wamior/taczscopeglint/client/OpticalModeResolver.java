package ru.wamior.taczscopeglint.client;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;

public final class OpticalModeResolver {
    private static final Map<BedrockAttachmentModel, Map<Integer, Boolean>> VIEWS = new WeakHashMap<>();
    public static boolean canGlint(ResourceLocation id, ClientAttachmentIndex index, CompoundTag tag, int mode, double minZoom) {
        ResourceLocation variantId = tag == null ? null : ResourceLocation.tryParse(tag.getString("Variant"));
        if (variantId != null && TimelessAPI.getCommonAttachmentVariantIndex(variantId)
                .map(variant -> variant.appliesTo(id)).orElse(false)) {
            ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(variantId).orElse(null);
            if (variant != null) {
                return ScopeMode.canGlint(variant.isScope(), variant.isSight(),
                        variant.getZoom() == null ? index.getZoom() : variant.getZoom(), variant.getViews(),
                        mode, minZoom, view -> opticalView(variant.getAttachmentModel(), view));
            }
        }
        return ScopeMode.canGlint(index.isScope(), index.isSight(), index.getZoom(), index.getViews(),
                mode, minZoom, view -> opticalView(index.getAttachmentModel(), view));
    }

    private static boolean opticalView(BedrockAttachmentModel model, int view) {
        if (model == null) return false;
        // View IDs and ocular IDs need not match (for example TaCZ's AMG magnifier).
        return VIEWS.computeIfAbsent(model, ignored -> new HashMap<>()).computeIfAbsent(view,
                selected -> HybridLens.isOptical(model.getIndexBones(), selected));
    }

    private OpticalModeResolver() {
    }
}
