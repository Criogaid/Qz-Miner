package club.heiqi.qz_miner.utils;

import net.minecraft.entity.player.EntityPlayer;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class PlayerUuidCompat {
    private static final String[] PLAYER_UUID_METHODS = {
            "getUniqueID",
            "getPersistentID",
            "func_110124_au"
    };

    private static final String[] PROFILE_METHODS = {
            "getGameProfile",
            "func_146103_bH"
    };

    private PlayerUuidCompat() {}

    public static UUID getPlayerUUID(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        for (String methodName : PLAYER_UUID_METHODS) {
            Object result = invokeNoArg(player, methodName);
            if (result instanceof UUID) {
                return (UUID) result;
            }
        }

        for (String profileMethod : PROFILE_METHODS) {
            Object profile = invokeNoArg(player, profileMethod);
            if (profile == null) {
                continue;
            }
            Object id = invokeNoArg(profile, "getId");
            if (id instanceof UUID) {
                return (UUID) id;
            }
        }

        String name = player.getCommandSenderName();
        return name == null ? null : UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
