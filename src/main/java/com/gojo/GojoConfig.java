package com.gojo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/gojo.json — her setir yoxlanilir (yanlis/NaN deyer varsa default-a qayidir). */
public class GojoConfig {
    private static final Logger LOG = LoggerFactory.getLogger("gojo");

    public static class Data {
        // Umumi
        public float particleScale = 1.0f;      // 0 = hec partikl, 2 = max
        public boolean destroyBlocks = true;    // Purple/Blue/Red blok qirsin? (v4 kimi: beli)
        public int blocksPerTick = 40;

        // Blue
        public float blueRadius = 10f;
        public float bluePull = 1.0f;
        public float blueDamage = 4f;           // 10 tickde bir (merkeze yaxin olanlara)
        public int blueDuration = 100;
        public float blueSpeed = 0.6f;          // "Fixed" rejimde

        // Red
        public float redDamage = 12f;
        public float redKnockback = 1.5f;
        public float redRadius = 6f;
        public boolean redInstakill = true;     // partlayisin merkezinde (4 blok) oldurur (v4 kimi)
        public float redSpeed = 1.6f;
        public int redDuration = 60;

        // Purple
        public float purpleDamage = 40f;
        public float purpleKnockback = 1.5f;
        public float purpleRadius = 4.5f;
        public boolean purpleInstakill = true;  // Purple yolundakı hər şeyi yox edir (v4 kimi)
        public float purpleSpeed = 0.9f;
        public int purpleDuration = 70;         // sarjdan sonraki ucus muddeti

        // Black Flash
        public float blackFlashDamage = 22f;
        public float blackFlashKnockback = 1.4f;

        // Infinity
        public float infinityDrain = 0.3f;      // tick basina enerji (0 = pulsuz)
        public float infinityHitCost = 5f;      // bloklanan her zerbe ucun enerji
        public boolean infinityBlockEnvironmental = false; // dusme/od/donma da bloklansin?

        // Six Eyes
        public float sixEyesRadius = 24f;
        public int sixEyesInterval = 10;        // tick
        public boolean sixEyesThroughWalls = false;

        // Teleport
        public float teleportRange = 30f;
        public int teleportCooldown = 40;

        // Domain
        public float domainRadius = 14f;
        public int domainDuration = 120;
        public int domainCooldown = 1200;
        public boolean domainAffectPlayers = true;

        void sanitize() {
            particleScale = f(particleScale, 0f, 2f, 1f);
            blocksPerTick = i(blocksPerTick, 1, 200);
            blueRadius = f(blueRadius, 2f, 24f, 10f);
            bluePull = f(bluePull, 0f, 3f, 1f);
            blueDamage = f(blueDamage, 0f, 100f, 4f);
            blueDuration = i(blueDuration, 20, 600);
            blueSpeed = f(blueSpeed, 0.1f, 2f, 0.6f);
            redDamage = f(redDamage, 0f, 200f, 12f);
            redKnockback = f(redKnockback, 0f, 4f, 1.5f);
            redRadius = f(redRadius, 1f, 16f, 6f);
            redSpeed = f(redSpeed, 0.2f, 3f, 1.6f);
            redDuration = i(redDuration, 10, 300);
            purpleDamage = f(purpleDamage, 0f, 500f, 40f);
            purpleKnockback = f(purpleKnockback, 0f, 4f, 1.5f);
            purpleRadius = f(purpleRadius, 1f, 8f, 4.5f);
            purpleSpeed = f(purpleSpeed, 0.2f, 2f, 0.9f);
            purpleDuration = i(purpleDuration, 10, 300);
            blackFlashDamage = f(blackFlashDamage, 0f, 200f, 22f);
            blackFlashKnockback = f(blackFlashKnockback, 0f, 4f, 1.4f);
            infinityDrain = f(infinityDrain, 0f, 5f, 0.3f);
            infinityHitCost = f(infinityHitCost, 0f, 100f, 5f);
            sixEyesRadius = f(sixEyesRadius, 4f, 64f, 24f);
            sixEyesInterval = i(sixEyesInterval, 2, 100);
            teleportRange = f(teleportRange, 3f, 64f, 30f);
            teleportCooldown = i(teleportCooldown, 5, 6000);
            domainRadius = f(domainRadius, 4f, 32f, 14f);
            domainDuration = i(domainDuration, 20, 1200);
            domainCooldown = i(domainCooldown, 20, 72000);
        }
    }

    public static Data C = new Data();

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("gojo.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Data d = null;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                d = gson.fromJson(r, Data.class);
            } catch (Exception e) {
                LOG.warn("gojo.json oxunmadi, default istifade olunur: {}", e.toString());
            }
        }
        if (d == null) d = new Data();
        d.sanitize();
        C = d;
        try (Writer w = Files.newBufferedWriter(file)) {
            gson.toJson(d, w);
        } catch (Exception e) {
            LOG.warn("gojo.json yazila bilmedi: {}", e.toString());
        }
        Abilities.COOLDOWN[6] = d.teleportCooldown;
        Abilities.COOLDOWN[7] = d.domainCooldown;
    }

    private static float f(float v, float lo, float hi, float def) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return def;
        return Math.max(lo, Math.min(hi, v));
    }

    private static int i(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
