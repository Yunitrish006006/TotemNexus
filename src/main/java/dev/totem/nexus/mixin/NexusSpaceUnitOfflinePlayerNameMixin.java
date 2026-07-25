package dev.totem.nexus.mixin;

import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import dev.totem.nexus.space.NexusSpaceUnitAuthority;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves names for offline Space Unit friends from the server profile cache.
 *
 * <p>The original fallback intentionally shortens the UUID. That is useful only when the server
 * has never seen a profile; known offline players should retain their last cached Minecraft name.</p>
 */
@Mixin(NexusSpaceUnitAuthority.class)
public abstract class NexusSpaceUnitOfflinePlayerNameMixin {
    @Inject(method = "friendEntry", at = @At("HEAD"), cancellable = true)
    private static void deadrecall$useCachedNameForOfflineFriend(
            MinecraftServer server,
            UUID playerId,
            String status,
            CallbackInfoReturnable<SpaceUnitFriendsPayload.Entry> cir
    ) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        String name = online != null
                ? online.getName().getString()
                : deadrecall$cachedPlayerName(server, playerId).orElseGet(() -> deadrecall$shortPlayerId(playerId));
        cir.setReturnValue(new SpaceUnitFriendsPayload.Entry(playerId, name, online != null, status));
    }

    @Inject(method = "playerDisplayName", at = @At("HEAD"), cancellable = true)
    private static void deadrecall$useCachedPlayerDisplayName(
            MinecraftServer server,
            UUID playerId,
            CallbackInfoReturnable<String> cir
    ) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            cir.setReturnValue(online.getName().getString());
            return;
        }
        deadrecall$cachedPlayerName(server, playerId).ifPresent(cir::setReturnValue);
    }

    private static Optional<String> deadrecall$cachedPlayerName(MinecraftServer server, UUID playerId) {
        Object cache = deadrecall$profileCache(server);
        if (cache == null) {
            return Optional.empty();
        }

        Object profile = deadrecall$lookupProfile(cache, playerId);
        if (profile == null) {
            return Optional.empty();
        }

        String name = deadrecall$invokeNameAccessor(profile, "name");
        if (name == null || name.isBlank()) {
            name = deadrecall$invokeNameAccessor(profile, "getName");
        }
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    /**
     * Minecraft 26.x mappings have changed the public cache accessor name more than once. Resolve
     * the cache reflectively so this compatibility shim does not depend on one mapping spelling.
     */
    private static Object deadrecall$profileCache(MinecraftServer server) {
        Object cache = deadrecall$invokeNoArg(server, "getProfileCache");
        if (cache == null) {
            cache = deadrecall$invokeNoArg(server, "getUserCache");
        }
        if (cache == null) {
            cache = deadrecall$invokeNoArg(server, "profileCache");
        }
        if (cache == null) {
            cache = deadrecall$invokeNoArg(server, "userCache");
        }
        if (cache != null) {
            return cache;
        }

        Object services = deadrecall$invokeNoArg(server, "services");
        if (services == null) {
            services = deadrecall$invokeNoArg(server, "getServices");
        }
        if (services == null) {
            return null;
        }

        cache = deadrecall$invokeNoArg(services, "profileCache");
        if (cache == null) {
            cache = deadrecall$invokeNoArg(services, "userCache");
        }
        if (cache == null) {
            cache = deadrecall$invokeNoArg(services, "nameToIdCache");
        }
        return cache;
    }

    private static Object deadrecall$lookupProfile(Object cache, UUID playerId) {
        for (Method method : cache.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != UUID.class) {
                continue;
            }

            String methodName = method.getName();
            if (!methodName.equals("get")
                    && !methodName.equals("getByUuid")
                    && !methodName.equals("getById")
                    && !methodName.equals("findById")
                    && !methodName.equals("findProfileById")) {
                continue;
            }

            try {
                Object value = method.invoke(cache, playerId);
                if (value instanceof Optional<?> optional) {
                    return optional.orElse(null);
                }
                return value;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next compatible accessor.
            }
        }
        return null;
    }

    private static Object deadrecall$invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String deadrecall$invokeNameAccessor(Object profile, String methodName) {
        try {
            Method method = profile.getClass().getMethod(methodName);
            Object value = method.invoke(profile);
            return value instanceof String string ? string : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String deadrecall$shortPlayerId(UUID playerId) {
        String id = playerId.toString();
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}
