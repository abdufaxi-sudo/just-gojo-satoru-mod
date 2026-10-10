package com.gojo.client;

import com.gojo.Abilities;
import com.gojo.StatePayload;
import com.gojo.TargetsPayload;

import java.util.ArrayList;
import java.util.List;

public class ClientState {
    public static int energy = (int) Abilities.MAX_ENERGY;
    public static int flags = 0;
    /** bit0 Blue, bit1 Red, bit2 Purple: 1 = Fixed, 0 = Camera. */
    public static int aim = 0;
    public static boolean victim, caster;
    public static float domainAlpha = 0f;
    public static final int[] cd = new int[Abilities.COUNT];
    public static final List<TargetsPayload.Entry> targets = new ArrayList<>();

    public static void update(StatePayload p) {
        energy = p.energy();
        flags = p.flags();
        aim = p.aim();
        victim = (flags & 8) != 0;
        caster = (flags & 16) != 0;
        System.arraycopy(p.cd(), 0, cd, 0, Abilities.COUNT);
    }

    public static void setTargets(List<TargetsPayload.Entry> l) {
        targets.clear();
        targets.addAll(l);
    }

    public static boolean isFixed(int slot) {
        return ((aim >> slot) & 1) == 1;
    }

    public static boolean active(int ability) {
        return (ability == 3 && (flags & 1) != 0) || (ability == 9 && (flags & 2) != 0) || (ability == 5 && (flags & 4) != 0);
    }
}
