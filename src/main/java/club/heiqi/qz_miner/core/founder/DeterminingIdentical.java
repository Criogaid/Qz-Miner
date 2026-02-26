package club.heiqi.qz_miner.core.founder;

import appeng.block.solids.OreQuartz;
import appeng.block.solids.OreQuartzCharged;
import bartworks.system.material.BWMetaGeneratedOres;
import bartworks.system.material.BWMetaGeneratedSmallOres;
import bartworks.system.material.TileEntityMetaGeneratedBlock;
import club.heiqi.qz_miner.Config;
import club.heiqi.qz_miner.utils.MessageUtils;
import com.github.bsideup.jabel.Desugar;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.blocks.BlockOresAbstract;
import gregtech.common.blocks.TileEntityOres;
import gtPlusPlus.core.block.base.BlockBaseOre;
import net.minecraft.block.Block;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockRedstoneOre;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3i;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DeterminingIdentical {
    public static Logger LOG = LogManager.getLogger();
    private static final Map<BlockMetaKey, Boolean> ORE_FALLBACK_CACHE = new ConcurrentHashMap<>();

    public enum MatchDecision {
        MATCH,
        NO_MATCH,
        DEFER
    }

    public static MatchDecision determineIdentical(
            Block sBlock,
            int sMeta,
            @Nullable TileEntity sTile,
            Vector3i pos,
            EntityPlayer player
    ) {
        if (!hasCheck) checkCompatibility();
        boolean safeAsyncGuard = Config.safeAsyncWorldAccess && !isServerThread();

        if (pos.y < 0 || pos.y >= 256) {
            return MatchDecision.NO_MATCH;
        }
        if (safeAsyncGuard && !player.worldObj.blockExists(pos.x, pos.y, pos.z)) {
            return MatchDecision.NO_MATCH;
        }

        Block thisBlock = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        int thisMeta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);

        if (!sBlock.equals(thisBlock) || sMeta != thisMeta)
            return MatchDecision.NO_MATCH;

        if (sTile == null) {
            return MatchDecision.MATCH;
        }
        if (safeAsyncGuard) {
            return MatchDecision.DEFER;
        }

        TileEntity thisTile = tryGetTileEntityForMatch(player, pos, false);
        if (thisTile == null) {
            return MatchDecision.NO_MATCH;
        }

        // 格雷机器判断相同
        if (hasGregTechTileEntity &&
                sTile instanceof IGregTechTileEntity sMetaTile &&
                thisTile instanceof IGregTechTileEntity thisMetaTile
        ) {
            return sMetaTile.getMetaTileID() == thisMetaTile.getMetaTileID()
                    ? MatchDecision.MATCH
                    : MatchDecision.NO_MATCH;
        }
        // 格雷矿石判断相同
        if (hasTileEntityOre &&
                sTile instanceof TileEntityOres sTileEntityOre &&
                thisTile instanceof TileEntityOres tTileEntityOre
        ) {
            return sTileEntityOre.mMetaData == tTileEntityOre.mMetaData
                    ? MatchDecision.MATCH
                    : MatchDecision.NO_MATCH;
        }
        // 判断BartWork
        if (hasTileEntityMetaGeneratedBlock &&
                sTile instanceof TileEntityMetaGeneratedBlock sBTEMGB &&
                thisTile instanceof TileEntityMetaGeneratedBlock tBTEMGB
        ) {
            return sBTEMGB.mMetaData == tBTEMGB.mMetaData
                    ? MatchDecision.MATCH
                    : MatchDecision.NO_MATCH;
        }

        // 判断普通Tile
        return sTile.getBlockMetadata() == thisTile.getBlockMetadata()
                ? MatchDecision.MATCH
                : MatchDecision.NO_MATCH;
    }

    public static final Set<String> collectOrePackage = ConcurrentHashMap.newKeySet();
    public static boolean isOreBlock(Vector3i pos, EntityPlayer player) {
        if (!hasCheck) checkCompatibility();
        boolean safeAsyncGuard = Config.safeAsyncWorldAccess && !isServerThread();
        if (pos.y < 0 || pos.y >= 256) {
            return false;
        }
        if (safeAsyncGuard && !player.worldObj.blockExists(pos.x, pos.y, pos.z)) {
            return false;
        }
        Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);
        int meta = player.worldObj.getBlockMetadata(pos.x, pos.y, pos.z);
        boolean matched = isOreLike(block, meta);
        if (matched) {
            reportUnknownOrePackageOnce(block, player);
        }
        return matched;
    }

    public static boolean isOreLike(Block block, int meta) {
        // 原版矿石
        if (block instanceof BlockOre || block instanceof BlockRedstoneOre) return true;
        // GT/BW/GTPP矿石（不引入 mNatural 限制，保持 QzMiner 连锁语义）
        if (hasBlockOresAbstract && block instanceof BlockOresAbstract) return true;
        if (hasBWMetaGeneratedSmallOres && block instanceof BWMetaGeneratedSmallOres) return true;
        if (hasBWMetaGeneratedOres && block instanceof BWMetaGeneratedOres) return true;
        if (hasBlockBaseOre && block instanceof BlockBaseOre) return true;
        // AE矿石，两个分支独立判断，避免运算符优先级错误
        if (hasAEOreQuartz && block instanceof OreQuartz) return true;
        if (hasAEOreQuartzCharged && block instanceof OreQuartzCharged) return true;

        boolean matched = ORE_FALLBACK_CACHE.computeIfAbsent(new BlockMetaKey(block, meta), key -> {
            String blockUnlocalizedName = key.block.getUnlocalizedName();
            return blockUnlocalizedName != null && blockUnlocalizedName.toLowerCase().contains("ore");
        });
        return matched;
    }

    public static boolean hasCheck = false;

    private static boolean isServerThread() {
        String threadName = Thread.currentThread().getName();
        return threadName.contains("server") || threadName.contains("Server");
    }

    private static void reportUnknownOrePackageOnce(Block block, EntityPlayer player) {
        if (!isServerThread()) {
            return;
        }
        String packageName = block.getClass().getTypeName();
        if (!collectOrePackage.add(packageName)) {
            return;
        }
        MessageUtils.sendPlayerMessage(
                "发现可能未被收录的矿石类: 【"+ packageName +"】Mod正在测试阶段，发现此消息可上报issue在未来版本逐渐完善后可能消失",
                player
        );
    }

    @Desugar
    private record BlockMetaKey(Block block, int meta) {

        @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof BlockMetaKey other)) return false;
                return this.block == other.block && this.meta == other.meta;
            }

            @Override
            public int hashCode() {
                return 31 * System.identityHashCode(block) + meta;
            }
        }

    @Nullable
    private static TileEntity tryGetTileEntityForMatch(EntityPlayer player, Vector3i pos, boolean safeAsyncGuard) {
        try {
            return player.worldObj.getTileEntity(pos.x, pos.y, pos.z);
        } catch (RuntimeException e) {
            if (safeAsyncGuard) {
                return null;
            }
            throw e;
        }
    }

    public static void checkCompatibility() {
        hasCheck = true;
        hasGregTechTileEntity();
        hasTileEntityOre();
        hasTileEntityMetaGeneratedBlock();
        hasBlockOresAbstract();
        hasBWMetaGeneratedSmallOres();
        hasBWMetaGeneratedOres();
        hasAEOreQuartz();
        hasAEOreQuartzCharged();
        hasBlockBaseOre();
    }

    public static boolean hasGregTechTileEntity = false;
    public static void hasGregTechTileEntity() {
        try {
            Class<?> clazz = Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
            hasGregTechTileEntity = true;
        } catch (ClassNotFoundException e) {
            LOG.warn("未检测到 IGregTechTileEntity");
            hasGregTechTileEntity = false;
        }
    }

    public static boolean hasTileEntityOre = false;
    public static void hasTileEntityOre() {
        try {
            Class<?> clazz = Class.forName("gregtech.common.blocks.TileEntityOres");
            hasTileEntityOre = true;
        } catch (ClassNotFoundException e) {
            LOG.warn("未检测到 TileEntityOres");
            hasTileEntityOre = false;
        }
    }

    public static boolean hasTileEntityMetaGeneratedBlock = false;
    public static void hasTileEntityMetaGeneratedBlock() {
        try {
            Class<?> clazz = Class.forName("bartworks.system.material.TileEntityMetaGeneratedBlock");
            hasTileEntityMetaGeneratedBlock = true;
        } catch (ClassNotFoundException e) {
            LOG.warn("未检测到 TileEntityMetaGeneratedBlock");
            hasTileEntityMetaGeneratedBlock = false;
        }
    }

    public static boolean hasBlockOresAbstract = false;
    public static void hasBlockOresAbstract() {
        try {
            Class<?> clazz = Class.forName("gregtech.common.blocks.BlockOresAbstract");
            hasBlockOresAbstract = true;
        } catch (ClassNotFoundException e) {
            LOG.warn("未检测到 BlockOresAbstract");
            hasBlockOresAbstract = false;
        }
    }

    public static boolean hasBWMetaGeneratedSmallOres = false;
    public static void hasBWMetaGeneratedSmallOres() {
        try {
            Class<?> clazz = Class.forName("bartworks.system.material.BWMetaGeneratedSmallOres");
            hasBWMetaGeneratedSmallOres = true;
        } catch (ClassNotFoundException e) {
            LOG.warn("未检测到 BWMetaGeneratedSmallOres");
            hasBWMetaGeneratedSmallOres = false;
        }
    }

    public static boolean hasBWMetaGeneratedOres = false;
    public static void hasBWMetaGeneratedOres() {
        try {
            Class<?> clazz = Class.forName("bartworks.system.material.BWMetaGeneratedOres");
            hasBWMetaGeneratedOres = true;
        } catch (ClassNotFoundException e) {
            LOG.warn("未检测到 BWMetaGeneratedOres");
            hasBWMetaGeneratedOres = false;
        }
    }

    public static boolean hasAEOreQuartz = false;
    public static void hasAEOreQuartz() {
        try {
            Class<?> clazz = Class.forName("appeng.block.solids.OreQuartz");
            hasAEOreQuartz = true;
        } catch (ClassNotFoundException e) {
            LOG.warn("未检测到 OreQuartz");
            hasAEOreQuartz = false;
        }
    }

    public static boolean hasAEOreQuartzCharged = false;
    public static void hasAEOreQuartzCharged() {
        try {
            Class<?> clazz = Class.forName("appeng.block.solids.OreQuartzCharged");
            hasAEOreQuartzCharged = true;
        } catch (ClassNotFoundException e) {
            LOG.warn("未检测到 OreQuartz");
            hasAEOreQuartzCharged = false;
        }
    }

    public static boolean hasBlockBaseOre =false;
    public static void hasBlockBaseOre() {
        try {
            Class<?> clazz = Class.forName("gtPlusPlus.core.block.base.BlockBaseOre");
            hasBlockBaseOre = true;
        } catch (ClassNotFoundException e) {
            LOG.warn("未检测到 BlockBaseOre");
            hasBlockBaseOre = false;
        }
    }
}
