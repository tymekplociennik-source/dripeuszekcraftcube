package pl.dripeuszek.craftcube;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import pl.dripeuszek.craftcube.mixin.HandledScreenAccessor;

public class CraftcubeMod implements ClientModInitializer {
    private static KeyBinding guiKey;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        Config.load();

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
                    mc.player.sendMessage(Text.literal(Config.get().enabled
                            ? "§a[Otchłań] Zbieranie WŁĄCZONE"
                            : "§c[Otchłań] Zbieranie WYŁĄCZONE"), true);
                }
            }
            OtchlanModule.tick(mc);
        });

        // wiadomość o otwarciu otchłani – raz jako wiadomość systemowa, raz jako czat
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) OtchlanModule.onMessage(message);
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) ->
                OtchlanModule.onMessage(message));

        // Alt+C nad itemem w dowolnym oknie = dodaj/usuń z listy
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof HandledScreen<?> hs) {
                ScreenKeyboardEvents.afterKeyPress(screen).register((s, key, scancode, mods) -> {
                    if ((mods & GLFW.GLFW_MOD_ALT) == 0) return;
                    if (key == GLFW.GLFW_KEY_C) {
                        toggleHovered(client, hs);
                    } else if (key == GLFW.GLFW_KEY_D && hs instanceof GenericContainerScreen gcs) {
                        OtchlanModule.dump(client, gcs);
                    }
                });
            }
        });
    }

    private static void toggleHovered(MinecraftClient mc, HandledScreen<?> hs) {
        if (mc.player == null) return;
        Slot slot = ((HandledScreenAccessor) hs).getFocusedSlot();
        if (slot == null || slot.getStack().isEmpty()) {
            mc.player.sendMessage(Text.literal("§e[Otchłań] Najedź na item i naciśnij Alt+C"), true);
            return;
        }
        ItemStack stack = slot.getStack();
        Item item = stack.getItem();
        boolean added = Config.get().toggleItem(item);
        mc.player.sendMessage(Text.literal(added
                ? "§a[Otchłań] Dodano do listy: §f"
                : "§c[Otchłań] Usunięto z listy: §f").append(stack.getName()), true);
    }
}
