package com.gojo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GojoMod implements ModInitializer {
    public static final EntityType<OrbEntity> ORB = Registry.register(Registries.ENTITY_TYPE, Identifier.of("gojo", "orb"),
            EntityType.Builder.<OrbEntity>create(OrbEntity::new, SpawnGroup.MISC)
                    .dimensions(2f, 2f).maxTrackingRange(10).trackingTickInterval(1)
                    .disableSaving().makeFireImmune().build("orb"));

    @Override
    public void onInitialize() {
        GojoConfig.load();

        PayloadTypeRegistry.playC2S().register(AbilityPayload.ID, AbilityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AimPayload.ID, AimPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TargetsPayload.ID, TargetsPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.ID,
                (payload, ctx) -> Abilities.use(ctx.player(), payload.id()));

        // Nisan rejimi: server deyeri yoxlayir (yalniz 3 slot, 2 rejim)
        ServerPlayNetworking.registerGlobalReceiver(AimPayload.ID, (payload, ctx) -> {
            int slot = payload.slot(), mode = payload.mode();
            if (slot < 0 || slot > 2 || mode < 0 || mode > 1) return;
            PlayerState.get(ctx.player()).aim[slot] = mode;
            sync(ctx.player());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int t = server.getTicks();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) tick(p, t);
        });

        // Temizlik: disconnect / server dayanmasi / olum-respawn / dunya deyisimi
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PlayerState.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PlayerState.clear());
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            PlayerState.get(newPlayer).resetTransient();
            sync(newPlayer);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            PlayerState s = PlayerState.get(player);
            s.bf = 0;
            s.domainVictim = 0;
            s.domainCaster = 0;
            s.domainOrb = null;
            sync(player);
        });

        // INFINITY: yalniz uygun zerbeleri bloklayir (void/kill/aclıq vs. keçir)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity sp) || !sp.isAlive()) return true;
            PlayerState s = PlayerState.peek(sp.getUuid());
            if (s == null || !s.infinity) return true;
            if (!infinityBlocks(source)) return true;
            float cost = GojoConfig.C.infinityHitCost;
            if (s.energy < cost) { // enerji yoxdur -> Infinity sönür, zerbe kecir
                s.infinity = false;
                Abilities.msg(sp, "Infinity: OFF (no energy)");
                sync(sp);
                return true;
            }
            s.energy -= cost;
            return false;
        });

        // BLACK FLASH: yalniz canli varliga ilk ugurlu yaxin vurus, bir defe
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp) || !(entity instanceof LivingEntity le)) return ActionResult.PASS;
            if (le == sp || !le.isAlive() || le.isSpectator() || le instanceof ArmorStandEntity || !sp.isAlive() || sp.isSpectator()) return ActionResult.PASS;
            PlayerState s = PlayerState.get(sp);
            if (s.bf <= 0) return ActionResult.PASS;
            s.bf = 0; // ILK ONCE istehlak et -> ikiqat tetiklenme mumkun deyil
            blackFlashHit(sp, le);
            sync(sp);
            return ActionResult.PASS;
        });
    }

    private static boolean infinityBlocks(DamageSource src) {
        if (src.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false; // void, /kill
        if (src.getAttacker() != null || src.getSource() != null
                || src.isIn(DamageTypeTags.IS_PROJECTILE) || src.isIn(DamageTypeTags.IS_EXPLOSION)) return true;
        return GojoConfig.C.infinityBlockEnvironmental
                && (src.isIn(DamageTypeTags.IS_FALL) || src.isIn(DamageTypeTags.IS_FIRE) || src.isIn(DamageTypeTags.IS_FREEZING));
    }

    private static void blackFlashHit(ServerPlayerEntity sp, LivingEntity le) {
        GojoConfig.Data cfg = GojoConfig.C;
        ServerWorld sw = sp.getServerWorld();
        if (cfg.blackFlashDamage > 0) le.damage(sw.getDamageSources().playerAttack(sp), cfg.blackFlashDamage);
        Vec3d kb = sp.getRotationVec(1f).multiply(cfg.blackFlashKnockback);
        le.setVelocity(kb.x, 0.35, kb.z);
        le.velocityModified = true;
        Vec3d at = le.getPos().add(0, le.getHeight() / 2, 0);
        Abilities.dust(sw, at, 0.05f, 0.05f, 0.05f, 20, 0.5);
        Abilities.dust(sw, at, 1f, 0.05f, 0.1f, 20, 0.5);
        sw.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        sw.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.8f, 1.5f);
        Abilities.msg(sp, "BLACK FLASH!");
    }

    private static void tick(ServerPlayerEntity p, int ticks) {
        PlayerState s = PlayerState.get(p);
        for (int i = 0; i < s.cd.length; i++) if (s.cd[i] > 0) s.cd[i]--;
        if (!p.isAlive()) { // olende hec bir yuk/toggle qalmir
            if (s.bf != 0 || s.infinity) { s.resetTransient(); sync(p); }
            return;
        }
        if (s.simple > 0) s.simple--;
        if (s.domainVictim > 0 && --s.domainVictim == 0) sync(p);
        if (s.domainCaster > 0 && --s.domainCaster == 0) sync(p);

        s.energy = Math.min(Abilities.MAX_ENERGY, s.energy + (s.sixEyes ? 0.6f : 0.2f));

        if (s.sixEyes && ticks % 20 == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 1, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 0, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 0, true, false, false));
        }
        if (s.sixEyes) {
            if (ticks % GojoConfig.C.sixEyesInterval == 0) scanTargets(p, s);
        } else if (s.targetsSent) {
            s.targetsSent = false;
            ServerPlayNetworking.send(p, new TargetsPayload(new ArrayList<>()));
        }

        // Black Flash: aktiv eldə (əsas əl) qara-qırmızı enerji — taymer YOXDUR, vurana/legv edene qeder qalir
        if (s.bf > 0 && ticks % 2 == 0) {
            ServerWorld hw = p.getServerWorld();
            Vec3d look = p.getRotationVec(1f);
            Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
            double side = p.getMainArm() == Arm.RIGHT ? 0.45 : -0.45;
            Vec3d hand = p.getEyePos().add(right.multiply(side)).add(look.multiply(0.6)).add(0, -0.35, 0);
            Abilities.dust(hw, hand, 0.03f, 0.03f, 0.03f, 2, 0.08);
            Abilities.dust(hw, hand, 1f, 0.05f, 0.1f, 2, 0.1);
            if (ticks % 6 == 0 && GojoConfig.C.particleScale >= 0.5f) {
                hw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 1, 0.15, 0.15, 0.15, 0.05);
            }
        }

        if (s.infinity) {
            s.energy -= GojoConfig.C.infinityDrain;
            if (s.energy <= 1f) {
                s.infinity = false;
                p.sendMessage(Text.literal("Infinity: OFF (no energy)"), true);
                sync(p);
            } else if (ticks % 2 == 0) {
                ServerWorld w = p.getServerWorld();
                Box box = p.getBoundingBox().expand(3.5);
                List<Entity> list = w.getOtherEntities(p, box, e -> e.isAlive() && (e instanceof LivingEntity || e instanceof ProjectileEntity));
                for (Entity e : list) {
                    Vec3d away = e.getPos().subtract(p.getPos()).multiply(1, 0, 1).normalize();
                    if (e instanceof ProjectileEntity) {
                        e.setVelocity(away.multiply(0.4).add(0, 0.05, 0));
                    } else {
                        e.addVelocity(away.x * 0.35, 0, away.z * 0.35);
                    }
                    e.velocityModified = true;
                }
            }
        }

        if (ticks % 4 == 0) sync(p);
    }

    /** Six Eyes: server yalniz GORUNEN (line-of-sight) canlilari gonderir; config ile divar arxasi acila biler. */
    private static void scanTargets(ServerPlayerEntity p, PlayerState s) {
        ServerWorld w = p.getServerWorld();
        final double r = GojoConfig.C.sixEyesRadius;
        final boolean walls = GojoConfig.C.sixEyesThroughWalls;
        List<LivingEntity> list = w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r),
                e -> e != p && e.isAlive() && !e.isSpectator() && !e.isInvisible()
                        && e.squaredDistanceTo(p) <= r * r && (walls || p.canSee(e)));
        list.sort(Comparator.comparingDouble((LivingEntity e) -> e.squaredDistanceTo(p)));
        List<TargetsPayload.Entry> out = new ArrayList<>();
        for (int i = 0; i < list.size() && i < TargetsPayload.MAX; i++) {
            LivingEntity e = list.get(i);
            String name = e.getName().getString();
            if (name.length() > 32) name = name.substring(0, 32);
            out.add(new TargetsPayload.Entry(name, (float) e.getX(), (float) e.getY(), (float) e.getZ()));
        }
        if (out.isEmpty() && !s.targetsSent) return;
        s.targetsSent = !out.isEmpty();
        ServerPlayNetworking.send(p, new TargetsPayload(out));
    }

    public static void sync(ServerPlayerEntity p) {
        PlayerState s = PlayerState.get(p);
        int flags = (s.infinity ? 1 : 0) | (s.sixEyes ? 2 : 0) | (s.bf > 0 ? 4 : 0)
                | (s.domainVictim > 0 ? 8 : 0) | (s.domainCaster > 0 ? 16 : 0);
        int aim = s.aim[0] | (s.aim[1] << 1) | (s.aim[2] << 2);
        ServerPlayNetworking.send(p, new StatePayload((int) s.energy, flags, aim, s.cd.clone()));
    }
}
