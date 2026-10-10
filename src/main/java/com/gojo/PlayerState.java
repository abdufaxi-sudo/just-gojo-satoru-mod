package com.gojo;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerState {
    private static final Map<UUID, PlayerState> MAP = new HashMap<>();

    public float energy = Abilities.MAX_ENERGY;
    public final int[] cd = new int[Abilities.COUNT];
    public boolean infinity, sixEyes;
    /** Black Flash: 0 = yoxdur, 1 = yuklu (vurusa qeder SONSUZ qalir, taymer yoxdur). */
    public int bf;
    public int domainVictim, domainCaster, simple; // domain vəziyyəti (tick sayğacları)
    /** Blue/Red/Purple nisan rejimi: 0 = camera-follow, 1 = fixed. */
    public final int[] aim = new int[3];
    public java.util.UUID domainOrb;
    public boolean targetsSent;

    public static PlayerState get(ServerPlayerEntity p) {
        return MAP.computeIfAbsent(p.getUuid(), k -> new PlayerState());
    }

    public static PlayerState peek(UUID id) {
        return MAP.get(id);
    }

    public static void remove(UUID id) {
        MAP.remove(id);
    }

    public static void clear() {
        MAP.clear();
    }

    /** Olum / respawn / dunya deyisimi: kohne yuk qalmasin. */
    public void resetTransient() {
        bf = 0;
        infinity = false;
        domainVictim = 0;
        domainCaster = 0;
        simple = 0;
        domainOrb = null;
    }
}
