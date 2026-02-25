package club.heiqi.qz_miner.client;

import club.heiqi.qz_miner.ClientProxy;
import club.heiqi.qz_miner.MyMod;
import club.heiqi.qz_miner.core.MinerConfig;
import club.heiqi.qz_miner.core.MinerModeState;
import club.heiqi.qz_miner.network.PacketChainSwitcher;
import club.heiqi.qz_miner.network.PacketMinerConfig;
import club.heiqi.qz_miner.network.PacketMinerModeState;
import club.heiqi.qz_miner.utils.MessageUtils;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@SideOnly(Side.CLIENT)
public class KeyListener {
    public Logger LOG = LogManager.getLogger();
    // 默认 ~ 键
    public static KeyBinding chainSwitch = new KeyBinding(
            "key.qz_miner.chainSwitch", Keyboard.KEY_GRAVE, "key.categories.qz_miner"
    );
    public static KeyBinding mainModeSwitch = new KeyBinding(
            "key.qz_miner.mainModeSwitch", Keyboard.KEY_V/*鼠标中键*/, "key.categories.qz_miner"
    );
    public boolean onChain = false;
    public boolean onMainModeSwitch = false;
    private int lastWorldIdentity = 0;
    private boolean pendingReconnectModeSync = false;

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        if (!onChain) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || event.dwheel == 0) {
            return;
        }
        MinerModeState minerModeState = ((ClientProxy) MyMod.proxy).clientState.minerModeState;
        if (event.dwheel < 0) {
            minerModeState.nextSecondMode();
        } else {
            minerModeState.previousSecondMode();
        }
        // 网络同步当前子模式
        MyMod.networkMain.network.sendToServer(new PacketMinerModeState(minerModeState));
        MessageUtils.printSelfMessage("当前子模式: " + I18n.format(minerModeState.currentSecondMode()));
        ((ClientProxy) MyMod.proxy).minerRenderer.refreshPreviewAfterModeChanged();
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.ClientTickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        trackWorldChangeAndScheduleSync(mc);

        boolean noScreen = mc.currentScreen == null;
        syncModeAfterReconnectIfNeeded(mc, noScreen);

        boolean chainPressed = noScreen && isBindingPressed(chainSwitch);
        boolean mainModePressed = noScreen && isBindingPressed(mainModeSwitch);
        handleChainKeyState(chainPressed);
        handleMainModeKeyState(mainModePressed);
    }

    private void handleChainKeyState(boolean pressed) {
        // ===== 状态切换: 开始连锁 =====
        if (pressed && !onChain) {
            MyMod.networkMain.network.sendToServer(new PacketChainSwitcher(true));
            MinerModeState minerModeState = ((ClientProxy) MyMod.proxy).clientState.minerModeState;
            boolean mineRevealMode = minerModeState.isMineRevealMode();
            // 扫雷模式不应启动本地连锁预览线程，避免无意义搜索占用客户端。
            ((ClientProxy) MyMod.proxy).minerRenderer.inPressChainKey = !mineRevealMode;
            if (!mineRevealMode) {
                MyMod.networkMain.network.sendToServer(new PacketMinerConfig(new MinerConfig()));
            }
        }
        // ===== 状态切换: 关闭连锁 =====
        if (!pressed && onChain) {
            MyMod.networkMain.network.sendToServer(new PacketChainSwitcher(false));
            ((ClientProxy) MyMod.proxy).minerRenderer.inPressChainKey = false;
        }
        onChain = pressed;
    }

    private void trackWorldChangeAndScheduleSync(Minecraft mc) {
        World world = mc.theWorld;
        int worldIdentity = world == null ? 0 : System.identityHashCode(world);
        if (worldIdentity == lastWorldIdentity) {
            return;
        }
        lastWorldIdentity = worldIdentity;
        onChain = false;
        onMainModeSwitch = false;
        ((ClientProxy) MyMod.proxy).minerRenderer.inPressChainKey = false;
        ((ClientProxy) MyMod.proxy).minerRenderer.refreshPreviewAfterModeChanged();
        pendingReconnectModeSync = world != null;
    }

    private void syncModeAfterReconnectIfNeeded(Minecraft mc, boolean noScreen) {
        if (!pendingReconnectModeSync || !noScreen || mc.thePlayer == null) {
            return;
        }
        // 延后到玩家完成登录初始化后再同步，避免服务端Manager尚未就绪导致首包丢失。
        if (mc.thePlayer.ticksExisted < 20) {
            return;
        }
        MinerModeState minerModeState = ((ClientProxy) MyMod.proxy).clientState.minerModeState;
        MyMod.networkMain.network.sendToServer(new PacketMinerModeState(minerModeState));
        pendingReconnectModeSync = false;
    }

    private void handleMainModeKeyState(boolean pressed) {
        if (pressed && !onMainModeSwitch) {
            MinerModeState minerModeState = ((ClientProxy) MyMod.proxy).clientState.minerModeState;
            MessageUtils.printSelfMessage("当前模式: " + I18n.format(minerModeState.nextMainMode()));
            MyMod.networkMain.network.sendToServer(new PacketMinerModeState(minerModeState));
            ((ClientProxy) MyMod.proxy).minerRenderer.refreshPreviewAfterModeChanged();
        }
        onMainModeSwitch = pressed;
    }

    private static boolean isBindingPressed(KeyBinding keyBinding) {
        int keyCode = keyBinding.getKeyCode();
        if (keyCode < 0) {
            return Mouse.isButtonDown(keyCode + 100);
        }
        return Keyboard.isKeyDown(keyCode);
    }

    public void registry() {
        ClientRegistry.registerKeyBinding(mainModeSwitch);
        ClientRegistry.registerKeyBinding(chainSwitch);
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }
}
