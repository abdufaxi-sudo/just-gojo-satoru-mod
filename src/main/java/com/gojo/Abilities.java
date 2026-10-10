package com.gojo;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

public class Abilities {
    public static final int COUNT = 10;
    public static final float MAX_ENERGY = 200f;

    public static final String[] IDS = {"blue", "red", "purple", "infinity", "reverse", "blackflash", "teleport", "domain", "simple", "sixeyes"};
    public static final String[] NAMES = {"Blue (Ao)", "Red (Aka)", "Purple (Murasaki)", "Infinity", "Reverse Technique",
            "Black Flash", "Teleport", "Domain: Unlimited Void", "Simple Domain", "Six Eyes"};
    public static final String[] DESC = {
            "Pulls nearby living things toward the orb. Aim mode selectable below.",
            "Repulsion orb: knockback + damage on impact. Aim mode selectable below.",
            "Large purple projectile. Hits each target once. Aim mode selectable below.",
            "Toggle. Blocks eligible attacks, pushes enemies and projectiles away. Costs energy.",
            "Heals you.",
            "Charges your hand until you hit a living target. Press again to cancel.",
            "Blink in your look direction to a safe spot.",
            "Unlimited Void: briefly freezes hostile creatures in the radius.",
            "Protects you from Domain Expansion for a short time.",
            "Toggle. Faster regen, buffs, and a list of nearby targets."};
    public static final int[] COST = {30, 30, 80, 0, 30, 10, 15, 100, 25, 0};
    /** Teleport (6) ve Domain (7) config-den yazilir. */
    public static final int[] COOLDOWN = {100, 80, 300, 10, 100, 40, 40, 1200, 400, 10};

    public static void use(ServerPlayerEntity p, int id) {
        if (id < 0 || id >= COUNT || p.isSpectator() || !p.isAlive()) return;
        PlayerState s = PlayerState.get(p);
        if (id == 5 && s.bf > 0) { // yeniden basanda legv et
            s.bf = 0; s.cd[5] = 10;
            msg(p, "Black Flash cancelled");
            GojoMod.sync(p);
            return;
        }
        if (s.cd[id] > 0) return; // basili saxlama / spam: cooldown serverdedir
        if (s.domainVictim > 0) { msg(p, "Frozen in the Unlimited Void"); return; }

        if (id == 3) {
            if (s.infinity) s.infinity = false;
            else if (s.energy >= 10) s.infinity = true;
            else { msg(p, "Not enough cursed energy"); return; }
            s.cd[id] = COOLDOWN[id];
            msg(p, "Infinity: " + (s.infinity ? "ON" : "OFF"));
            GojoMod.sync(p);
            return;
        }
        if (id == 9) {
            s.sixEyes = !s.sixEyes;
            s.cd[id] = COOLDOWN[id];
            msg(p, "Six Eyes: " + (s.sixEyes ? "ON" : "OFF"));
            GojoMod.sync(p);
            return;
        }
        if (s.energy < COST[id]) { msg(p, "Not enough cursed energy"); return; }

        ServerWorld w = p.getServerWorld();
        boolean ok = true;
        switch (id) {
            case 0 -> blue(p, w, s.aim[0] == 1);
            case 1 -> red(p, w, s.aim[1] == 1);
            case 2 -> purple(p, w, s.aim[2] == 1);
            case 4 -> reverse(p, w);
            case 5 -> { s.bf = 1; msg(p, "Black Flash ready - hit someone!"); }
            case 6 -> ok = teleport(p, w);
            case 7 -> domain(p, w, s);
            case 8 -> simpleDomain(p, w);
            default -> { return; }
        }
        if (!ok) return; // yanlis teleport: enerji/cooldown xerclenmir
        s.energy -= COST[id];
        s.cd[id] = COOLDOWN[id];
        GojoMod.sync(p);
    }

    // ---------- abilities ----------

    private static void blue(ServerPlayerEntity p, ServerWorld w, boolean fixed) {
        Vec3d dir = p.getRotationVec(1f);
        Vec3d pt = ray(p, w, 25);
        if (pt.distanceTo(p.getEyePos()) < 24.9) pt = pt.subtract(dir.multiply(1.5));
        if (fixed) pt = p.getEyePos().add(dir.multiply(2.0));
        spawnOrb(w, OrbEntity.BLUE, p, pt, dir, fixed);
        sound(w, pt, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.7f);
    }

    private static void red(ServerPlayerEntity p, ServerWorld w, boolean fixed) {
        Vec3d dir = p.getRotationVec(1f);
        spawnOrb(w, OrbEntity.RED, p, p.getEyePos().add(dir.multiply(1.2)), dir, fixed);
        sound(w, p.getPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 0.7f);
    }

    private static void purple(ServerPlayerEntity p, ServerWorld w, boolean fixed) {
        Vec3d dir = p.getRotationVec(1f);
        spawnOrb(w, OrbEntity.PURPLE, p, p.getEyePos().add(dir.multiply(3.5)), dir, fixed);
    }

    private static OrbEntity spawnOrb(ServerWorld w, int kind, ServerPlayerEntity p, Vec3d center, Vec3d dir, boolean fixed) {
        OrbEntity o = new OrbEntity(GojoMod.ORB, w);
        o.setup(kind, p.getUuid(), center, dir, fixed);
        w.spawnEntity(o);
        return o;
    }

