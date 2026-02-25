package club.heiqi.qz_miner.lootgame;

import cpw.mods.fml.common.Loader;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3i;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * LootGames 扫雷反射兼容层（2.2.0.1 签名校验通过）。
 */
public final class LootGameMineCompat {
    private static final Logger LOG = LogManager.getLogger();
    private static final String MOD_ID = "lootgames";

    private static volatile boolean initialized = false;
    private static volatile boolean available = false;

    private static Class<?> gameBlockClass;
    private static Class<?> smartSubordinateBlockClass;
    private static Class<?> boardBorderBlockClass;
    private static Class<?> blockPosClass;
    private static Class<?> blockStateClass;
    private static Class<?> msMasterTileClass;
    private static Class<?> gameMasterTileClass;
    private static Class<?> gameMineSweeperClass;
    private static Class<?> msBoardClass;
    private static Class<?> pos2iClass;
    private static Class<?> msTypeClass;

    private static Constructor<?> blockPosCtor;
    private static Constructor<?> pos2iCtor;

    private static Method getMasterPosMethod;
    private static Method getBorderMasterPosMethod;
    private static Method blockStateOfMethod;
    private static Method blockPosGetXMethod;
    private static Method blockPosGetYMethod;
    private static Method blockPosGetZMethod;
    private static Method getGameMethod;
    private static Method getBoardMethod;
    private static Method boardSizeMethod;
    private static Method boardGetTypeMethod;
    private static Method typeGetIdMethod;

    private static Object bombTypeEnumConstant;

    private LootGameMineCompat() {}

    public static boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    public static ArrayList<Vector3i> findBombsAround(EntityPlayer player, int scanRadius) {
        ArrayList<Vector3i> empty = new ArrayList<>();
        ensureInitialized();
        if (!available || player == null || player.worldObj == null) {
            return empty;
        }

        World world = player.worldObj;
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);
        double playerCenterX = player.posX;
        double playerCenterY = player.posY;
        double playerCenterZ = player.posZ;

        int radius = Math.max(1, scanRadius);
        Set<Vector3i> seenMasterPos = new HashSet<>();
        ArrayList<BoardCandidate> candidates = new ArrayList<>();
        for (int ox = -radius; ox <= radius; ox++) {
            for (int oy = -radius; oy <= radius; oy++) {
                for (int oz = -radius; oz <= radius; oz++) {
                    int x = px + ox;
                    int y = py + oy;
                    int z = pz + oz;
                    Block block = world.getBlock(x, y, z);
                    if (!gameBlockClass.isInstance(block)) {
                        continue;
                    }
                    try {
                        Object scanPos = blockPosCtor.newInstance(x, y, z);
                        int masterX;
                        int masterY;
                        int masterZ;

                        // 主方块自身命中
                        TileEntity currentTile = world.getTileEntity(x, y, z);
                        if (msMasterTileClass.isInstance(currentTile)) {
                            masterX = x;
                            masterY = y;
                            masterZ = z;
                        } else {
                            Object masterPos = resolveMasterPos(world, block, scanPos, x, y, z);
                            if (masterPos == null) {
                                continue;
                            }
                            masterX = ((Number) blockPosGetXMethod.invoke(masterPos)).intValue();
                            masterY = ((Number) blockPosGetYMethod.invoke(masterPos)).intValue();
                            masterZ = ((Number) blockPosGetZMethod.invoke(masterPos)).intValue();
                        }

                        if (!seenMasterPos.add(new Vector3i(masterX, masterY, masterZ))) {
                            continue;
                        }

                        TileEntity tileEntity = world.getTileEntity(masterX, masterY, masterZ);
                        if (!msMasterTileClass.isInstance(tileEntity)) {
                            continue;
                        }

                        double distanceSq = distanceSquared(
                                playerCenterX, playerCenterY, playerCenterZ,
                                masterX + 0.5D, masterY + 0.5D, masterZ + 0.5D
                        );
                        candidates.add(new BoardCandidate(masterX, masterY, masterZ, tileEntity, distanceSq));
                    } catch (Throwable ignored) {
                        // 单个方块失败不影响其他点继续扫描。
                    }
                }
            }
        }

