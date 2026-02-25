package club.heiqi.qz_miner.lootgame;

import cpw.mods.fml.common.Loader;
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
import java.util.List;
import java.util.Set;

/**
 * LootGames 扫雷反射兼容层（服务端安全路径）。
 */
public final class LootGameMineCompat {
    private static final Logger LOG = LogManager.getLogger();
    private static final String MOD_ID = "lootgames";
    private static final int MASTER_SEARCH_EXTRA_RADIUS = 8;

    private static volatile boolean initialized = false;
    private static volatile boolean available = false;

    private static Class<?> blockPosClass;
    private static Class<?> msMasterTileClass;
    private static Class<?> gameMasterTileClass;
    private static Class<?> boardLootGameClass;
    private static Class<?> gameMineSweeperClass;
    private static Class<?> msBoardClass;
    private static Class<?> pos2iClass;
    private static Class<?> msTypeClass;

    private static Constructor<?> pos2iCtor;

    private static Method blockPosGetXMethod;
    private static Method blockPosGetYMethod;
    private static Method blockPosGetZMethod;
    private static Method getGameMethod;
    private static Method isBoardGeneratedMethod;
    private static Method getBoardMethod;
    private static Method getBoardOriginMethod;
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
        int radius = Math.max(1, scanRadius) + MASTER_SEARCH_EXTRA_RADIUS;
        double maxDistanceSq = (double) radius * radius;

