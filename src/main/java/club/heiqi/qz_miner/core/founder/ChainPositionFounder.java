package club.heiqi.qz_miner.core.founder;

import club.heiqi.qz_miner.core.MinerConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import org.joml.Vector3i;

import java.util.concurrent.LinkedBlockingQueue;

public class ChainPositionFounder extends BasePositionFounder {
    public ChainPositionFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player, MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("连锁搜索器");
    }

    @Override
    public void run1() {
        int curRadius = 1;
        Vector3i scanPos = new Vector3i();
        while (curCount < minerConfig.blockLimit && curRadius <= minerConfig.bigRadius) {
            for (int x = center.x - curRadius; x <= center.x + curRadius; x++) {
                int minY = Math.max(center.y - curRadius, 0);
                int maxY = Math.min(center.y + curRadius, 255);
                for (int y = minY; y <= maxY; y++) {
                    for (int z = center.z - curRadius; z <= center.z + curRadius; z++) {
                        scanPos.set(x, y, z);

                        if (checkCanAdd(scanPos)) {
                            this.addResult(scanPos);
                        }

                        // 检查性流程    检查数量     检查线程是否被中断
                        if (curCount >= minerConfig.blockLimit) {
                            return;
                        }
                        waitUntil();
                        if (Thread.currentThread().isInterrupted()) {
                            // LOG.info("线程被中断");
                            return;
                        }
                    }
                }
            }
            curRadius++;
            if (curRadius > minerConfig.bigRadius) {
                // 超出半径范围，退出
                return;
            }
        }
    }

    @Override
    public boolean checkCanAdd(Vector3i pos) {
        if (foundedPositions.contains(pos)) {
            // LOG.info("重复的点");
            return false;
        }
        if (!isSafeToReadAt(pos)) {
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

        // 检查该点连锁小区域内是否有已标记点
        boolean inRange = false;
        for (Vector3i position : foundedPositions) {
            // 判断点 X Y Z 距离 及其曼哈顿距离
            int xOffset = Math.abs(position.x - pos.x);
            int yOffset = Math.abs(position.y - pos.y);
            int zOffset = Math.abs(position.z - pos.z);

            if (xOffset <= minerConfig.smallRadius &&
                    yOffset <= minerConfig.smallRadius &&
                    zOffset <= minerConfig.smallRadius
            ) {
                inRange = true;
                break;
            }
        }
        if (!inRange) return false;

        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }

    @Override
    public void addResult(Vector3i pos) {
        super.addResult(pos);
    }
}
