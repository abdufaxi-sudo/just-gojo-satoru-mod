package com.gojo.client;

import com.gojo.Abilities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import com.gojo.TargetsPayload;

/** Cox yungul HUD: yalniz fill() ve kicik text - FPS-e tesir etmir. */
public class Hud {
    private static float frac(double v) {
        return (float) (v - Math.floor(v));
    }

    /** Unlimited Void: qara ekran + hereket eden ulduzlar. Yalniz fill() - cox yungul. */
    private static void domainOverlay(DrawContext c, MinecraftClient mc) {
        float a = ClientState.domainAlpha;
        if (a < 0.02f) return;
        int w = c.getScaledWindowWidth(), h = c.getScaledWindowHeight();
        float time = (System.currentTimeMillis() % 1000000L) / 1000f;
        c.fill(0, 0, w, h, ((int) (a * 255) << 24) | 0x03000A);

        for (int i = 0; i < 70; i++) {
            float fx = frac(Math.sin(i * 12.9898) * 43758.5453);
            float fy = frac(Math.sin(i * 78.233) * 12345.6789);
            int sx = (int) ((fx * w + time * (8 + fy * 20)) % w);
            int sy = (int) (fy * h);
            int size = i % 5 == 0 ? 2 : 1;
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 3 + i);
            int al = (int) (255 * Math.min(1f, a) * (0.4f + 0.6f * tw));
            c.fill(sx, sy, sx + size, sy + size, (al << 24) | 0xCCDDFF);
        }
        for (int i = 0; i < 6; i++) {
            float fy = frac(Math.sin(i * 39.346) * 9871.31);
            int sx = (int) ((time * (90 + i * 25) + i * 97) % (w + 40)) - 40;
            c.fill(sx, (int) (fy * h), sx + 40, (int) (fy * h) + 1, ((int) (110 * a) << 24) | 0x40B0E0);
        }
        if (a > 0.6f) {
            c.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("\u7121\u91cf\u7a7a\u51e6"), w / 2, h / 2 - 12, 0xFFFFFF);
            c.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Domain Expansion: Unlimited Void"), w / 2, h / 2 + 2, 0x9FB8FF);
        }
    }

    /** Six Eyes: nazik mavi cerçive + yaxin hedeflerin siyahisi (server-in gonderdiyi, gorunen canlilar). */
    private static void sixEyes(DrawContext c, MinecraftClient mc) {
        if (!ClientState.active(9)) return;
        int w = c.getScaledWindowWidth(), h = c.getScaledWindowHeight();
        int col = 0x403A9BFF;
        c.fill(0, 0, w, 2, col);
        c.fill(0, h - 2, w, h, col);
        c.fill(0, 0, 2, h, col);
        c.fill(w - 2, 0, w, h, col);
        int y = 6;
        c.drawText(mc.textRenderer, "Six Eyes", w - 110, y, 0xFF8FB8FF, true);
        y += 11;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        for (TargetsPayload.Entry t : ClientState.targets) {
            double dx = t.x() - px, dz = t.z() - pz;
            double dist = Math.sqrt(dx * dx + (t.y() - py) * (t.y() - py) + dz * dz);
            float rel = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYaw()));
            int idx = Math.floorMod(Math.round(rel / 45f), 8);
            String arrow = ARROWS[idx];
            String name = t.name().length() > 14 ? t.name().substring(0, 14) : t.name();
            c.drawText(mc.textRenderer, arrow + " " + name + " " + (int) dist + "m", w - 110, y, 0xFFCFE0FF, true);
            y += 10;
        }
    }

    private static final String[] ARROWS = {"^", "^>", ">", "v>", "v", "<v", "<", "<^"};

    public static void render(DrawContext c, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        domainOverlay(c, mc);
        sixEyes(c, mc);
        if ((ClientState.flags & 4) != 0) { // Black Flash hazirdir
            int pulse = 160 + (int) (95 * Math.sin(System.currentTimeMillis() / 120.0));
            c.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("BLACK FLASH"),
                    c.getScaledWindowWidth() / 2, c.getScaledWindowHeight() - 62, 0xFF000000 | (pulse << 16) | 0x1020);
        }
        int x = 6, y = 6;

        c.fill(x, y, x + 102, y + 8, 0xAA000000);
        int bar = (int) (100f * ClientState.energy / Abilities.MAX_ENERGY);
        c.fill(x + 1, y + 1, x + 1 + bar, y + 7, 0xFF3A7BFF);
        c.drawText(mc.textRenderer, "CE " + ClientState.energy, x + 106, y, 0xFF8FB8FF, false);

        for (int i = 0; i < Abilities.COUNT; i++) {
            int bx = x + i * 13, by = y + 12;
            c.fill(bx, by, bx + 12, by + 12, 0xAA000000);
            int cd = ClientState.cd[i];
            if (ClientState.active(i)) {
                c.fill(bx + 1, by + 1, bx + 11, by + 11, 0xFF2ECC71);
            } else if (cd > 0) {
                int hgt = Math.min(10, (int) (10f * cd / Abilities.COOLDOWN[i]));
                c.fill(bx + 1, by + 1, bx + 11, by + 11, 0xFF333333);
                c.fill(bx + 1, by + 11 - hgt, bx + 11, by + 11, 0xFF777777);
            } else {
                c.fill(bx + 1, by + 1, bx + 11, by + 11, 0xFF2F6FD0);
            }
            String k = GojoClient.keys[i].getBoundKeyLocalizedText().getString();
            if (k.length() > 1) k = k.substring(0, 1);
            c.drawText(mc.textRenderer, k, bx + 4, by + 2, 0xFFFFFFFF, false);
        }
    }
}