        ArrayList<BoardCandidate> candidates = collectBoardCandidates(world, player, maxDistanceSq);
        candidates.sort((a, b) -> Double.compare(a.distanceSq, b.distanceSq));
        for (BoardCandidate candidate : candidates) {
            ArrayList<Vector3i> bombs = findBombsOnBoard(candidate);
            if (!bombs.isEmpty()) {
                return bombs;
            }
        }
        return empty;
    }

    private static ArrayList<BoardCandidate> collectBoardCandidates(World world, EntityPlayer player, double maxDistanceSq) {
        ArrayList<BoardCandidate> result = new ArrayList<>();
        List<?> tileEntities = new ArrayList<>(world.loadedTileEntityList);
        for (Object obj : tileEntities) {
            if (!(obj instanceof TileEntity tileEntity)) {
                continue;
            }
            if (!msMasterTileClass.isInstance(tileEntity)) {
                continue;
            }
            if (tileEntity.isInvalid()) {
                continue;
            }
            BoardSnapshot snapshot = trySnapshot(tileEntity);
            if (snapshot == null) {
                continue;
            }
            double distanceSq = distanceSquaredToBoard(
                    player.posX,
                    player.posY,
                    player.posZ,
                    snapshot.originX,
                    snapshot.originY,
                    snapshot.originZ,
                    snapshot.boardSize
            );
            if (distanceSq > maxDistanceSq) {
                continue;
            }
            result.add(new BoardCandidate(snapshot.board, snapshot.originX, snapshot.originY, snapshot.originZ, snapshot.boardSize, distanceSq));
        }
        return result;
    }

    private static BoardSnapshot trySnapshot(TileEntity masterTile) {
        try {
            Object game = getGameMethod.invoke(masterTile);
            if (game == null || !gameMineSweeperClass.isInstance(game)) {
                return null;
            }
            if (isBoardGeneratedMethod != null && !Boolean.TRUE.equals(isBoardGeneratedMethod.invoke(game))) {
                return null;
            }
            Object board = getBoardMethod.invoke(game);
            if (board == null || !msBoardClass.isInstance(board)) {
                return null;
            }
            Object boardOrigin = getBoardOriginMethod.invoke(game);
            if (boardOrigin == null) {
                return null;
            }
            int originX = ((Number) blockPosGetXMethod.invoke(boardOrigin)).intValue();
            int originY = ((Number) blockPosGetYMethod.invoke(boardOrigin)).intValue();
            int originZ = ((Number) blockPosGetZMethod.invoke(boardOrigin)).intValue();
            int boardSize = ((Number) boardSizeMethod.invoke(board)).intValue();
            if (boardSize <= 0) {
                return null;
            }
            return new BoardSnapshot(board, originX, originY, originZ, boardSize);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ArrayList<Vector3i> findBombsOnBoard(BoardCandidate candidate) {
        ArrayList<Vector3i> empty = new ArrayList<>();
        try {
            Set<Vector3i> result = new HashSet<>();
            for (int x = 0; x < candidate.boardSize; x++) {
                for (int z = 0; z < candidate.boardSize; z++) {
                    Object pos2i = pos2iCtor.newInstance(x, z);
                    Object type = boardGetTypeMethod.invoke(candidate.board, pos2i);
                    if (isBombType(type)) {
                        result.add(new Vector3i(candidate.originX + x, candidate.originY, candidate.originZ + z));
                    }
                }
            }
            return new ArrayList<>(result);
        } catch (Throwable t) {
            LOG.warn("LootGames 扫雷解析失败: {}", t.toString());
            return empty;
        }
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

    private static double distanceSquaredToBoard(double px, double py, double pz, int originX, int originY, int originZ, int boardSize) {
        double minX = originX;
        double maxX = originX + boardSize;
        double minY = originY;
        double maxY = originY + 1.0D;
        double minZ = originZ;
        double maxZ = originZ + boardSize;

        double dx = distanceToRange(px, minX, maxX);
        double dy = distanceToRange(py, minY, maxY);
        double dz = distanceToRange(pz, minZ, maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceToRange(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0D;
    }

    private static final class BoardSnapshot {
        private final Object board;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int boardSize;

        private BoardSnapshot(Object board, int originX, int originY, int originZ, int boardSize) {
            this.board = board;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.boardSize = boardSize;
        }
    }

    private static final class BoardCandidate {
        private final Object board;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int boardSize;
        private final double distanceSq;

        private BoardCandidate(Object board, int originX, int originY, int originZ, int boardSize, double distanceSq) {
            this.board = board;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.boardSize = boardSize;
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
                // 仅加载服务端安全类，避免触发客户端专有类型（例如 IIconRegister）的类加载失败。
                blockPosClass = Class.forName("ru.timeconqueror.lootgames.utils.future.BlockPos");
                msMasterTileClass = Class.forName("ru.timeconqueror.lootgames.common.block.tile.MSMasterTile");
                gameMasterTileClass = Class.forName("ru.timeconqueror.lootgames.api.block.tile.GameMasterTile");
                boardLootGameClass = Class.forName("ru.timeconqueror.lootgames.api.minigame.BoardLootGame");
                gameMineSweeperClass = Class.forName("ru.timeconqueror.lootgames.minigame.minesweeper.GameMineSweeper");
                msBoardClass = Class.forName("ru.timeconqueror.lootgames.minigame.minesweeper.MSBoard");
                pos2iClass = Class.forName("ru.timeconqueror.lootgames.api.util.Pos2i");
                msTypeClass = Class.forName("ru.timeconqueror.lootgames.minigame.minesweeper.Type");

                pos2iCtor = pos2iClass.getConstructor(int.class, int.class);

                blockPosGetXMethod = blockPosClass.getMethod("getX");
                blockPosGetYMethod = blockPosClass.getMethod("getY");
                blockPosGetZMethod = blockPosClass.getMethod("getZ");
                getGameMethod = gameMasterTileClass.getMethod("getGame");
                isBoardGeneratedMethod = gameMineSweeperClass.getMethod("isBoardGenerated");
                getBoardMethod = gameMineSweeperClass.getMethod("getBoard");
                getBoardOriginMethod = boardLootGameClass.getMethod("getBoardOrigin");
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
                LOG.info("LootGames 扫雷兼容层初始化成功（server-safe）。");
            } catch (Throwable t) {
                available = false;
                LOG.warn("LootGames 扫雷兼容层初始化失败，将跳过该功能: {}", t.toString());
            }
        }
    }
}
