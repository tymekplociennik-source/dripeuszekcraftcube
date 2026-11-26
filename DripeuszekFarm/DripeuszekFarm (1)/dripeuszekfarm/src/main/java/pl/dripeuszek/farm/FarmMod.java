package pl.dripeuszek.farm;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class FarmMod implements ClientModInitializer {
    public static KeyBinding menuKey, toggleKey, confirmKey;

    @Override
    public void onInitializeClient() {
        FarmConfig.load();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dripeuszekfarm.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, "category.dripeuszekfarm"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dripeuszekfarm.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.dripeuszekfarm"));
        confirmKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dripeuszekfarm.confirm", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, "category.dripeuszekfarm"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.wasPressed()) {
                if (mc.player != null && mc.currentScreen == null) {
                    FarmBot.cancelSelect();
                    mc.setScreen(new FarmScreen());
                }
            }
            while (toggleKey.wasPressed()) {
                if (mc.player != null && mc.currentScreen == null) FarmConfig.toggle(mc);
            }
            while (confirmKey.wasPressed()) {
                if (mc.player != null && mc.currentScreen == null) FarmBot.confirm(mc);
            }
            FarmBot.tick(mc);
        });
    }
}
