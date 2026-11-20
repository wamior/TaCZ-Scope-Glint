package ru.wamior.taczscopeglint.platform;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ModConfig;
import ru.wamior.taczscopeglint.Config;
import ru.wamior.taczscopeglint.ScopeGlint;
import ru.wamior.taczscopeglint.particle.ModParticles;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ScopeGlint.MODID)
public final class ScopeGlintForge {
    public ScopeGlintForge() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModParticles.register(bus);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
