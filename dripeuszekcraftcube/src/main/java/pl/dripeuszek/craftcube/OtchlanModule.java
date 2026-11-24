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
    public static boolean autoOpen = true;
    public static int clicksPerTick = 54;   // ile ziemniaków na tick (1-54)
    public static int reopenSeconds = 2;    // co ile sekund ponownie otwierać /otchłań

    public static long collected = 0;

    private static int reopenTimer = 0;
    private static int awaiting = 0;   // ticki, w których akceptujemy dowolne okno po wpisaniu komendy
    private static int idleTicks = 0;

    public static void toggle() {
        enabled = !enabled;
        reopenTimer = 9999; // otwórz od razu po włączeniu
        idleTicks = 0;
    }

    public static void tick(MinecraftClient mc) {
        if (awaiting > 0) awaiting--;
        if (!enabled || mc.player == null || mc.interactionManager == null) return;

        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            if (!isOtchlan(screen)) return;

            GenericContainerScreenHandler h = screen.getScreenHandler();
            int slots = h.getRows() * 9;
            int done = 0;

            for (int i = 0; i < slots && done < clicksPerTick; i++) {
                ItemStack st = h.getSlot(i).getStack();
                if (!st.isOf(Items.POTATO)) continue;

                if (!canHold(mc)) {
                    enabled = false;
                    mc.player.sendMessage(Text.literal("§c[Otchłań] Pełny ekwipunek – wyłączono"), true);
                    mc.player.closeHandledScreen();
                    return;
                }

                int before = st.getCount();
                mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                ItemStack now = h.getSlot(i).getStack();
                int after = now.isOf(Items.POTATO) ? now.getCount() : 0;
                collected += Math.max(0, before - after);
                done++;
            }

            if (done == 0) {
                // brak ziemniaków – poczekaj chwilę (zawartość mogła się jeszcze nie załadować) i zamknij
                if (++idleTicks >= 3) {
                    idleTicks = 0;
                    mc.player.closeHandledScreen();
                    reopenTimer = 0;
                }
            } else {
                idleTicks = 0;
            }
        } else if (mc.currentScreen == null && autoOpen) {
            if (++reopenTimer >= reopenSeconds * 20) {
                reopenTimer = 0;
                awaiting = 60;
                mc.player.networkHandler.sendChatCommand("otchłań");
            }
        }
    }

    private static boolean isOtchlan(GenericContainerScreen screen) {
        if (awaiting > 0) return true; // właśnie wpisaliśmy komendę – bierz to okno
        String t = screen.getTitle().getString().toLowerCase();
        return t.contains("otchłań") || t.contains("otchlan");
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
