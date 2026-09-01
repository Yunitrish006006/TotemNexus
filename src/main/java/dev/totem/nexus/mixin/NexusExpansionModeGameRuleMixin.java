package dev.totem.nexus.mixin;

import com.mojang.serialization.DataResult;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.totem.nexus.space.NexusTeleportArrayExpansionRules;
import net.minecraft.world.level.gamerules.GameRule;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Compatibility shim for Fabric API 4.0.8's enum gamerule string transport.
 *
 * <p>The in-world vanilla screen sends {@link GameRule#serialize(Object)},
 * which uses the enum's lower-case {@code toString()}, while Fabric's enum
 * deserializer currently accepts only the upper-case enum constant name.
 * Keep this workaround scoped to the Nexus expansion rule until the upstream
 * Fabric fix is available in the supported dependency range. The elevated
 * mixin priority keeps this wrapper outside Fabric API's default-priority enum
 * wrapper, so lower-case screen values are handled before that wrapper rejects
 * them.</p>
 */
@Mixin(value = GameRule.class, priority = 1100)
public abstract class NexusExpansionModeGameRuleMixin<T> {
    @WrapMethod(method = "deserialize")
    @SuppressWarnings("unchecked")
    private DataResult<T> deadrecall$acceptExpansionModeScreenValue(
            String serialized,
            Operation<DataResult<T>> original) {
        if ((Object) this != NexusTeleportArrayExpansionRules.EXPANSION_MODE) {
            return original.call(serialized);
        }

        NexusTeleportArrayExpansionRules.ExpansionMode mode = switch (serialized) {
            case "local" -> NexusTeleportArrayExpansionRules.ExpansionMode.LOCAL;
            case "centered" -> NexusTeleportArrayExpansionRules.ExpansionMode.CENTERED;
            default -> null;
        };
        if (mode != null) {
            return DataResult.success((T) mode);
        }
        return original.call(serialized);
    }
}
