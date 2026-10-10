package com.gojo.client;

import com.gojo.AbilityPayload;
import com.gojo.Abilities;
import com.gojo.StatePayload;
import com.gojo.TargetsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import com.gojo.GojoMod;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class GojoClient implements ClientModInitializer {
    public static final KeyBinding[] keys = new KeyBinding[Abilities.COUNT];
    private static KeyBinding menuKey;

    // Blue Z, Red X, Purple C, Infinity V, Reverse B, BlackFlash N, Teleport G, Domain H, Simple J, SixEyes K
    private static final int[] DEFAULTS = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B,
            GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K};

    @Override
    public void onInitializeClient() {
        for (int i = 0; i < Abilities.COUNT; i++) {
            keys[i] = KeyBindingHelper.registerKeyBinding(
                    new KeyBinding("key.gojo." + Abilities.IDS[i], InputUtil.Type.KEYSYM, DEFAULTS[i], "category.gojo"));
        }
        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.gojo.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.gojo"));

        ClientPlayNetworking.registerGlobalReceiver(StatePayload.ID, (payload, ctx) -> ClientState.update(payload));
        ClientPlayNetworking.registerGlobalReceiver(TargetsPayload.ID, (payload, ctx) -> ClientState.setTargets(payload.list()));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            for (int i = 0; i < Abilities.COUNT; i++) {
                while (keys[i].wasPressed()) ClientPlayNetworking.send(new AbilityPayload(i));
            }
            while (menuKey.wasPressed()) mc.setScreen(new GojoScreen());
        });

        HudRenderCallback.EVENT.register(Hud::render);
        EntityRendererRegistry.register(GojoMod.ORB, OrbRenderer::new);

        // Domain: fade + donmus oyuncu (hereket/tullanma/sprint blok)
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.player == null) { ClientState.victim = false; ClientState.caster = false; ClientState.flags = 0; ClientState.targets.clear(); }
            float target = ClientState.victim ? 0.92f : ClientState.caster ? 0.4f : 0f;
            ClientState.domainAlpha += (target - ClientState.domainAlpha) * 0.25f;
            if (Math.abs(target - ClientState.domainAlpha) < 0.01f) ClientState.domainAlpha = target;
            if (mc.player != null && ClientState.victim) {
                mc.options.forwardKey.setPressed(false);
                mc.options.backKey.setPressed(false);
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(false);
                mc.options.jumpKey.setPressed(false);
                mc.options.sprintKey.setPressed(false);
            }
        });
    }
}
