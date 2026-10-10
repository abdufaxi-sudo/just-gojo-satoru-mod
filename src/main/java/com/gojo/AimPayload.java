package com.gojo;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: Blue(0)/Red(1)/Purple(2) nisan rejimi (0 camera, 1 fixed). Server yoxlayir. */
public record AimPayload(int slot, int mode) implements CustomPayload {
    public static final CustomPayload.Id<AimPayload> ID = new CustomPayload.Id<>(Identifier.of("gojo", "aim"));
    public static final PacketCodec<RegistryByteBuf, AimPayload> CODEC =
            PacketCodec.of((v, buf) -> { buf.writeVarInt(v.slot()); buf.writeVarInt(v.mode()); },
                    buf -> new AimPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
