package com.gojo;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AbilityPayload(int id) implements CustomPayload {
    public static final CustomPayload.Id<AbilityPayload> ID = new CustomPayload.Id<>(Identifier.of("gojo", "ability"));
    public static final PacketCodec<RegistryByteBuf, AbilityPayload> CODEC =
            PacketCodec.of((v, buf) -> buf.writeVarInt(v.id()), buf -> new AbilityPayload(buf.readVarInt()));

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
