package com.adaptor.deadrecall.space;

import com.adaptor.deadrecall.network.RefreshSpaceUnitQuotePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class SpaceUnitRefreshNetworking implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(
                RefreshSpaceUnitQuotePayload.TYPE,
                RefreshSpaceUnitQuotePayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                RefreshSpaceUnitQuotePayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    if (SpaceUnitHandler.SOURCE_TYPE_LODESTONE.equals(payload.sourceType())) {
                        SpaceUnitStructureRefresh.refresh(context.server(), payload.sourceUnitId());
                    }
                    SpaceUnitStructureRefresh.refresh(context.server(), payload.targetUnitId());
                    SpaceUnitHandler.sendSpaceUnitMap(
                            context.player(),
                            payload.sourceType(),
                            payload.sourceUnitId()
                    );
                })
        );
    }
}
