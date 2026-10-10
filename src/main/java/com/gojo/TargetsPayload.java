package com.gojo;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: Six Eyes hedefleri. Yalniz server-in icaze verdiyi (gorunen) canlilar gonderilir. */
public record TargetsPayload(List<Entry> list) implements CustomPayload {
    public record Entry(String name, float x, float y, float z) {}

    public static final int MAX = 8;
    public static final CustomPayload.Id<TargetsPayload> ID = new CustomPayload.Id<>(Identifier.of("gojo", "targets"));
    public static final PacketCodec<RegistryByteBuf, TargetsPayload> CODEC = PacketCodec.of(TargetsPayload::write, TargetsPayload::read);

    private void write(RegistryByteBuf buf) {
        int n = Math.min(MAX, list.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = list.get(i);
            buf.writeString(e.name(), 48);
            buf.writeFloat(e.x());
            buf.writeFloat(e.y());
            buf.writeFloat(e.z());
        }
    }

    private static TargetsPayload read(RegistryByteBuf buf) {
        int n = Math.min(MAX, buf.readVarInt());
        List<Entry> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new Entry(buf.readString(48), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        return new TargetsPayload(l);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
