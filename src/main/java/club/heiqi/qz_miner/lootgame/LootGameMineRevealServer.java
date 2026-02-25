package club.heiqi.qz_miner.lootgame;

import club.heiqi.qz_miner.Config;
import club.heiqi.qz_miner.MyMod;
import club.heiqi.qz_miner.core.Manager;
import club.heiqi.qz_miner.network.PacketSweepMine;
import club.heiqi.qz_miner.utils.PlayerUuidCompat;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class LootGameMineRevealServer {
    private static final Logger LOG = LogManager.getLogger();
    private static final long COOLDOWN_HINT_INTERVAL_NANOS = 1_000_000_000L;

    private final Map<UUID, Long> pressStartNanos = new HashMap<>();
    private final Map<UUID, Long> lastRevealNanos = new HashMap<>();
    private final Map<UUID, Long> lastCooldownHintNanos = new HashMap<>();
    private boolean registered = false;

    public void register() {
        if (registered) {
            return;
        }
        if (!LootGameMineCompat.isAvailable()) {
            LOG.warn("LootGames 扫雷揭示服务端监听未注册: 未检测到可用的 LootGames 兼容环境。");
            return;
        }
        FMLCommonHandler.instance().bus().register(this);
        registered = true;
        LOG.info("LootGames 扫雷揭示服务端监听已注册。");
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient()) {
            return;
        }
        if (!Config.enableLootGameMineReveal || MyMod.playerManager == null) {
            return;
        }
        if (!(event.player instanceof EntityPlayerMP playerMP)) {
            return;
        }

        UUID uuid = PlayerUuidCompat.getPlayerUUID(playerMP);
        if (uuid == null) {
            return;
        }
        Manager manager = MyMod.playerManager.managers.get(uuid);
        if (manager == null || !manager.minerModeState.isMineRevealMode() || !manager.inPressChainKey) {
            pressStartNanos.remove(uuid);
            lastCooldownHintNanos.remove(uuid);
            return;
        }

        long now = System.nanoTime();
        Long start = pressStartNanos.get(uuid);
        if (start == null) {
            pressStartNanos.put(uuid, now);
            return;
        }

        long holdNanos = (long) (Math.max(0.0D, Config.lootGameMineRevealHoldSeconds) * 1_000_000_000L);
        if (now - start < holdNanos) {
            return;
        }

        long cooldownNanos = (long) (Math.max(0.0D, Config.lootGameMineRevealCooldownSeconds) * 1_000_000_000L);
        Long lastUse = lastRevealNanos.get(uuid);
        if (lastUse != null && now - lastUse >= cooldownNanos) {
            lastRevealNanos.remove(uuid);
            lastUse = null;
        }
        if (lastUse != null && now - lastUse < cooldownNanos) {
            long remainingNanos = cooldownNanos - (now - lastUse);
            trySendCooldownHint(playerMP, uuid, now, remainingNanos);
            return;
        }

        ArrayList<Vector3i> mines = LootGameMineCompat.findBombsAround(playerMP, Config.lootGameMineRevealScanRadius);
        lastRevealNanos.put(uuid, now);
        lastCooldownHintNanos.remove(uuid);
        double renderSeconds = Math.max(0.0D, Config.lootGameMineRevealRenderSeconds);
        MyMod.networkMain.network.sendTo(new PacketSweepMine(mines, renderSeconds), playerMP);
        if (mines.isEmpty()) {
            playerMP.addChatMessage(new ChatComponentText("扫雷揭示: 未发现可揭示雷区（请靠近棋盘并确保本局已生成地雷）"));
        } else {
            playerMP.addChatMessage(new ChatComponentText("扫雷揭示: 已锁定最近雷区，显示最近地雷位置"));
        }
    }

    private void trySendCooldownHint(EntityPlayerMP playerMP, UUID uuid, long now, long remainingNanos) {
        Long lastHint = lastCooldownHintNanos.get(uuid);
        if (lastHint != null && now - lastHint < COOLDOWN_HINT_INTERVAL_NANOS) {
            return;
        }
        lastCooldownHintNanos.put(uuid, now);
        double remainSeconds = Math.max(0.0D, remainingNanos / 1_000_000_000.0D);
        playerMP.addChatMessage(new ChatComponentText(
                String.format(Locale.ROOT, "扫雷揭示: 冷却中，%.1f 秒后可再次使用", remainSeconds)
        ));
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = PlayerUuidCompat.getPlayerUUID(event.player);
        if (uuid == null) {
            return;
        }
        pressStartNanos.remove(uuid);
        Long lastUse = lastRevealNanos.get(uuid);
        if (lastUse != null) {
            long cooldownNanos = (long) (Math.max(0.0D, Config.lootGameMineRevealCooldownSeconds) * 1_000_000_000L);
            if (System.nanoTime() - lastUse >= cooldownNanos) {
                lastRevealNanos.remove(uuid);
            }
        }
        lastCooldownHintNanos.remove(uuid);
    }
}
