package ru.wamior.taczscopeglint.platform;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import ru.wamior.taczscopeglint.Config;
import ru.wamior.taczscopeglint.ScopeGlint;
import ru.wamior.taczscopeglint.particle.ModParticles;
import net.neoforged.fml.ModContainer;

@Mod(ScopeGlint.MODID)
public final class ScopeGlintNeoForge {
    public ScopeGlintNeoForge(IEventBus bus, ModContainer container) {
        ModParticles.register(bus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
