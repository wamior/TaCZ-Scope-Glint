package ru.wamior.taczscopeglint.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import ru.wamior.taczscopeglint.ScopeGlint;
import java.util.function.Supplier;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(
            net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES, ScopeGlint.MODID);
    public static final Supplier<SimpleParticleType> SCOPE_GLINT = TYPES.register("scope_glint", () -> new SimpleParticleType(true));

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    private ModParticles() {
    }
}
