package dev.totem.nexus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import dev.totem.nexus.migration.LegacyNexusGameRuleMigration;
import net.minecraft.world.level.gamerules.GameRuleMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Covers both level rule decoding and the separate game_rules saved-data file. */
@Mixin(GameRuleMap.class)
public abstract class NexusLegacyGameRuleMapMixin {
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lcom/mojang/serialization/Codec;xmap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Codec<GameRuleMap> totem$migrateLegacyKeys(Codec<GameRuleMap> codec) {
        return LegacyNexusGameRuleMigration.wrap(codec);
    }
}
