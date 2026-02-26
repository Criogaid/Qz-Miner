package club.heiqi.qz_miner.core.founder;

import club.heiqi.qz_miner.core.MinerConfig;
import ic2.core.crop.TileEntityCrop;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import org.joml.Vector3i;

import java.util.concurrent.LinkedBlockingQueue;

public class CropFounder extends BasePositionFounder {
    public CropFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player, MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("作物搜索器");
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
        if (block.equals(Blocks.air) || block.getMaterial().isLiquid()) {
            return false;
        }

        // 检查是否是作物
        if (block instanceof BlockCrops) return true;

        TileEntity tile = tryGetTileEntityForMatch(pos);
        if (tile instanceof TileEntityCrop) return true;
        return false;
    }
}
