package club.heiqi.qz_miner;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class Config {
    public static String configPath;
    public static Configuration config;

    public static int bigRadius = 8;
    public static int blockLimit = 1024;
    public static int smallRadius = 2;
    public static int tunnelWidth = 1;
    public static int deferredCheckBudgetPerTick = 48;
    public static int breakBudgetPerTick = 64;
    public static boolean safeAsyncWorldAccess = true;
    public static boolean enableLootGameMineReveal = true;
    public static int lootGameMineRevealScanRadius = 2;
    public static double lootGameMineRevealHoldSeconds = 5.0D;
    public static double lootGameMineRevealCooldownSeconds = 30.0D;
    public static double lootGameMineRevealRenderSeconds = 5.0D;

    public static final String CLIENT_CATEGORY = "Client";
    public static boolean usePreview = true, useChainDoneMessage = true;
    public static double lootGameMineRevealLineWidth = 1.5D;
    public static double addExhaustion;

    public void init(File configFile) {
        if (config == null) {
            configPath = configFile.getAbsolutePath();
            config = new Configuration(configFile);
        }
        load();
    }

    public void load() {
        bigRadius = config.getInt("bigRadius", Configuration.CATEGORY_GENERAL, 8, 0, Integer.MAX_VALUE, "最大连锁半径");
        blockLimit = config.getInt("blockLimit", Configuration.CATEGORY_GENERAL, 1024, 0, Integer.MAX_VALUE, "最大连锁数量");
        smallRadius = config.getInt("smallRadius", Configuration.CATEGORY_GENERAL, 2, 0, Integer.MAX_VALUE, "连锁 小区域 检测半径");
        tunnelWidth = config.getInt("tunnelWidth", Configuration.CATEGORY_GENERAL, 1, 0, Integer.MAX_VALUE, "隧道半径");
        deferredCheckBudgetPerTick = config.getInt(
                "deferredCheckBudgetPerTick",
                Configuration.CATEGORY_GENERAL,
                48,
                1,
                512,
                "每tick主线程处理延迟判定请求的预算"
        );
        breakBudgetPerTick = config.getInt(
                "breakBudgetPerTick",
                Configuration.CATEGORY_GENERAL,
                64,
                1,
                512,
                "每tick主线程执行连锁动作的预算"
        );
        safeAsyncWorldAccess = config.getBoolean(
                "safeAsyncWorldAccess",
                Configuration.CATEGORY_GENERAL,
                true,
                "启用异步世界访问保护: 在非服务器线程中避免触发区块加载和TileEntity访问"
        );
        enableLootGameMineReveal = config.getBoolean(
                "enableLootGameMineReveal",
                Configuration.CATEGORY_GENERAL,
                true,
                "启用 LootGames 扫雷揭示功能（按住连锁键达到蓄力后揭示最近地雷）"
        );
        lootGameMineRevealScanRadius = config.getInt(
                "lootGameMineRevealScanRadius",
                Configuration.CATEGORY_GENERAL,
                2,
                1,
                16,
                "LootGames 扫雷方块搜索半径"
        );
        lootGameMineRevealHoldSeconds = config.get(
                Configuration.CATEGORY_GENERAL,
                "lootGameMineRevealHoldSeconds",
                5.0D,
                "LootGames 扫雷揭示触发前需要持续按住连锁键的秒数",
                0.0D,
                60.0D
        ).getDouble();
        lootGameMineRevealCooldownSeconds = config.get(
                Configuration.CATEGORY_GENERAL,
                "lootGameMineRevealCooldownSeconds",
                30.0D,
                "LootGames 扫雷揭示冷却时间（秒）",
                0.0D,
                3600.0D
        ).getDouble();
        lootGameMineRevealRenderSeconds = config.get(
                CLIENT_CATEGORY,
                "lootGameMineRevealRenderSeconds",
                5.0D,
                "LootGames 扫雷揭示结果在客户端保留渲染时长（秒）",
                0.0D,
                60.0D
        ).getDouble();
        lootGameMineRevealLineWidth = config.get(
                CLIENT_CATEGORY,
                "lootGameMineRevealLineWidth",
                1.5D,
                "LootGames 扫雷揭示线框粗细",
                0.1D,
                10.0D
        ).getDouble();
        addExhaustion = config.get(CLIENT_CATEGORY, "addExhaustion", 0.025, "每次挖掘增加的饥饿值", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();

        usePreview = config.getBoolean("usePreview", CLIENT_CATEGORY, true, "是否使用连锁预览功能");
        useChainDoneMessage = config.getBoolean("useChainDoneMessage", CLIENT_CATEGORY, true, "是否使用连锁后的消息提示");

        if (config.hasChanged()) {
            config.save();
        }
    }

    @SubscribeEvent
    public void onConfigChangeEvent(ConfigChangedEvent event) {
        if (!event.modID.equalsIgnoreCase(Constant.MODID)) return;
        Constant.LOG.info("保存事件触发");
        load();
    }

    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }
}