    private static void reverse(ServerPlayerEntity p, ServerWorld w) {
        p.heal(10f);
        p.extinguish();
        w.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1, p.getZ(), 5, 0.4, 0.5, 0.4, 0);
        sound(w, p.getPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f);
    }

    /** Teleport: yalniz tehlukesiz yer tapilsa isleyir. Tapilmasa false (enerji xerclenmir). */
    private static boolean teleport(ServerPlayerEntity p, ServerWorld w) {
        double range = GojoConfig.C.teleportRange;
        Vec3d dir = p.getRotationVec(1f);
        Vec3d eye = p.getEyePos();
        double dist = ray(p, w, range).distanceTo(eye);
        double start = dist < range - 0.05 ? dist - 0.7 : range;
        double eyeH = p.getEyeHeight(p.getPose());
        for (double d = start; d >= 1.0; d -= 0.5) {
            Vec3d feet = eye.add(dir.multiply(d)).subtract(0, eyeH, 0);
            if (!isSafe(p, w, feet)) continue;
            dust(w, p.getPos().add(0, 1, 0), 0.2f, 0.5f, 1f, 15, 0.4);
            p.requestTeleport(feet.x, feet.y, feet.z);
            p.fallDistance = 0;
            dust(w, feet.add(0, 1, 0), 0.2f, 0.5f, 1f, 15, 0.4);
            w.spawnParticles(ParticleTypes.PORTAL, feet.x, feet.y + 1, feet.z, 10, 0.3, 0.5, 0.3, 0.1);
            sound(w, feet, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f);
            return true;
        }
        msg(p, "Teleport: no safe destination");
        return false;
    }

    private static boolean isHazard(BlockState st) {
        return st.isOf(Blocks.LAVA) || st.isOf(Blocks.FIRE) || st.isOf(Blocks.SOUL_FIRE) || st.isOf(Blocks.MAGMA_BLOCK)
                || st.isOf(Blocks.CACTUS) || st.isOf(Blocks.SWEET_BERRY_BUSH) || st.isOf(Blocks.WITHER_ROSE);
    }

    private static boolean isSafe(ServerPlayerEntity p, ServerWorld w, Vec3d f) {
        if (!w.getWorldBorder().contains(f.x, f.z)) return false;
        if (f.y <= w.getBottomY() + 1 || f.y >= w.getTopY() - 2) return false;
        Box box = p.getBoundingBox().offset(f.subtract(p.getPos()));
        if (!w.isSpaceEmpty(p, box)) return false;
        BlockPos feet = BlockPos.ofFloored(f);
        if (isHazard(w.getBlockState(feet)) || isHazard(w.getBlockState(feet.up()))) return false;
        if (p.getAbilities().flying) return true;
        for (int i = 1; i <= 8; i++) { // bosluga/lavaya dusmesin
            BlockPos bp = feet.down(i);
            BlockState st = w.getBlockState(bp);
            if (isHazard(st)) return false;
            if (!st.getCollisionShape(w, bp).isEmpty() || !st.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    private static void domain(ServerPlayerEntity p, ServerWorld w, PlayerState s) {
        // bir sahibin eyni anda yalniz 1 domaini ola biler (cakisma yoxdur)
        if (s.domainOrb != null) {
            Entity old = w.getEntity(s.domainOrb);
            if (old instanceof OrbEntity) old.discard();
        }
        OrbEntity o = spawnOrb(w, OrbEntity.DOMAIN, p, p.getPos().add(0, 1, 0), Vec3d.ZERO, false);
        s.domainOrb = o.getUuid();
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 120, 1));
        msg(p, "Domain Expansion: Unlimited Void");
    }

    private static void simpleDomain(ServerPlayerEntity p, ServerWorld w) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 2));
        PlayerState.get(p).simple = 200; // 10 san domain-e qarsi toxunulmaz
        w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 10, 0.6, 0.8, 0.6, 0.02);
        msg(p, "Simple Domain (anti-domain)");
    }

    // ---------- helpers ----------

    private static Vec3d ray(ServerPlayerEntity p, ServerWorld w, double range) {
        Vec3d a = p.getEyePos();
        Vec3d b = a.add(p.getRotationVec(1f).multiply(range));
        HitResult h = w.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        return h.getPos();
    }

    /** Partikl sayi config-deki particleScale ile olceklenir (0 = yoxdur). */
    public static void dust(ServerWorld w, Vec3d at, float r, float g, float b, int count, double spread) {
        int n = Math.round(count * GojoConfig.C.particleScale);
        if (n <= 0) return;
        w.spawnParticles(new DustParticleEffect(new Vector3f(r, g, b), 1.4f), at.x, at.y, at.z, n, spread, spread, spread, 0);
    }

    private static void sound(ServerWorld w, Vec3d at, SoundEvent ev, float pitch) {
        w.playSound(null, at.x, at.y, at.z, ev, SoundCategory.PLAYERS, 1f, pitch);
    }

    public static void msg(ServerPlayerEntity p, String s) {
        p.sendMessage(Text.literal(s), true);
    }
}
