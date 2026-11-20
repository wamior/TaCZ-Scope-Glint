package ru.wamior.taczscopeglint.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class GlintVisibilityTest {
    private Function<BlockPos, VoxelShape> world(VoxelShape obstacle) {
        return pos -> pos.equals(new BlockPos(1, 0, 0)) ? obstacle : Shapes.empty();
    }

    @Test void wallBetweenCameraAndLensHidesGlint() {
        assertFalse(GlintVisibility.isVisible(new Vec3(0.5, 0.5, 0.5), new Vec3(2.5, 0.5, 0.5), world(Shapes.block())));
    }

    @Test void lensInsideWallIsNotMovedOntoItsOutsideFace() {
        assertFalse(GlintVisibility.isVisible(new Vec3(2.01, 0.5, 0.5), new Vec3(1.99, 0.5, 0.5), world(Shapes.block())));
        assertFalse(GlintVisibility.isVisible(new Vec3(1.1, 0.5, 0.5), new Vec3(1.9, 0.5, 0.5), world(Shapes.block())));
    }

    @Test void partialCoverUsesItsActualShapeRatherThanWholeBlock() {
        var level = world(Shapes.box(0, 0, 0, 1, 0.5, 1));
        assertFalse(GlintVisibility.isVisible(new Vec3(0.5, 0.25, 0.5), new Vec3(2.5, 0.25, 0.5), level));
        assertTrue(GlintVisibility.isVisible(new Vec3(0.5, 0.75, 0.5), new Vec3(2.5, 0.75, 0.5), level));
    }

    @Test void emptyVisualShapesDoNotBlockTheRay() {
        assertTrue(GlintVisibility.isVisible(new Vec3(0.5, 0.5, 0.5), new Vec3(2.5, 0.5, 0.5), world(Shapes.empty())));
    }

    @Test void forwardLensOffsetCannotEscapeCoverAroundTheShooter() {
        assertFalse(GlintVisibility.isVisible(new Vec3(1.9, 0.5, 0.5), new Vec3(2.2, 0.5, 0.5), world(Shapes.block())));
    }
}
