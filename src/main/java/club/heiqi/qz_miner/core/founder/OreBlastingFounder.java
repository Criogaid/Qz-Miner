package club.heiqi.qz_miner.core.founder;

import club.heiqi.qz_miner.core.MinerConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import org.joml.Vector3i;

import java.util.concurrent.LinkedBlockingQueue;

public class OreBlastingFounder extends BasePositionFounder {
    public OreBlastingFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player, MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("矿石搜索器");
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
        if (block.equals(Blocks.air) || block.getMaterial().isLiquid() || block.equals(Blocks.bedrock)) {
            return false;
        }
        int blockMeta = getBlockMetaAt(pos);

        // 玩家脚下的一个方块不能被挖掘
        if (isPlayerFootBlock(pos))
            return false;

        // 检查是否是矿石
        if (!DeterminingIdentical.isOreBlock(pos, player))
            return false;

        // 如果是创造模式全都能挖掘
        if (player.capabilities.isCreativeMode) return true;
        return block.canHarvestBlock(player, blockMeta);
    }
}
