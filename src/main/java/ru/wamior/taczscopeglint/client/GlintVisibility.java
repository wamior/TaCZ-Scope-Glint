package ru.wamior.taczscopeglint.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import java.util.function.Function;

/** Never moves a glint out of cover. Tests the full sight line against actual block shapes. */
public final class GlintVisibility {
    public static boolean isVisible(BlockGetter level, Vec3 camera, Vec3 lens) {
        return isVisible(camera, lens, pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof LeavesBlock || state.getBlock() instanceof BushBlock
                    || state.getBlock() instanceof IronBarsBlock) return Shapes.empty();
            return state.getVisualShape(level, pos, CollisionContext.empty());
        });
    }

    static boolean isVisible(Vec3 camera, Vec3 lens, Function<BlockPos, VoxelShape> visualShape) {
        return BlockGetter.traverseBlocks(camera, lens, visualShape, (shapes, pos) -> {
            VoxelShape shape = shapes.apply(pos);
            // Vanilla glass has an empty visual shape. Stone, slabs and stairs retain their shape.
            if (shape.isEmpty()) return null;
            // VoxelShape.clip alone does not cover a ray whose endpoints are both inside a block.
            Vec3 localLens = lens.subtract(pos.getX(), pos.getY(), pos.getZ());
            if (contains(shape, localLens)) return Boolean.FALSE;
            BlockHitResult hit = shape.clip(camera, lens, pos);
            return hit == null ? null : Boolean.FALSE;
        }, world -> Boolean.TRUE);
    }

    private static boolean contains(VoxelShape shape, Vec3 point) {
        return shape.toAabbs().stream().anyMatch(box -> box.contains(point));
    }

    private GlintVisibility() {
    }
}
