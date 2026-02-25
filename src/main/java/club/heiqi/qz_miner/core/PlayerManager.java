package club.heiqi.qz_miner.core;

import club.heiqi.qz_miner.utils.PlayerUuidCompat;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    public static Logger LOG = LogManager.getLogger();
    public Map<UUID, Manager> managers = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP playerMP) {
            UUID uuid = PlayerUuidCompat.getPlayerUUID(playerMP);
            if (uuid == null) {
                LOG.warn("注册玩家失败, 无法获取UUID: {}", playerMP.getDisplayName());
                return;
            }
            Manager manager = new Manager(playerMP);
            managers.put(uuid, manager);
            manager.registry();
            LOG.info("注册 玩家: {}: {}", playerMP.getDisplayName(), uuid);
        }
    }

    /**可能是掉线触发的登出事件 -> 此时玩家可能仍在连锁过程中*/
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP playerMP) {
            UUID uuid = PlayerUuidCompat.getPlayerUUID(playerMP);
            if (uuid == null) {
                LOG.warn("卸载管理器时无法获取UUID: {}", playerMP.getDisplayName());
                return;
            }
            Manager manager = managers.get(uuid);
            if (manager == null) {
                LOG.warn("卸载管理器时未找到 玩家: {}: {}", playerMP.getDisplayName(), uuid);
                return;
            }
            manager.unRegistry();
            managers.remove(uuid);
            LOG.info("卸载 玩家: {}: {}", playerMP.getDisplayName(), uuid);
        }
    }

    public void registry() {
        // 只在服务端注册
        FMLCommonHandler.instance().bus().register(this);
        System.out.println("注册玩家管理器");
    }
}
