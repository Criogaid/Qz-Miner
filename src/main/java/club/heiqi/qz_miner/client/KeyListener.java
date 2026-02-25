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
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
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

    @SubscribeEvent
    public void onInput(InputEvent event) {
        // ========== 按住连锁键 + 滚轮切换子模式 ==========
        if (onChain && event instanceof InputEvent.MouseInputEvent && Mouse.getEventDWheel() != 0) {
            MinerModeState minerModeState = ((ClientProxy) MyMod.proxy).clientState.minerModeState;
            int dWheel = Mouse.getEventDWheel();
            if (dWheel < 0) {
                minerModeState.nextSecondMode();
            } else if (dWheel > 0) {
                minerModeState.previousSecondMode();
            }
            // 网络同步当前子模式
            MyMod.networkMain.network.sendToServer(new PacketMinerModeState(minerModeState));
            MessageUtils.printSelfMessage("当前子模式: "+I18n.format(minerModeState.currentSecondMode()));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.ClientTickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        boolean noScreen = mc.currentScreen == null;
        boolean chainPressed = noScreen && isBindingPressed(chainSwitch);
        boolean mainModePressed = noScreen && isBindingPressed(mainModeSwitch);
        handleChainKeyState(chainPressed);
        handleMainModeKeyState(mainModePressed);
    }

    private void handleChainKeyState(boolean pressed) {
        // ===== 状态切换: 开始连锁 =====
        if (pressed && !onChain) {
            MyMod.networkMain.network.sendToServer(new PacketChainSwitcher(true));
            ((ClientProxy) MyMod.proxy).minerRenderer.inPressChainKey = true;
            MyMod.networkMain.network.sendToServer(new PacketMinerConfig(new MinerConfig()));
        }
        // ===== 状态切换: 关闭连锁 =====
        if (!pressed && onChain) {
            MyMod.networkMain.network.sendToServer(new PacketChainSwitcher(false));
            ((ClientProxy) MyMod.proxy).minerRenderer.inPressChainKey = false;
        }
        onChain = pressed;
    }

    private void handleMainModeKeyState(boolean pressed) {
        if (pressed && !onMainModeSwitch) {
            MinerModeState minerModeState = ((ClientProxy) MyMod.proxy).clientState.minerModeState;
            MessageUtils.printSelfMessage("当前模式: " + I18n.format(minerModeState.nextMainMode()));
            MyMod.networkMain.network.sendToServer(new PacketMinerModeState(minerModeState));
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
