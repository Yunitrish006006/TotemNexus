package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests the current server-authoritative teleport-array material reference table. */
public record RequestMaterialCatalogPayload() implements CustomPacketPayload {
    public static final Type<RequestMaterialCatalogPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "request_material_catalog"));

    public static final StreamCodec<FriendlyByteBuf, RequestMaterialCatalogPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                    },
                    buf -> new RequestMaterialCatalogPayload()
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