        candidates.sort((a, b) -> Double.compare(a.distanceSq, b.distanceSq));
        for (BoardCandidate candidate : candidates) {
            ArrayList<Vector3i> bombs = findBombsOnBoard(
                    world,
                    candidate.masterTile,
                    candidate.masterX,
                    candidate.masterY,
                    candidate.masterZ
            );
            if (!bombs.isEmpty()) {
                return bombs;
            }
        }
        return empty;
    }

    private static Object resolveMasterPos(World world, Block block, Object scanPos, int x, int y, int z) throws Exception {
        if (smartSubordinateBlockClass.isInstance(block)) {
            return getMasterPosMethod.invoke(null, world, scanPos);
        }
        if (boardBorderBlockClass.isInstance(block)) {
            int meta = world.getBlockMetadata(x, y, z);
            Object blockState = blockStateOfMethod.invoke(null, block, meta);
            return getBorderMasterPosMethod.invoke(null, world, scanPos, blockState);
        }
        // MSActivator 等非棋盘方块不参与主块定位。
        return null;
    }

    private static double distanceSquared(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static ArrayList<Vector3i> findBombsOnBoard(World world, TileEntity masterTile, int masterX, int masterY, int masterZ) {
        ArrayList<Vector3i> empty = new ArrayList<>();
        try {
            Object game = getGameMethod.invoke(masterTile);
            if (game == null || !gameMineSweeperClass.isInstance(game)) {
                return empty;
            }
            Object board = getBoardMethod.invoke(game);
            if (board == null || !msBoardClass.isInstance(board)) {
                return empty;
            }
            int boardSize = ((Number) boardSizeMethod.invoke(board)).intValue();
            if (boardSize <= 0) {
                return empty;
            }

            int[] xSpan = resolveAxisSpan(world, masterX, masterY, masterZ, 1, 0);
            int[] zSpan = resolveAxisSpan(world, masterX, masterY, masterZ, 0, 1);

            int spanXStart = xSpan[0];
            int spanXLength = Math.max(boardSize, xSpan[1]);
            int spanZStart = zSpan[0];
            int spanZLength = Math.max(boardSize, zSpan[1]);

            int fieldStartX = spanXStart + (spanXLength - boardSize) / 2;
            int fieldStartZ = spanZStart + (spanZLength - boardSize) / 2;

            Set<Vector3i> result = new HashSet<>();
            for (int x = 0; x < boardSize; x++) {
                for (int z = 0; z < boardSize; z++) {
                    Object pos2i = pos2iCtor.newInstance(x, z);
                    Object type = boardGetTypeMethod.invoke(board, pos2i);
                    if (isBombType(type)) {
                        result.add(new Vector3i(fieldStartX + x, masterY, fieldStartZ + z));
                    }
                }
            }
            return new ArrayList<>(result);
        } catch (Throwable t) {
            LOG.warn("LootGames 扫雷解析失败: {}", t.getMessage());
            return empty;
        }
    }

    /**
     * @return [轴向起点, 轴向长度]
     */
    private static int[] resolveAxisSpan(World world, int masterX, int masterY, int masterZ, int stepX, int stepZ) {
        int negativeCount = countContinuousGameBlocks(world, masterX, masterY, masterZ, -stepX, -stepZ);
        int positiveCount = countContinuousGameBlocks(world, masterX, masterY, masterZ, stepX, stepZ);
        int startX = masterX - negativeCount * stepX;
        int startZ = masterZ - negativeCount * stepZ;

        int axisStart = (stepX != 0) ? startX : startZ;
        int axisLength = negativeCount + 1 + positiveCount;
        return new int[]{axisStart, axisLength};
    }

    private static int countContinuousGameBlocks(World world, int startX, int y, int startZ, int stepX, int stepZ) {
        int maxStep = 128;
        int count = 0;
        int x = startX + stepX;
        int z = startZ + stepZ;
        while (count < maxStep) {
            Block block = world.getBlock(x, y, z);
            if (!gameBlockClass.isInstance(block)) {
                break;
            }
            count++;
            x += stepX;
            z += stepZ;
        }
        return count;
    }

    private static boolean isBombType(Object type) {
        if (type == null) {
            return false;
        }
        if (bombTypeEnumConstant != null && type == bombTypeEnumConstant) {
            return true;
        }
        try {
            if (typeGetIdMethod != null) {
                byte id = ((Number) typeGetIdMethod.invoke(type)).byteValue();
                return id == (byte) -1;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static final class BoardCandidate {
        private final int masterX;
        private final int masterY;
        private final int masterZ;
        private final TileEntity masterTile;
        private final double distanceSq;

        private BoardCandidate(int masterX, int masterY, int masterZ, TileEntity masterTile, double distanceSq) {
            this.masterX = masterX;
            this.masterY = masterY;
            this.masterZ = masterZ;
            this.masterTile = masterTile;
            this.distanceSq = distanceSq;
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (LootGameMineCompat.class) {
            if (initialized) {
                return;
            }
            initialized = true;
            if (!Loader.isModLoaded(MOD_ID)) {
                available = false;
                LOG.info("未检测到 LootGames，扫雷揭示功能保持关闭。");
                return;
            }
            try {
                gameBlockClass = Class.forName("ru.timeconqueror.lootgames.api.block.GameBlock");
                smartSubordinateBlockClass = Class.forName("ru.timeconqueror.lootgames.api.block.SmartSubordinateBlock");
                boardBorderBlockClass = Class.forName("ru.timeconqueror.lootgames.api.block.BoardBorderBlock");
                blockPosClass = Class.forName("ru.timeconqueror.lootgames.utils.future.BlockPos");
                blockStateClass = Class.forName("ru.timeconqueror.lootgames.utils.future.BlockState");
                msMasterTileClass = Class.forName("ru.timeconqueror.lootgames.common.block.tile.MSMasterTile");
                gameMasterTileClass = Class.forName("ru.timeconqueror.lootgames.api.block.tile.GameMasterTile");
                gameMineSweeperClass = Class.forName("ru.timeconqueror.lootgames.minigame.minesweeper.GameMineSweeper");
                msBoardClass = Class.forName("ru.timeconqueror.lootgames.minigame.minesweeper.MSBoard");
                pos2iClass = Class.forName("ru.timeconqueror.lootgames.api.util.Pos2i");
                msTypeClass = Class.forName("ru.timeconqueror.lootgames.minigame.minesweeper.Type");

                blockPosCtor = blockPosClass.getConstructor(int.class, int.class, int.class);
                pos2iCtor = pos2iClass.getConstructor(int.class, int.class);

                getMasterPosMethod = smartSubordinateBlockClass.getMethod("getMasterPos", World.class, blockPosClass);
                getBorderMasterPosMethod = boardBorderBlockClass.getMethod("getMasterPos", World.class, blockPosClass, blockStateClass);
                blockStateOfMethod = blockStateClass.getMethod("of", Block.class, int.class);
                blockPosGetXMethod = blockPosClass.getMethod("getX");
                blockPosGetYMethod = blockPosClass.getMethod("getY");
                blockPosGetZMethod = blockPosClass.getMethod("getZ");
                getGameMethod = gameMasterTileClass.getMethod("getGame");
                getBoardMethod = gameMineSweeperClass.getMethod("getBoard");
                boardSizeMethod = msBoardClass.getMethod("size");
                boardGetTypeMethod = msBoardClass.getMethod("getType", pos2iClass);
                typeGetIdMethod = msTypeClass.getMethod("getId");

                Object[] enumConstants = msTypeClass.getEnumConstants();
                if (enumConstants != null) {
                    for (Object enumConstant : enumConstants) {
                        if (enumConstant instanceof Enum<?> e && "BOMB".equals(e.name())) {
                            bombTypeEnumConstant = enumConstant;
                            break;
                        }
                    }
                }

                available = true;
                LOG.info("LootGames 扫雷兼容层初始化成功。");
            } catch (Throwable t) {
                available = false;
                LOG.warn("LootGames 扫雷兼容层初始化失败，将跳过该功能: {}", t.toString());
            }
        }
    }
}
