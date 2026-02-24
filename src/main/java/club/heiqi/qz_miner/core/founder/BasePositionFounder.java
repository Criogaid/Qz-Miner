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
        while (curCount < minerConfig.blockLimit && curRadius <= minerConfig.bigRadius) {
            // LOG.info("当前半径: {} 当前块数: {}", curRadius, curCount);
            for (int x = center.x - curRadius; x <= center.x + curRadius; x++) {
                for (int y = center.y - curRadius; y <= center.y + curRadius; y++) {
                    for (int z = center.z - curRadius; z <= center.z + curRadius; z++) {
                        Vector3i pos = new Vector3i(x, y, z);
                        if (checkCanAdd(pos)) {
                            this.addResult(pos);
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
                }
            }
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
        if (!isSafeToReadAt(pos)) {
            return false;
        }
        Block block = getBlockAt(pos);
        if (block.equals(Blocks.air) || block.getMaterial().isLiquid() || block.equals(Blocks.bedrock)) {
            return false;
        }
        Vector3i playerPos = new Vector3i((int) Math.floor(player.posX), (int) Math.floor(player.posY), (int) Math.floor(player.posZ));
        int blockMeta = getBlockMetaAt(pos);

        // 玩家脚下的一个方块不能被挖掘
        if (pos.x == playerPos.x && pos.y == (playerPos.y - 1) && pos.z == playerPos.z) {
            return false;
        }

        // 如果是创造模式全都能挖掘
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }

    public synchronized void addResult(Vector3i pos) {
        // LOG.info("添加位置: x: {} y: {} z: {}", pos.x, pos.y, pos.z);
        Vector3i key = new Vector3i(pos);
        if (!this.foundedPositions.add(key)) {
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
        if (!isSafeToReadAt(pos)) {
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
            } else if (!deferredQueuedPositions.contains(deferredPos)) {
                clearDeferredState(deferredPos);
            }
            processed++;
        }
    }

    protected boolean isServerThread() {
        return Thread.currentThread().getName().toLowerCase().contains("server");
    }

    protected boolean shouldGuardAsyncWorldAccess() {
        return Config.safeAsyncWorldAccess && !isServerThread();
    }

    protected boolean isSafeToReadAt(Vector3i pos) {
        if (pos.y < 0 || pos.y >= 256) {
            return false;
        }
        if (!shouldGuardAsyncWorldAccess()) {
            return true;
        }
        // 非服务器线程只允许访问已加载区块，避免触发ChunkIO和Tile列表变更。
        return player.worldObj.blockExists(pos.x, pos.y, pos.z);
    }

    protected Block getBlockAt(Vector3i pos) {
        if (!isSafeToReadAt(pos)) {
            return Blocks.air;
        }
        return player.worldObj.getBlock(pos.x, pos.y, pos.z);
    }

    protected int getBlockMetaAt(Vector3i pos) {
        if (!isSafeToReadAt(pos)) {
            return 0;
        }
        return player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
    }

    protected TileEntity getTileEntityAt(Vector3i pos) {
        if (!isSafeToReadAt(pos)) {
            return null;
        }
        if (shouldGuardAsyncWorldAccess()) {
            // 异步线程避免读取TileEntity，防止触发setTileEntity/列表改写。
            return null;
        }
        return player.worldObj.getTileEntity(pos.x, pos.y, pos.z);
    }

    protected TileEntity tryGetTileEntityForMatch(Vector3i pos) {
        if (!isSafeToReadAt(pos)) {
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
}
