package com.gojo.client;

import com.gojo.AbilityPayload;
import com.gojo.AimPayload;
import com.gojo.Abilities;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class GojoScreen extends Screen {
    private static final String[] AIM_NAMES = {"Blue", "Red", "Purple"};
    private ButtonWidget[] abil = new ButtonWidget[Abilities.COUNT];
    private ButtonWidget[] aim = new ButtonWidget[3];

    public GojoScreen() {
        super(Text.literal("Gojo Satoru - Limitless"));
    }

    @Override
    protected void init() {
        abil = new ButtonWidget[Abilities.COUNT];
        aim = new ButtonWidget[3];
        int w = 150, h = 22, gap = 4;
        for (int i = 0; i < Abilities.COUNT; i++) {
            final int id = i;
            int x = width / 2 - w - 2 + (i % 2) * (w + 4);
            int y = 40 + (i / 2) * (h + gap);
            abil[i] = addDrawableChild(ButtonWidget.builder(Text.literal(label(i)),
                            b -> ClientPlayNetworking.send(new AbilityPayload(id)))
                    .dimensions(x, y, w, h)
                    .tooltip(Tooltip.of(Text.literal(Abilities.DESC[i]))).build());
        }
        int aw = 100, ay = 40 + 5 * (h + gap) + 10;
        for (int s = 0; s < 3; s++) {
            final int slot = s;
            int x = width / 2 - (3 * aw + 8) / 2 + s * (aw + 4);
            aim[s] = addDrawableChild(ButtonWidget.builder(Text.literal(aimLabel(s)),
                            b -> ClientPlayNetworking.send(new AimPayload(slot, ClientState.isFixed(slot) ? 0 : 1)))
                    .dimensions(x, ay, aw, h)
                    .tooltip(Tooltip.of(Text.literal("Camera: orb follows your view. Fixed: flies straight along launch direction."))).build());
        }
    }

    private static String label(int i) {
        String s = Abilities.NAMES[i];
        if (ClientState.active(i)) s = "[ON] " + s;
        else if (ClientState.cd[i] > 0) s += " (" + (ClientState.cd[i] + 19) / 20 + "s)";
        else if (Abilities.COST[i] > 0) s += "  [" + Abilities.COST[i] + "]";
        return s;
    }

    private static String aimLabel(int slot) {
        return AIM_NAMES[slot] + ": " + (ClientState.isFixed(slot) ? "Fixed" : "Camera");
    }

    @Override
    public void tick() {
        for (int i = 0; i < abil.length; i++) if (abil[i] != null) abil[i].setMessage(Text.literal(label(i)));
        for (int s = 0; s < aim.length; s++) if (aim[s] != null) aim[s].setMessage(Text.literal(aimLabel(s)));
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        c.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        c.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Cursed Energy: " + ClientState.energy + " / " + (int) Abilities.MAX_ENERGY), width / 2, 26, 0x66AAFF);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("Aiming mode"), width / 2, 40 + 5 * 26 + 1, 0x9FB8FF);
    }

    @Override
    public boolean shouldPause() {
        return false; // multiplayer-safe; singleplayerde de oyun davam edir
    }
}
