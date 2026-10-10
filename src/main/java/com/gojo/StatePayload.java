package com.gojo;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StatePayload(int energy, int flags, int aim, int[] cd) implements CustomPayload {
    public static final CustomPayload.Id<StatePayload> ID = new CustomPayload.Id<>(Identifier.of("gojo", "state"));
    public static final PacketCodec<RegistryByteBuf, StatePayload> CODEC = PacketCodec.of(StatePayload::write, StatePayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(energy);
        buf.writeVarInt(flags);
        buf.writeVarInt(aim);
        for (int v : cd) buf.writeVarInt(v);
    }

    private static StatePayload read(RegistryByteBuf buf) {
        int e = buf.readVarInt();
        int f = buf.readVarInt();
        int a = buf.readVarInt();
        int[] c = new int[Abilities.COUNT];
        for (int i = 0; i < c.length; i++) c[i] = buf.readVarInt();
        return new StatePayload(e, f, a, c);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
