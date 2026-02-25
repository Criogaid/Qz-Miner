package club.heiqi.qz_miner.core;

import club.heiqi.qz_miner.Config;
import club.heiqi.qz_miner.utils.PlayerUuidCompat;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 以玩家UUID为核心的连锁管理器
 * 容器核心为玩家
 */
public class Manager {
    public Logger LOG = LogManager.getLogger();
    public EntityPlayerMP player;
    public final UUID playerUUID;
    public MinerConfig pConfig = new MinerConfig();
    public volatile MinerModeState minerModeState = new MinerModeState();
    /**是否按下连锁键*/
    public volatile boolean inPressChainKey = false;
    public boolean inOperate = false;

    public Manager(EntityPlayerMP player) {
        this.player = player;
        playerUUID = PlayerUuidCompat.getPlayerUUID(player);
    }

    public BaseOperator operator = null;
    @SubscribeEvent
    public void onBlockBreakEvent(BlockEvent.BreakEvent event) {
        // 0.是交互模式 1.不是同一个玩家且不是服务器线程
        if (!minerModeState.isBreakMode() || !isSamePlayer_checkOnServer(event.getPlayer(), playerUUID))
            return;
        // 正在连锁中 或 未按下连锁键 不处理 避免重复触发连锁
        if (inOperate || !inPressChainKey) {
            return;
        }
        inOperate = true;

        Vector3i pos = new Vector3i(event.x, event.y, event.z);
        // ==========  触发连锁  ==========
        player = (EntityPlayerMP) event.getPlayer();
        operator = new BaseOperator(pos, this);
        operator.registry();
    }

    /**Bottom = 0, Top = 1, East = 2, West = 3, North = 4, South = 5.*/
    public int hitSide = 1;
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteractEvent(PlayerInteractEvent event) {
        if (!minerModeState.isInteractMode() || !isSamePlayer_checkOnServer(event.entityPlayer, playerUUID))
            return;
        // 正在连锁中 或 未按下连锁键 不处理 避免重复触发连锁
        if (inOperate || !inPressChainKey) {
            return;
        }
        inOperate = true;

        Vector3i pos = new Vector3i(event.x, event.y, event.z);
        hitSide = event.face;
        // ==========  触发连锁  ==========
        player = (EntityPlayerMP) event.entityPlayer;
        operator = new InteractOperator(pos, this);
        operator.registry();
    }

    public ArrayList<ItemStack> drops = new ArrayList<>();
    @SubscribeEvent
    public void onHarvestDropEvent(BlockEvent.HarvestDropsEvent event) {
        // 事件触发者 0.掉落物没有收获者 1.不是玩家自己 2.不是服务器玩家类 3.不是服务器线程 任意一个满足 不处理
        if (event.harvester == null || !isSamePlayer_checkOnServer(event.harvester, playerUUID))
            return;
        // 该管理器只收集此 player 的掉落物

        // 未在连锁不处理 - 检查drops收集容器是否有东西 此时掉落到地面
        if (!inOperate) {
            return;
        }

        // 收集掉落物
        LinkedHashMap<DropKey, ItemStack> mergedDrops = new LinkedHashMap<>();
        for (ItemStack container : drops) {
            mergeDropStack(mergedDrops, container);
        }
        for (ItemStack drop : event.drops) {
            mergeDropStack(mergedDrops, drop);
        }
        drops.clear();
        drops.addAll(mergedDrops.values());

        // 阻止原始掉落
        event.drops.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.WorldTickEvent.Phase.START) return;
        if (!inOperate && !inPressChainKey) dropCollects();
    }

    public void dropCollects() {
        // 只收集自己掉落的东西!
        if (!drops.isEmpty()) {
            double dropX = Math.floor(player.posX) + 0.5D;
            double dropY = Math.floor(player.posY);
            double dropZ = Math.floor(player.posZ) + 0.5D;
            for (ItemStack itemStack : drops) {
                EntityItem entityItem = new EntityItem(player.worldObj, dropX, dropY, dropZ, itemStack);
                entityItem.motionX = 0.0D;
                entityItem.motionY = 0.0D;
                entityItem.motionZ = 0.0D;
                entityItem.velocityChanged = true;
                player.worldObj.spawnEntityInWorld(entityItem);
            }
            drops.clear();
        }
    }

    public void registry() {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        LOG.info("注册成功");
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        cleanupState();
    }

    public void unRegistry() {
        cleanupState();
        FMLCommonHandler.instance().bus().unregister(this);
        MinecraftForge.EVENT_BUS.unregister(this);
        LOG.info("注销成功");
    }

    public void cleanupState() {
        inPressChainKey = false;
        inOperate = false;
    }

    /**从网络接收来自客户端的配置*/
    public void receiveClientConfig(MinerConfig minerConfig) {
        pConfig.bigRadius = Math.max(Math.min(minerConfig.bigRadius, Config.bigRadius), 0);
        pConfig.blockLimit = Math.max(Math.min(minerConfig.blockLimit, Config.blockLimit), 0);
        pConfig.smallRadius = Math.max(Math.min(minerConfig.smallRadius, Config.smallRadius), 0);
        pConfig.tunnelWidth = Math.max(Math.min(minerConfig.tunnelWidth, Config.tunnelWidth), 0);
        pConfig.useChainDoneMessage = minerConfig.useChainDoneMessage;
    }



    public static boolean isSamePlayer_checkOnServer(EntityPlayer player, UUID playerUUID) {
        UUID currentUUID = PlayerUuidCompat.getPlayerUUID(player);
        return (currentUUID != null && currentUUID.equals(playerUUID)  // 1.玩家UUID相同
                && player instanceof EntityPlayerMP  // 2.是服务器玩家类
                && Thread.currentThread().getName().toLowerCase().contains("server")  // 3.发生在服务器线程
                && !(player instanceof FakePlayer)  // 4.不是假玩家
        );
    }

    private static void mergeDropStack(Map<DropKey, ItemStack> mergedDrops, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return;
        }
        DropKey key = DropKey.of(stack);
        ItemStack existing = mergedDrops.get(key);
        if (existing == null) {
            mergedDrops.put(key, stack);
            return;
        }
        existing.stackSize += stack.stackSize;
    }

    private static final class DropKey {
        private final Item item;
        private final int itemDamage;
        private final NBTTagCompound tagSnapshot;

        private DropKey(Item item, int itemDamage, NBTTagCompound tagSnapshot) {
            this.item = item;
            this.itemDamage = itemDamage;
            this.tagSnapshot = tagSnapshot;
        }

        private static DropKey of(ItemStack stack) {
            NBTTagCompound tag = stack.getTagCompound();
            NBTTagCompound tagCopy = tag == null ? null : (NBTTagCompound) tag.copy();
            return new DropKey(stack.getItem(), stack.getItemDamage(), tagCopy);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DropKey)) return false;
            DropKey dropKey = (DropKey) o;
            return itemDamage == dropKey.itemDamage
                    && Objects.equals(item, dropKey.item)
                    && Objects.equals(tagSnapshot, dropKey.tagSnapshot);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, itemDamage, tagSnapshot);
        }
    }
}
