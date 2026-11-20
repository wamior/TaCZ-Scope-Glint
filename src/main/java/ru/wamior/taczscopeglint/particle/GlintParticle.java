package ru.wamior.taczscopeglint.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Частица «блик оптики» (Scope Glint) — яркая белоснежная вспышка света от линзы снайперского прицела.
 * Видна с соседнего здания на больших дистанциях, а вблизи представляет собой
 * крупное выразительное белое оптическое свечение.
 */
@OnlyIn(Dist.CLIENT)
public class GlintParticle extends TextureSheetParticle {

    private static final int LIFETIME = 2;
    private final float baseSize;

    protected GlintParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);

        this.setSpriteFromAge(sprites);
        this.lifetime = LIFETIME;

        // Чистейший белоснежный цвет (#FFFFFF)
        this.rCol = 1.0f;
        this.gCol = 1.0f;
        this.bCol = 1.0f;
        this.alpha = 1.0f;

        // Базовый размер вспышки: аккуратный яркий блик на линзе прицела
        float scale = ru.wamior.taczscopeglint.Config.GLINT_SIZE_SCALE.get().floatValue();
        this.quadSize = 0.32f * scale;
        this.baseSize = this.quadSize;

        this.gravity = 0.0f;
        this.hasPhysics = false;
    }

    @Override
    public float getQuadSize(float partialTicks) {
        float size = this.quadSize;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            double dx = this.x - cam.x;
            double dy = this.y - cam.y;
            double dz = this.z - cam.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // Дистанционное увеличение:
            // На дистанциях блик увеличивается до ~1.0 блока,
            // чтобы быть четко заметным издалека, но не закрывать собой всю модель игрока!
            float distScale = (float) Math.min(dist / 35.0, 2.0);
            return size * (1.0f + distScale);
        }
        return size;
    }

    @Override
    public void tick() {
        super.tick();

        // Живая пульсация блика
        float ageRatio = (float) this.age / this.lifetime;
        float pulse = (float) Math.sin(ageRatio * Math.PI);
        this.quadSize = baseSize * (0.95f + 0.15f * pulse);

        if (this.age >= this.lifetime - 1) {
            this.alpha = 0.7f;
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        // Полная яркость (fullbright 0xF000F0) — сияет на максимум
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // ---- Factory ----

    @OnlyIn(Dist.CLIENT)
    public record Provider(SpriteSet sprites) implements ParticleProvider<net.minecraft.core.particles.SimpleParticleType> {
        @Override
        public Particle createParticle(
                net.minecraft.core.particles.SimpleParticleType type,
                ClientLevel level,
                double x, double y, double z,
                double dx, double dy, double dz
        ) {
            return new GlintParticle(level, x, y, z, sprites);
        }
    }
}
