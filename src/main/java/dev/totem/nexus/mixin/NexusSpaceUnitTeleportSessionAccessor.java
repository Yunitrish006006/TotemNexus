package dev.totem.nexus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(targets = "dev.totem.nexus.space.NexusSpaceUnitAuthority$TeleportSession")
public interface NexusSpaceUnitTeleportSessionAccessor {
    @Accessor("targetUnitId")
    UUID deadrecall$getTargetUnitId();
}
