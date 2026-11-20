package ru.wamior.taczscopeglint;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Должен ли наблюдатель сам находиться в зуме/прицеле, чтобы видеть блик? */
    public static final ModConfigSpec.BooleanValue REQUIRE_OBSERVER_ZOOM = BUILDER
            .comment("If true, a player can ONLY see other players' scope glints when they themselves " +
                     "are in zoom / aiming down sights (or using a spyglass). If false, glints are visible even from hip/empty hands. Default: true")
            .define("requireObserverZoom", true);

    public static final ModConfigSpec.BooleanValue UPDATE_NOTICES = BUILDER
            .comment("Show a clickable update notice once per game launch. Uses the loader's built-in update checker.")
            .define("updateNotices", true);

    /** Минимальная текущая кратность оптического прицела для активации блика (> этого значения). */
    public static final ModConfigSpec.DoubleValue MIN_ZOOM_THRESHOLD = BUILDER
            .comment("Minimum current optical scope zoom multiplier to trigger the glint effect (strictly greater than this value). " +
                     "Only TaCZ attachments marked scope=true can glint. Collimator/red-dot/holographic sights without scope=true never glint, regardless of ADS zoom. Default: 1.5")
            .defineInRange("minZoomThreshold", 1.5, 1.0, 64.0);

    /** Радиус в блоках, в котором другие игроки видят блик. */
    public static final ModConfigSpec.DoubleValue GLINT_VISIBLE_RANGE = BUILDER
            .comment("The maximum distance (in blocks) at which other players can see the scope glint. Default: 128.0")
            .defineInRange("glintVisibleRange", 128.0, 8.0, 512.0);

    /** Максимальный угол конуса видимости блика (в градусах от взгляда снайпера к наблюдателю). */
    public static final ModConfigSpec.DoubleValue MAX_GLINT_ANGLE = BUILDER
            .comment("Maximum angle (in degrees) between the sniper's aim direction and the observer " +
                     "for the glint to be visible. If the sniper looks away beyond this angle, glint is hidden. Default: 45.0")
            .defineInRange("maxGlintAngle", 45.0, 5.0, 180.0);

    /** Масштаб размера частицы блика. */
    public static final ModConfigSpec.DoubleValue GLINT_SIZE_SCALE = BUILDER
            .comment("Size scaling multiplier for the glint particle flare. Default: 1.0")
            .defineInRange("glintSizeScale", 1.0, 0.1, 5.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
