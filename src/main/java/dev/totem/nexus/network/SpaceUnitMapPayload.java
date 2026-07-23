package dev.totem.nexus.network;

import dev.totem.nexus.space.TeleportInterfaceQuotePolicy;
import dev.totem.nexus.space.TeleportInterfaceType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stable {@code deadrecall:space_unit_map} clientbound wire contract. */
public record SpaceUnitMapPayload(UUID sourceUnitId, String sourceType, String sourceName, String sourceDimension,
                                  int sourceX, int sourceY, int sourceZ, TeleportInterfaceType interfaceType,
                                  List<Entry> entries) implements CustomPacketPayload {
    public static final Type<SpaceUnitMapPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "space_unit_map"));
    public static final int MAX_ENTRIES = 128, MAX_CATALYST_BLOCKS_PER_ENDPOINT = 74, MAX_BASE_AMETHYST_COST = 64;
    public SpaceUnitMapPayload { if (interfaceType == null || entries == null || entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Invalid Space Unit map"); entries = List.copyOf(entries); }
    public record Entry(UUID id, String type, String name, String visibility, boolean friendShared, String dimension,
                        int x, int y, int z, double resonance, int tier, int distanceBlocks, int baseFoodCost,
                        int finalFoodCost, int saturationCost, int hungerCost, int foodPointsNeeded,
                        int safeFoodPointsAvailable, int amethystCost, int amethystAvailable, int baseAmethystCost,
                        int sourceCatalysts, int targetCatalysts, int catalystDiscount, int basePrepareTicks,
                        int prepareTicks, int baseMaxHorizontalDeviation, int maxHorizontalDeviation,
                        int damageChancePercent, int baseStructureWearChancePercent, int structureWearChancePercent,
                        boolean interfaceBonusActive, String interfaceBonusMessageKey, boolean favorite,
                        boolean manageable, boolean owned, int administratorCount, int allowedPlayerCount,
                        boolean canTeleport, String blockedReason) {
        public Entry {
            range(baseFoodCost, 0, TeleportInterfaceQuotePolicy.MAX_FOOD_COST); range(finalFoodCost, 0, TeleportInterfaceQuotePolicy.MAX_FOOD_COST); reduction(baseFoodCost, finalFoodCost);
            if (saturationCost + hungerCost + foodPointsNeeded != 0 && saturationCost + hungerCost + foodPointsNeeded != finalFoodCost) throw new IllegalArgumentException("Inconsistent food allocation");
            range(baseAmethystCost, 0, MAX_BASE_AMETHYST_COST); range(amethystCost, 0, MAX_BASE_AMETHYST_COST); range(sourceCatalysts, 0, MAX_CATALYST_BLOCKS_PER_ENDPOINT); range(targetCatalysts, 0, MAX_CATALYST_BLOCKS_PER_ENDPOINT); range(catalystDiscount, 0, MAX_BASE_AMETHYST_COST);
            range(basePrepareTicks, 0, TeleportInterfaceQuotePolicy.MAX_PREPARE_TICKS); range(prepareTicks, 0, TeleportInterfaceQuotePolicy.MAX_PREPARE_TICKS); reduction(basePrepareTicks, prepareTicks);
            range(baseMaxHorizontalDeviation, 0, TeleportInterfaceQuotePolicy.MAX_DEVIATION); range(maxHorizontalDeviation, 0, TeleportInterfaceQuotePolicy.MAX_DEVIATION); reduction(baseMaxHorizontalDeviation, maxHorizontalDeviation);
            range(damageChancePercent, 0, 60); range(baseStructureWearChancePercent, 0, TeleportInterfaceQuotePolicy.MAX_WEAR_CHANCE_PERCENT); range(structureWearChancePercent, 0, TeleportInterfaceQuotePolicy.MAX_WEAR_CHANCE_PERCENT); reduction(baseStructureWearChancePercent, structureWearChancePercent);
            if (interfaceBonusMessageKey == null || interfaceBonusMessageKey.isBlank() || interfaceBonusMessageKey.length() > 128 || catalystDiscount > Math.max(0, baseAmethystCost - 1) || amethystCost != (baseAmethystCost == 0 ? 0 : Math.max(1, baseAmethystCost - catalystDiscount))) throw new IllegalArgumentException("Invalid Space Unit quote");
        }
        private static void range(int value, int min, int max) { if (value < min || value > max) throw new IllegalArgumentException("Quote value out of range"); }
        private static void reduction(int base, int value) { if (value > base) throw new IllegalArgumentException("Quote increased after specialization"); }
    }
    public static final StreamCodec<FriendlyByteBuf, SpaceUnitMapPayload> CODEC = StreamCodec.of(
            (b,p)->{ b.writeUUID(p.sourceUnitId()); b.writeUtf(p.sourceType(),32); b.writeUtf(p.sourceName(),128); b.writeUtf(p.sourceDimension(),128); b.writeInt(p.sourceX()); b.writeInt(p.sourceY()); b.writeInt(p.sourceZ()); b.writeUtf(p.interfaceType().id(),32); b.writeInt(p.entries().size()); for(Entry e:p.entries()) write(b,e); },
            b->{ UUID id=b.readUUID(); String st=b.readUtf(32), sn=b.readUtf(128), sd=b.readUtf(128); int x=b.readInt(),y=b.readInt(),z=b.readInt(); TeleportInterfaceType it=TeleportInterfaceType.fromId(b.readUtf(32)).orElseThrow(()->new DecoderException("Unknown teleport interface type")); int size=b.readInt(); if(size<0||size>MAX_ENTRIES) throw new DecoderException("Space Unit map entry count out of range"); List<Entry> es=new ArrayList<>(size); for(int i=0;i<size;i++) es.add(read(b)); return new SpaceUnitMapPayload(id,st,sn,sd,x,y,z,it,es); });
    private static void write(FriendlyByteBuf b, Entry e){ b.writeUUID(e.id()); b.writeUtf(e.type(),32); b.writeUtf(e.name(),128); b.writeUtf(e.visibility(),32); b.writeBoolean(e.friendShared()); b.writeUtf(e.dimension(),128); b.writeInt(e.x()); b.writeInt(e.y()); b.writeInt(e.z()); b.writeDouble(e.resonance()); b.writeInt(e.tier()); b.writeInt(e.distanceBlocks()); int[] v={e.baseFoodCost(),e.finalFoodCost(),e.saturationCost(),e.hungerCost(),e.foodPointsNeeded(),e.safeFoodPointsAvailable(),e.amethystCost(),e.amethystAvailable(),e.baseAmethystCost(),e.sourceCatalysts(),e.targetCatalysts(),e.catalystDiscount(),e.basePrepareTicks(),e.prepareTicks(),e.baseMaxHorizontalDeviation(),e.maxHorizontalDeviation(),e.damageChancePercent(),e.baseStructureWearChancePercent(),e.structureWearChancePercent()}; for(int n:v)b.writeInt(n); b.writeBoolean(e.interfaceBonusActive()); b.writeUtf(e.interfaceBonusMessageKey(),128); b.writeBoolean(e.favorite()); b.writeBoolean(e.manageable()); b.writeBoolean(e.owned()); b.writeInt(e.administratorCount()); b.writeInt(e.allowedPlayerCount()); b.writeBoolean(e.canTeleport()); b.writeUtf(e.blockedReason(),128); }
    private static Entry read(FriendlyByteBuf b){ UUID id=b.readUUID(); String t=b.readUtf(32),n=b.readUtf(128),v=b.readUtf(32); boolean fs=b.readBoolean(); String d=b.readUtf(128); int x=b.readInt(),y=b.readInt(),z=b.readInt(); double r=b.readDouble(); int tier=b.readInt(),dist=b.readInt(); int[] a=new int[19]; for(int i=0;i<a.length;i++)a[i]=b.readInt(); boolean bonus=b.readBoolean(); String key=b.readUtf(128); boolean fav=b.readBoolean(),man=b.readBoolean(),own=b.readBoolean(); int admins=b.readInt(),allowed=b.readInt(); boolean can=b.readBoolean(); String blocked=b.readUtf(128); return new Entry(id,t,n,v,fs,d,x,y,z,r,tier,dist,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],a[9],a[10],a[11],a[12],a[13],a[14],a[15],a[16],a[17],a[18],bonus,key,fav,man,own,admins,allowed,can,blocked); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
