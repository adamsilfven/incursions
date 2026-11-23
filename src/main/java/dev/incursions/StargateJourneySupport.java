package dev.incursions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.lang.reflect.InvocationTargetException;

/** Optional Stargate Journey integration. Nothing is linked when that mod is absent. */
final class StargateJourneySupport {
    private static final String ENTITY_PACKAGE = "net.povstalec.sgjourney.common.block_entities.stargate.";
    private StargateJourneySupport() {}

    static boolean exists(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && !blockEntity.isRemoved()
            && blockEntity.getClass().getName().startsWith(ENTITY_PACKAGE)
            && call(blockEntity, "getStargate") != null;
    }

    static boolean incoming(ServerLevel level, BlockPos pos) {
        if (!exists(level, pos)) return false;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!Boolean.TRUE.equals(call(blockEntity, "isWormholeOpen"))) return false;
        Object state = call(blockEntity, "getConnectionState");
        return state instanceof Enum<?> direction && direction.name().equals("INCOMING_CONNECTION");
    }

    private static Object call(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException | SecurityException e) {
            return null;
        }
    }
}
