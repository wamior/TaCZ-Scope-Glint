package ru.wamior.taczscopeglint.platform;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.VersionChecker;
import ru.wamior.taczscopeglint.Config;
import ru.wamior.taczscopeglint.ScopeGlint;
import ru.wamior.taczscopeglint.client.ScopeGlintClientHandler;
import ru.wamior.taczscopeglint.client.UpdateNotice;
import ru.wamior.taczscopeglint.particle.GlintParticle;
import ru.wamior.taczscopeglint.particle.ModParticles;
import net.minecraftforge.event.TickEvent;

public final class ClientEvents {
    @EventBusSubscriber(modid = ScopeGlint.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void particles(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.SCOPE_GLINT.get(), GlintParticle.Provider::new);
        }
    }

    @EventBusSubscriber(modid = ScopeGlint.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private static int ticks;
        private static boolean checked;

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            ScopeGlintClientHandler.onClientTick();
            if (checked || !Config.UPDATE_NOTICES.get() || Minecraft.getInstance().player == null || ++ticks % 20 != 0) return;
            ModList.get().getModContainerById(ScopeGlint.MODID).ifPresent(container -> {
                VersionChecker.CheckResult result = VersionChecker.getResult(container.getModInfo());
                if (result.status() == VersionChecker.Status.PENDING) return;
                checked = true;
                if (result.status() == VersionChecker.Status.OUTDATED || result.status() == VersionChecker.Status.BETA_OUTDATED) {
                    UpdateNotice.show(Minecraft.getInstance().player, container.getModInfo().getVersion().toString(),
                            String.valueOf(result.target()), result.url());
                }
            });
        }
    }

    private ClientEvents() {
    }
}
