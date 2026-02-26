package club.heiqi.qz_miner.core.founder;

import club.heiqi.qz_miner.core.MinerConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import org.joml.Vector3i;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ChainPositionFounder extends BasePositionFounder {
    private final LinkedBlockingQueue<Vector3i> frontier = new LinkedBlockingQueue<>();
    private final Set<Long> frontierSeeded = ConcurrentHashMap.newKeySet();
    private volatile boolean bfsReady = false;

    public ChainPositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player, MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("连锁搜索器");
    }

    @Override
    public void run1() {
        int minX = center.x - minerConfig.bigRadius;
        int maxX = center.x + minerConfig.bigRadius;
        int minY = Math.max(center.y - minerConfig.bigRadius, 0);
        int maxY = Math.min(center.y + minerConfig.bigRadius, 255);
        int minZ = center.z - minerConfig.bigRadius;
        int maxZ = center.z + minerConfig.bigRadius;

        bfsReady = true;
        Set<Long> visited = new HashSet<>();
        visited.add(packPosKey(center.x, center.y, center.z));
        frontier.clear();
        frontierSeeded.clear();
        enqueueFrontier(center);

        long idleStartNanos = -1L;
        Vector3i scanPos = new Vector3i();
        try {
            while (curCount < minerConfig.blockLimit) {
                waitUntil();
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                Vector3i current;
                try {
                    current = frontier.poll(2L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (current == null) {
                    if (frontier.isEmpty() && deferredPositions.isEmpty()) {
                        if (idleStartNanos < 0L) {
                            idleStartNanos = System.nanoTime();
                        } else if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - idleStartNanos) >= 50L) {
                            return;
                        }
                    } else {
                        idleStartNanos = -1L;
                    }
                    continue;
                }
                idleStartNanos = -1L;

                int nearMinX = Math.max(current.x - minerConfig.smallRadius, minX);
                int nearMaxX = Math.min(current.x + minerConfig.smallRadius, maxX);
                int nearMinY = Math.max(current.y - minerConfig.smallRadius, minY);
                int nearMaxY = Math.min(current.y + minerConfig.smallRadius, maxY);
                int nearMinZ = Math.max(current.z - minerConfig.smallRadius, minZ);
                int nearMaxZ = Math.min(current.z + minerConfig.smallRadius, maxZ);

                for (int x = nearMinX; x <= nearMaxX; x++) {
                    for (int y = nearMinY; y <= nearMaxY; y++) {
                        for (int z = nearMinZ; z <= nearMaxZ; z++) {
                            long key = packPosKey(x, y, z);
                            if (!visited.add(key)) {
                                continue;
                            }

                            scanPos.set(x, y, z);
                            if (checkCanAdd(scanPos)) {
                                addResult(scanPos);
                                if (curCount >= minerConfig.blockLimit) {
                                    return;
                                }
                            }

                            waitUntil();
                            if (Thread.currentThread().isInterrupted()) {
                                return;
                            }
                        }
                    }
                }
            }
        } finally {
            bfsReady = false;
            frontier.clear();
            frontierSeeded.clear();
        }
    }

    @Override
    public boolean checkCanAdd(Vector3i pos) {
        if (foundedPositions.contains(pos)) {
            // LOG.info("重复的点");
            return false;
        }
        if (isUnsafeToReadAt(pos)) {
            return false;
        }
        Block block = getBlockAt(pos);
        // 是空气跳过
        if (block.equals(Blocks.air) || block.getMaterial().isLiquid() || block.equals(Blocks.bedrock)) {
            return false;
        }
        int blockMeta = getBlockMetaAt(pos);


        // 玩家脚下的一个方块不能被挖掘
        if (isPlayerFootBlock(pos)) {
            return false;
        }

        // 判断是否与样本相同
        DeterminingIdentical.MatchDecision decision =
                DeterminingIdentical.determineIdentical(sampleBlock, sampleBlockMeta, sampleTileEntity, pos, player);
        if (decision == DeterminingIdentical.MatchDecision.DEFER) {
            deferPosition(pos);
            return false;
        }
        if (decision == DeterminingIdentical.MatchDecision.NO_MATCH)
            return false;

        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }

    @Override
    public void addResult(Vector3i pos) {
        boolean existedBefore = foundedPositions.contains(pos);
        super.addResult(pos);
        if (!bfsReady || existedBefore) {
            return;
        }
        if (foundedPositions.contains(pos)) {
            enqueueFrontier(pos);
        }
    }

    private void enqueueFrontier(Vector3i pos) {
        long key = packPosKey(pos.x, pos.y, pos.z);
        if (frontierSeeded.add(key)) {
            frontier.offer(new Vector3i(pos));
        }
    }

    private static long packPosKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
