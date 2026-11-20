package ru.wamior.taczscopeglint.client;

import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class OpticalModeResolver {
    public static boolean canGlint(ResourceLocation id, ClientAttachmentIndex index, CompoundTag tag, int mode, double minZoom) {
        return ScopeMode.canGlint(index.isScope(), index.isSight(), index.getZoom(), null,
                mode, minZoom, view -> false);
    }

    private OpticalModeResolver() {
    }
}
