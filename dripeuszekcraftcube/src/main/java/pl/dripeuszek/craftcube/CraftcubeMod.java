package pl.dripeuszek.craftcube;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class CraftcubeMod implements ClientModInitializer {
    private static KeyBinding guiKey;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dripeuszekcraftcube.gui", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, "category.dripeuszekcraftcube"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dripeuszekcraftcube.toggle", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R, "category.dripeuszekcraftcube"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (guiKey.wasPressed()) {
                mc.setScreen(new OtchlanScreen());
            }
            while (toggleKey.wasPressed()) {
                OtchlanModule.toggle();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal(OtchlanModule.enabled
                            ? "§a[Otchłań] Zbieranie WŁĄCZONE"
                            : "§c[Otchłań] Zbieranie WYŁĄCZONE"), true);
                }
            }
            OtchlanModule.tick(mc);
        });
    }
}
