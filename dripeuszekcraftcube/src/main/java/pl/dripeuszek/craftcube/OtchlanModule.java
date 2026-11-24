package pl.dripeuszek.craftcube;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class OtchlanModule {
    // ustawienia (zmieniane w GUI)
    public static boolean enabled = false;
    public static boolean autoOpen = false;
    public static int clicksPerTick = 54;   // ile ziemniaków na tick (1-54)
    public static int reopenSeconds = 2;

    public static long collected = 0;

    public static void toggle() {
        enabled = !enabled;
    }

    public static void tick(MinecraftClient mc) {
        if (!enabled || mc.player == null || mc.interactionManager == null) return;
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

        GenericContainerScreenHandler h = screen.getScreenHandler();
        int slots = h.getRows() * 9;
        int done = 0;

        for (int i = 0; i < slots && done < clicksPerTick; i++) {
            ItemStack st = h.getSlot(i).getStack();
            if (!st.isOf(Items.POTATO)) continue;

            if (!canHold(mc)) {
                enabled = false;
                mc.player.sendMessage(Text.literal("§c[Otchłań] Pełny ekwipunek – wyłączono"), true);
                return;
            }

            int before = st.getCount();
            mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            ItemStack now = h.getSlot(i).getStack();
            int after = now.isOf(Items.POTATO) ? now.getCount() : 0;
            collected += Math.max(0, before - after);
            done++;
        }
    }

    private static boolean canHold(MinecraftClient mc) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) return true;
            if (s.isOf(Items.POTATO) && s.getCount() < s.getMaxCount()) return true;
        }
        return false;
    }
}
