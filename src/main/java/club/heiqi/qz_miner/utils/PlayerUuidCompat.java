package club.heiqi.qz_miner.utils;

import net.minecraft.entity.player.EntityPlayer;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    private static final MethodCache PLAYER_UUID_CACHE = new MethodCache(PLAYER_UUID_METHODS);
    private static final MethodCache PROFILE_CACHE = new MethodCache(PROFILE_METHODS);
    private static final MethodCache PROFILE_ID_CACHE = new MethodCache("getId");

    private PlayerUuidCompat() {}

    public static UUID getPlayerUUID(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        Object uuidResult = PLAYER_UUID_CACHE.invoke(player);
        if (uuidResult instanceof UUID) {
            return (UUID) uuidResult;
        }

        Object profile = PROFILE_CACHE.invoke(player);
        Object id = PROFILE_ID_CACHE.invoke(profile);
        if (id instanceof UUID) {
            return (UUID) id;
        }

        String name = player.getCommandSenderName();
        return name == null ? null : UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static final class MethodCache {
        private final String[] methodNames;
        private final ConcurrentMap<Class<?>, Method> methods = new ConcurrentHashMap<>();
        private final Set<Class<?>> misses = ConcurrentHashMap.newKeySet();

        private MethodCache(String... methodNames) {
            this.methodNames = methodNames;
        }

        private Object invoke(Object target) {
            if (target == null) {
                return null;
            }
            Class<?> targetClass = target.getClass();
            Method method = methods.get(targetClass);
            if (method == null) {
                if (misses.contains(targetClass)) {
                    return null;
                }
                method = resolve(targetClass);
                if (method == null) {
                    misses.add(targetClass);
                    return null;
                }
                Method existing = methods.putIfAbsent(targetClass, method);
                if (existing != null) {
                    method = existing;
                }
            }
            try {
                return method.invoke(target);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private Method resolve(Class<?> targetClass) {
            for (String methodName : methodNames) {
                try {
                    return targetClass.getMethod(methodName);
                } catch (NoSuchMethodException ignored) {
                    // 继续尝试下一个兼容方法名
                }
            }
            return null;
        }
    }
}
