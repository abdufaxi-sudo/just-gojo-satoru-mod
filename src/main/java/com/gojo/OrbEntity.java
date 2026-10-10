package com.gojo;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Blue / Red / Purple / Domain. Bir entity, 4 novu (KIND). Butun movement/damage SERVERDE. */
public class OrbEntity extends Entity {
    public static final int BLUE = 0, RED = 1, PURPLE = 2, DOMAIN = 3;
    private static final TrackedData<Integer> KIND = DataTracker.registerData(OrbEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final int PURPLE_CHARGE = 20;
    private static final float[][] COL = {{0.2f, 0.5f, 1f}, {1f, 0.1f, 0.1f}, {0.65f, 0.15f, 1f}, {0.5f, 0.6f, 1f}};

    private UUID ownerId;
    private Vec3d dir = Vec3d.ZERO;
    /** true = Fixed-direction, false = Camera-follow. */
    private boolean fixed;
    /** Fixed Blue blokla toqqusanda dayanir (impact point). */
    private boolean stuck;
    /** Purple: artiq vurduğu canlilar (her canliya 1 defe). */
    private final Set<Integer> hitIds = new HashSet<>();

    public OrbEntity(EntityType<? extends OrbEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public void setup(int kind, UUID owner, Vec3d center, Vec3d direction, boolean fixedMode) {
        dataTracker.set(KIND, kind);
        ownerId = owner;
        fixed = fixedMode;
        dir = direction.normalize();
        setPosition(center.x, center.y - 1.0, center.z);
        if (kind == RED) setVelocity(dir.multiply(GojoConfig.C.redSpeed));
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getKind() {
        return Math.max(0, Math.min(3, dataTracker.get(KIND)));
    }

    private static int life(int k) {
        GojoConfig.Data c = GojoConfig.C;
        return switch (k) {
            case BLUE -> c.blueDuration;
            case RED -> c.redDuration;
            case PURPLE -> PURPLE_CHARGE + c.purpleDuration;
            default -> c.domainDuration;
        };
    }

    /** Render olcusu (radius, blok). */
    public float scale(float td) {
        float a = age + td;
        int k = getKind();
        float s;
        if (k == BLUE) s = 1.0f * Math.min(1f, a / 6f) * (1f + 0.06f * (float) Math.sin(a * 0.8f));
        else if (k == RED) s = 0.4f * Math.min(1f, a / 3f);
        else if (k == PURPLE) s = 2.6f * Math.min(1f, a / PURPLE_CHARGE);
        else s = GojoConfig.C.domainRadius * Math.min(1f, a / 12f);
        float left = life(k) - a;
        float fade = k == DOMAIN ? 10f : 6f;
        if (left < fade) s *= Math.max(0f, left / fade);
        return s;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(KIND, 0);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        int k = getKind();
        Vec3d c = getPos().add(0, 1, 0);

        if (getWorld().isClient) {
            clientFx(k, c);
            if (k != BLUE && getVelocity().lengthSquared() > 0) setPosition(getPos().add(getVelocity()));
            return;
        }

        ServerWorld sw = (ServerWorld) getWorld();
        LivingEntity owner = null;
        if (ownerId != null) {
            Entity oe = sw.getEntity(ownerId);
            if (oe instanceof LivingEntity l && l.isAlive()) owner = l;
        }
        // sahibi oldu / cixdi / baska dimensiyaya kecdi -> orb qalmir (kohne vəziyyət yoxdur)
        if (owner == null) {
            discard();
            return;
        }

        switch (k) {
            case BLUE -> blue(sw, c, owner);
            case RED -> { if (red(sw, c, owner)) return; }
            case DOMAIN -> domain(sw, c, owner);
            default -> { if (purple(sw, c, owner)) return; }
        }
        if (age >= life(k)) {
            burst(sw, c, k, 12);
            discard();
        }
    }

    // ---------- BLUE: cekir ----------
    private void blue(ServerWorld sw, Vec3d c0, LivingEntity owner) {
        GojoConfig.Data cfg = GojoConfig.C;
        Vec3d c = c0;
        if (fixed) {
            if (!stuck) {
                Vec3d step = dir.multiply(cfg.blueSpeed);
                BlockHitResult br = sw.raycast(new RaycastContext(c, c.add(step), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
                if (br.getType() == HitResult.Type.MISS) {
                    c = c.add(step);
                } else {
                    c = br.getPos().subtract(dir.multiply(0.6));
                    stuck = true;
                }
                setPosition(c.x, c.y - 1.0, c.z);
            }
        } else { // camera-follow: yumsaq, hereket limitli (titreme yoxdur)
            Vec3d d = aimPoint(sw, owner, 25).subtract(c).multiply(0.35);
            double len = d.length();
            if (len > 1.2) d = d.multiply(1.2 / len);
            c = c.add(d);
            setPosition(c.x, c.y - 1.0, c.z);
        }

        double R = cfg.blueRadius;
        List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(R),
                e -> e.isAlive() && e != owner && !e.isSpectator());
        for (LivingEntity e : list) {
            if (e instanceof PlayerEntity pl && pl.isCreative()) continue;
            Vec3d to = c.subtract(e.getPos().add(0, e.getHeight() / 2, 0));
            double d = to.length();
            if (d > R) continue;
            if (d > 0.01) {
                double speed = Math.min(1.0, (0.25 + 3.0 / Math.max(d, 1.0)) * cfg.bluePull); // sürət limiti
                e.setVelocity(to.normalize().multiply(speed));
                e.velocityModified = true;
                e.fallDistance = 0;
            }
            if (d < 2.5 && age % 10 == 0 && cfg.blueDamage > 0) {
                e.damage(sw.getDamageSources().indirectMagic(this, owner), cfg.blueDamage);
            }
        }
        if (cfg.destroyBlocks && age % 2 == 0) erode(sw, c, 2.2, 20);
        if (age % 10 == 0) sound(sw, c, SoundEvents.BLOCK_BEACON_AMBIENT, 1.5f);
    }

    // ---------- RED: itelәyir ----------
    private boolean red(ServerWorld sw, Vec3d c, LivingEntity owner) {
        GojoConfig.Data cfg = GojoConfig.C;
        Vec3d v = getVelocity();
        if (!fixed && age > 2) { // camera-follow: nisan nöqtəsinə doğru burulur
            Vec3d want = aimPoint(sw, owner, 40).subtract(c);
            if (want.lengthSquared() > 4 && v.lengthSquared() > 0) {
                v = v.normalize().multiply(0.65).add(want.normalize().multiply(0.35)).normalize().multiply(v.length());
                setVelocity(v);
                velocityModified = true;
            }
        }
        boolean hit = age >= life(RED) - 1 || !sw.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(1.0),
                e -> e.isAlive() && e != owner && !e.isSpectator()).isEmpty();
        Vec3d at = c;
        if (!hit) {
            BlockHitResult br = sw.raycast(new RaycastContext(c, c.add(v), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
            if (br.getType() != HitResult.Type.MISS) { hit = true; at = br.getPos(); }
        }
        if (!hit) {
            setPosition(getPos().add(v));
            return false;
        }
        // PARTLAYIS — bir defe, her canliya bir defe zerbe
        double R = cfg.redRadius;
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(at, at).expand(R), x -> x.isAlive() && x != owner && !x.isSpectator())) {
            Vec3d off = e.getPos().add(0, e.getHeight() / 2, 0).subtract(at);
            double d = off.length();
            if (d > R) continue;
            if (cfg.redInstakill && d < 4) { kill(sw, e); continue; }
            double f = Math.max(0, 1 - d / R);
            if (cfg.redDamage > 0) e.damage(sw.getDamageSources().indirectMagic(this, owner), (float) (cfg.redDamage * (0.5 + 0.5 * f)));
            Vec3d push = (d > 0.01 ? off.normalize() : new Vec3d(0, 1, 0)).multiply(cfg.redKnockback * f + 0.6).add(0, 0.4, 0);
            if (push.length() > 3.0) push = push.normalize().multiply(3.0);
            e.setVelocity(push);
            e.velocityModified = true;
        }
        if (cfg.destroyBlocks) erode(sw, at, 4.5, 350);
        sw.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        Abilities.dust(sw, at, 1f, 0.1f, 0.1f, 30, 2.0);
        sound(sw, at, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.8f);
        discard();
        return true;
    }

    // ---------- PURPLE ----------
    private boolean purple(ServerWorld sw, Vec3d c, LivingEntity owner) {
        GojoConfig.Data cfg = GojoConfig.C;
        if (age < PURPLE_CHARGE) { // sarj: sahibin qarsisinda (iki rejimde eyni)
            dir = owner.getRotationVec(1f);
            Vec3d p = owner.getEyePos().add(dir.multiply(3.5));
            setPosition(p.x, p.y - 1.0, p.z);
            if (age % 5 == 0) sound(sw, p, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.5f + age * 0.05f);
            return false;
        }
        if (age == PURPLE_CHARGE) { // atis: bu andaki baxis istiqameti
            setVelocity(dir.multiply(cfg.purpleSpeed));
            velocityModified = true;
            sound(sw, c, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1f);
        }
        Vec3d vel = getVelocity();
        if (!fixed && vel.lengthSquared() > 0) { // camera-follow
            Vec3d want = aimPoint(sw, owner, 40).subtract(c);
            if (want.lengthSquared() > 9) {
                setVelocity(vel.normalize().multiply(0.88).add(want.normalize().multiply(0.12)).normalize().multiply(vel.length()));
                velocityModified = true;
            }
        }
        vel = getVelocity();
        if (vel.lengthSquared() < 1.0E-6) { discard(); return true; }

        if (!cfg.destroyBlocks) { // dunyani qirmir: blokla toqqusanda partlayib yox olur
            BlockHitResult br = sw.raycast(new RaycastContext(c, c.add(vel.normalize().multiply(vel.length() + 2.0)),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
            if (br.getType() != HitResult.Type.MISS) {
                Vec3d at = br.getPos();
                sw.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                Abilities.dust(sw, at, 0.65f, 0.15f, 1f, 40, 1.5);
                sound(sw, at, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.8f);
                discard();
                return true;
            }
        }
        setPosition(getPos().add(vel));
        Vec3d n = getPos().add(0, 1, 0);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(n, n).expand(cfg.purpleRadius),
                x -> x.isAlive() && x != owner && !x.isSpectator())) {
            if (!hitIds.add(e.getId())) continue; // eyni canliya tekrar zerbe yoxdur
            if (cfg.purpleInstakill) { kill(sw, e); continue; }
            if (cfg.purpleDamage > 0) e.damage(sw.getDamageSources().indirectMagic(this, owner), cfg.purpleDamage);
            Vec3d push = vel.normalize().multiply(cfg.purpleKnockback).add(0, 0.3, 0);
            if (push.length() > 3.0) push = push.normalize().multiply(3.0);
            e.setVelocity(push);
            e.velocityModified = true;
            Abilities.dust(sw, e.getPos().add(0, e.getHeight() / 2, 0), 0.65f, 0.15f, 1f, 12, 0.6);
        }
        if (cfg.destroyBlocks) erode(sw, n, 3.5, cfg.blocksPerTick);
        if (age % 3 == 0 && cfg.particleScale > 0f) sw.spawnParticles(ParticleTypes.END_ROD, n.x, n.y, n.z, 3, 1.5, 1.5, 1.5, 0.02);
        return false;
    }

    // ---------- DOMAIN: Unlimited Void ----------
    private static boolean eligible(LivingEntity e, LivingEntity owner) {
        if (e instanceof PlayerEntity pl) return GojoConfig.C.domainAffectPlayers && !pl.isCreative();
        if (e instanceof Monster) return true;
        return e instanceof MobEntity m && m.getTarget() == owner;
    }

    private void domain(ServerWorld sw, Vec3d c, LivingEntity owner) {
        final double R = GojoConfig.C.domainRadius;
        final double R2 = R * R;
        if (age == 1) sound(sw, c, SoundEvents.BLOCK_END_PORTAL_SPAWN, 0.8f);
        if (age % 30 == 0) sound(sw, c, SoundEvents.AMBIENT_CAVE.value(), 0.5f);

        if (owner instanceof ServerPlayerEntity op && op.squaredDistanceTo(c) <= R2) {
            PlayerState s = PlayerState.get(op);
            boolean was = s.domainCaster > 0;
            s.domainCaster = 6;
            if (!was) GojoMod.sync(op);
        }
        if (age % 10 == 0 && GojoConfig.C.particleScale > 0f) { // yungul atmosfer
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = R * (0.3 + 0.7 * random.nextDouble());
                sw.spawnParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + (random.nextDouble() - 0.3) * 4, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0.01);
            }
        }
        if (age < 6) return; // dome acilarken qisa gecikme

        // Heç bir daimi vəziyyət yoxdur: sayğaclar (6 tick) və slowness (12 tick) özü bitir.
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(R),
                x -> x.isAlive() && x != owner && !x.isSpectator() && x.squaredDistanceTo(c) <= R2 && eligible(x, owner))) {
            if (e instanceof ServerPlayerEntity sp) {
                PlayerState s = PlayerState.get(sp);
                if (s.simple > 0) continue; // Simple Domain anti-domain muhafizesi
                boolean was = s.domainVictim > 0;
                s.domainVictim = 6;
                if (!was) GojoMod.sync(sp);
                if (age % 2 == 0) {
                    sp.setVelocity(0, Math.min(0, sp.getVelocity().y), 0);
                    sp.velocityModified = true;
                }
            } else {
                Vec3d v = e.getVelocity();
                e.setVelocity(0, Math.min(0, v.y), 0);
            }
            if (age % 5 == 0) e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 10, false, false, false));
        }
    }

    // ---------- helpers ----------
    /** Sahibinin nisan nöqtəsi (blok varsa, onun 1.5 blok qabağı). */
    private static Vec3d aimPoint(ServerWorld sw, LivingEntity owner, double range) {
        Vec3d eye = owner.getEyePos();
        Vec3d look = owner.getRotationVec(1f);
        HitResult h = sw.raycast(new RaycastContext(eye, eye.add(look.multiply(range)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner));
        Vec3d p = h.getPos();
        if (h.getType() != HitResult.Type.MISS) p = p.subtract(look.multiply(1.5));
        return p;
    }

    private static void kill(ServerWorld sw, LivingEntity e) {
        if (e instanceof PlayerEntity p && p.isCreative()) return;
        e.damage(sw.getDamageSources().genericKill(), Float.MAX_VALUE);
    }

    /** Yalniz config destroyBlocks=true olanda cagirilir. */
    private static void erode(ServerWorld sw, Vec3d c, double r, int cap) {
        if (!GojoConfig.C.destroyBlocks) return;
        BlockPos cp = BlockPos.ofFloored(c);
        int R = (int) Math.ceil(r);
        double r2 = r * r;
        int broken = 0;
        for (int dx = -R; dx <= R; dx++)
            for (int dy = -R; dy <= R; dy++)
                for (int dz = -R; dz <= R; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    BlockPos bp = cp.add(dx, dy, dz);
                    BlockState st = sw.getBlockState(bp);
                    if (st.isAir() || st.getHardness(sw, bp) < 0) continue;
                    sw.setBlockState(bp, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    if (++broken >= cap) return;
                }
    }

    private static void burst(ServerWorld sw, Vec3d c, int k, int n) {
        float[] col = COL[k];
        Abilities.dust(sw, c, col[0], col[1], col[2], n, 1.0);
    }

    private static void sound(ServerWorld sw, Vec3d at, SoundEvent ev, float pitch) {
        sw.playSound(null, at.x, at.y, at.z, ev, SoundCategory.PLAYERS, 1f, pitch);
    }

    private void clientFx(int k, Vec3d c) {
        float scale = GojoConfig.C.particleScale;
        if (k == DOMAIN || scale <= 0f) return;
        int every = Math.max(1, Math.round(2f / scale));
        if (age % every != 0) return;
        float[] col = COL[k];
        DustParticleEffect fx = new DustParticleEffect(new Vector3f(col[0], col[1], col[2]), 1.2f);
        double r = k == PURPLE ? 2.2 : k == RED ? 0.3 : 0.9;
        getWorld().addParticle(fx,
                c.x + (random.nextDouble() - 0.5) * 2 * r, c.y + (random.nextDouble() - 0.5) * 2 * r,
                c.z + (random.nextDouble() - 0.5) * 2 * r, 0, 0, 0);
        if (k != BLUE && getVelocity().lengthSquared() > 0.01) { // iz (trail)
            Vec3d t = c.subtract(getVelocity().multiply(1.5));
            getWorld().addParticle(fx, t.x, t.y, t.z, 0, 0, 0);
        }
    }
}
