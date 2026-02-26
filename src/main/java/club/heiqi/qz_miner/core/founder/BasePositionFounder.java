package club.heiqi.qz_miner.core.founder;

import club.heiqi.qz_miner.Config;
import club.heiqi.qz_miner.core.BaseOperator;
import club.heiqi.qz_miner.core.MinerConfig;
import club.heiqi.qz_miner.thread.Pauseable;
import cpw.mods.fml.common.FMLCommonHandler;
import gregtech.common.blocks.BlockOresAbstract;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3i;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BasePositionFounder extends Pauseable {
    public Logger LOG = LogManager.getLogger();
    private static final int MAX_DEFER_ATTEMPTS = 3;

    public Vector3i center;
    public EntityPlayer player;
    public MinerConfig minerConfig;
    /**已收集的可采集点 外部容器*/
    public LinkedBlockingQueue<Vector3i> positions;
    /**已收集的可采集点 内部容器*/
    public Set<Vector3i> foundedPositions = ConcurrentHashMap.newKeySet();
    protected final ConcurrentLinkedQueue<Vector3i> deferredPositions = new ConcurrentLinkedQueue<>();
    private final Set<Vector3i> deferredQueuedPositions = ConcurrentHashMap.newKeySet();
    private final Map<Vector3i, Integer> deferredAttempts = new ConcurrentHashMap<>();

    public int curCount = 0; // 包含初始加入的中心块
    // ========== 挖掘样本 ==========
    public final Block sampleBlock;
    public final int sampleBlockMeta;
    public final TileEntity sampleTileEntity;

    public BasePositionFounder(
            Vector3i center,
            LinkedBlockingQueue<Vector3i> results,
            EntityPlayer player,
            MinerConfig minerConfig
    ) {
        setName("无差别搜索器");
        BaseOperator.compatibilityCheck();

        this.center = center;
        this.player = player;
        this.positions = results;
        this.minerConfig = minerConfig;
        addResult(center);

        sampleBlock = getBlockAt(center);
        sampleBlockMeta = getBlockMetaAt(center);
        sampleTileEntity = getTileEntityAt(center);
    }

    @Override
    public void run1() {
        int curRadius = 1;
        Vector3i scanPos = new Vector3i();
        MutableBoxDiffPositionIterator iterator = new MutableBoxDiffPositionIterator();
        boolean hasPreviousBounds = false;
        int prevMinX = 0;
        int prevMaxX = 0;
        int prevMinY = 0;
        int prevMaxY = 0;
        int prevMinZ = 0;
        int prevMaxZ = 0;
        while (curCount < minerConfig.blockLimit && curRadius <= minerConfig.bigRadius) {
            // LOG.info("当前半径: {} 当前块数: {}", curRadius, curCount);
            int minX = center.x - curRadius;
            int maxX = center.x + curRadius;
            int minY = Math.max(center.y - curRadius, 0);
            int maxY = Math.min(center.y + curRadius, 255);
            int minZ = center.z - curRadius;
            int maxZ = center.z + curRadius;
            iterator.reset(
                    minX, maxX, minY, maxY, minZ, maxZ,
                    prevMinX, prevMaxX, prevMinY, prevMaxY, prevMinZ, prevMaxZ,
                    hasPreviousBounds
            );
            while (iterator.next(scanPos)) {
                if (checkCanAdd(scanPos)) {
                    this.addResult(scanPos);
                }
                if (curCount >= minerConfig.blockLimit) {
                    return;
                }
                waitUntil();
                if (Thread.currentThread().isInterrupted()) {
                    LOG.info("线程被中断");
                    return;
                }
            }
            prevMinX = minX;
            prevMaxX = maxX;
            prevMinY = minY;
            prevMaxY = maxY;
            prevMinZ = minZ;
            prevMaxZ = maxZ;
            hasPreviousBounds = true;
            curRadius++;
            if (curRadius > minerConfig.bigRadius) {
                break; // 超出半径范围，退出
            }
        }
    }

    public boolean checkCanAdd(Vector3i pos) {
        if (foundedPositions.contains(pos)) {
            // LOG.info("重复的点");
            return false;
        }
        Block block = getBlockAt(pos);
        if (block.equals(Blocks.air) || block.getMaterial().isLiquid() || block.equals(Blocks.bedrock)) {
            return false;
        }

        // 玩家脚下的一个方块不能被挖掘
        if (isPlayerFootBlock(pos)) {
            return false;
        }

        // 如果是创造模式全都能挖掘
        if (player.capabilities.isCreativeMode) return true;
        int blockMeta = getBlockMetaAt(pos);
        return block.canHarvestBlock(player, blockMeta);
    }

    public synchronized void addResult(Vector3i pos) {
        // LOG.info("添加位置: x: {} y: {} z: {}", pos.x, pos.y, pos.z);
        Vector3i key = new Vector3i(pos);
        if (!this.foundedPositions.add(key)) {
            return;
        }
        // 首个样本点保持历史行为；其余路径统一遵守 blockLimit。
        if (this.foundedPositions.size() > 1 && this.curCount >= minerConfig.blockLimit) {
            this.foundedPositions.remove(key);
            clearDeferredState(key);
            return;
        }
        try {
            this.positions.put(key);
            curCount++;
        } catch (InterruptedException e) {
            this.foundedPositions.remove(key);
            Thread.currentThread().interrupt(); // 重新设置中断标志位
            return;
        }
        clearDeferredState(key);

        // 触发矿脉探索功能
        if (BaseOperator.hasVP_API && DeterminingIdentical.hasBlockBaseOre &&
                player.worldObj.isRemote && FMLCommonHandler.instance().getEffectiveSide().isClient() &&
                getBlockAt(key) instanceof BlockOresAbstract
        ) {
            getBlockAt(key).onBlockActivated(player.worldObj, key.x, key.y, key.z, player, 0,0,0,0);
        }
    }

    protected void deferPosition(Vector3i pos) {
        if (isUnsafeToReadAt(pos)) {
            return;
        }
        Vector3i key = new Vector3i(pos);
        if (foundedPositions.contains(key)) {
            clearDeferredState(key);
            return;
        }

        if (!deferredQueuedPositions.add(key)) {
            return;
        }
        int attempts = deferredAttempts.getOrDefault(key, 0) + 1;
        if (attempts > MAX_DEFER_ATTEMPTS) {
            deferredQueuedPositions.remove(key);
            clearDeferredState(key);
            return;
        }
        deferredAttempts.put(key, attempts);
        deferredPositions.offer(key);
    }

    public void processDeferredPositions(int maxPerTick) {
        if (maxPerTick <= 0) {
            return;
        }
        if (curCount >= minerConfig.blockLimit) {
            clearAllDeferredState();
            return;
        }
        int processed = 0;
        while (processed < maxPerTick) {
            Vector3i deferredPos = deferredPositions.poll();
            if (deferredPos == null) {
                return;
            }
            deferredQueuedPositions.remove(deferredPos);

            if (foundedPositions.contains(deferredPos)) {
                clearDeferredState(deferredPos);
                processed++;
                continue;
            }

            if (checkCanAdd(deferredPos)) {
                addResult(deferredPos);
                if (curCount >= minerConfig.blockLimit) {
                    clearAllDeferredState();
                    return;
                }
            } else if (!deferredQueuedPositions.contains(deferredPos)) {
                clearDeferredState(deferredPos);
            }
            processed++;
        }
    }

    protected boolean isServerThread() {
        String threadName = Thread.currentThread().getName();
        return threadName.contains("server") || threadName.contains("Server");
    }

    protected boolean isPlayerFootBlock(Vector3i pos) {
        int playerX = (int) Math.floor(player.posX);
        int playerY = (int) Math.floor(player.posY);
        int playerZ = (int) Math.floor(player.posZ);
        return pos.x == playerX && pos.y == (playerY - 1) && pos.z == playerZ;
    }

    /**
     * 执行阶段的轻量复检（主线程调用）。
     * 仅做公共安全检查，不包含模式特定匹配逻辑。
     */
    public boolean canHarvestNow(Vector3i pos) {
        if (pos == null) {
            return false;
        }
        if (pos.y < 0 || pos.y >= 256) {
            return false;
        }
        if (!player.worldObj.blockExists(pos.x, pos.y, pos.z)) {
            return false;
        }
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        if (block.equals(Blocks.air) || block.getMaterial().isLiquid() || block.equals(Blocks.bedrock)) {
            return false;
        }
        return !isPlayerFootBlock(pos);
    }

    protected boolean shouldGuardAsyncWorldAccess() {
        return Config.safeAsyncWorldAccess && !isServerThread();
    }

    protected boolean isUnsafeToReadAt(Vector3i pos) {
        if (pos.y < 0 || pos.y >= 256) {
            return true;
        }
        if (!shouldGuardAsyncWorldAccess()) {
            return false;
        }
        // 非服务器线程只允许访问已加载区块，避免触发ChunkIO和Tile列表变更。
        return !player.worldObj.blockExists(pos.x, pos.y, pos.z);
    }

    protected Block getBlockAt(Vector3i pos) {
        if (isUnsafeToReadAt(pos)) {
            return Blocks.air;
        }
        return getBlockAtUnsafe(pos);
    }

    protected int getBlockMetaAt(Vector3i pos) {
        if (isUnsafeToReadAt(pos)) {
            return 0;
        }
        return getBlockMetaAtUnsafe(pos);
    }

    protected Block getBlockAtUnsafe(Vector3i pos) {
        return player.worldObj.getBlock(pos.x, pos.y, pos.z);
    }

    protected int getBlockMetaAtUnsafe(Vector3i pos) {
        return player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
    }

    protected TileEntity getTileEntityAt(Vector3i pos) {
        if (isUnsafeToReadAt(pos)) {
            return null;
        }
        if (shouldGuardAsyncWorldAccess()) {
            // 异步线程避免读取TileEntity，防止触发setTileEntity/列表改写。
            return null;
        }
        return player.worldObj.getTileEntity(pos.x, pos.y, pos.z);
    }

    protected TileEntity tryGetTileEntityForMatch(Vector3i pos) {
        if (isUnsafeToReadAt(pos)) {
            return null;
        }
        try {
            return player.worldObj.getTileEntity(pos.x, pos.y, pos.z);
        } catch (RuntimeException e) {
            if (shouldGuardAsyncWorldAccess()) {
                return null;
            }
            throw e;
        }
    }

    private void clearDeferredState(Vector3i pos) {
        deferredQueuedPositions.remove(pos);
        deferredAttempts.remove(pos);
    }

    private void clearAllDeferredState() {
        deferredPositions.clear();
        deferredQueuedPositions.clear();
        deferredAttempts.clear();
    }
}
