package club.heiqi.qz_miner.core.founder;

import club.heiqi.qz_miner.core.MinerConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import org.joml.Vector3i;

import java.util.concurrent.LinkedBlockingQueue;

public class BlastingLoggingFounder extends BasePositionFounder {
    public BlastingLoggingFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player, MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("爆破伐木搜索器");
    }

    @Override
    public void run1() {
        int curRadius = 1;
        int highRadius = 1;
        Vector3i scanPos = new Vector3i();
        while (curCount < minerConfig.blockLimit) {
            // LOG.info("当前半径: {} 当前块数: {}", curRadius, curCount);
            for (int x = center.x - curRadius; x <= center.x + curRadius; x++) {
                int minY = Math.max(center.y - highRadius, 0);
                int maxY = Math.min(center.y + highRadius, 255);
                for (int y = minY; y <= maxY; y++) {
                    for (int z = center.z - curRadius; z <= center.z + curRadius; z++) {
                        scanPos.set(x, y, z);
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
                }
            }
            curRadius = Math.min(curRadius+1, minerConfig.bigRadius);
            highRadius++;
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
        if (block.equals(Blocks.air) || block.getMaterial().isLiquid() || block.equals(Blocks.bedrock)) {
            return false;
        }
        int blockMeta = getBlockMetaAt(pos);

        // 玩家脚下的一个方块不能被挖掘
        if (isPlayerFootBlock(pos)) {
            return false;
        }

        // 检查是否是木头或树叶
        boolean founded = false;
        int[] oreIDs = OreDictionary.getOreIDs(new ItemStack(block));
        for (int oreID : oreIDs) {
            String oreName = OreDictionary.getOreName(oreID);
            if (!oreName.equals("logWood") && !oreName.equals("treeLeaves")) continue;
            founded = true;
        }
        if (!founded) return false;

        // 如果是创造模式全都能挖掘
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }
}
