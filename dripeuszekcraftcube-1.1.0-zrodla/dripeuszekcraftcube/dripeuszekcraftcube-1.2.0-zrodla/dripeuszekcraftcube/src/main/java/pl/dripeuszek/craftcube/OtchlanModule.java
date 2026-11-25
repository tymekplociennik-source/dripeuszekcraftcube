package pl.dripeuszek.craftcube;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public class OtchlanModule {
    public static long collected = 0;
    public static int sessions = 0;

    private static int pendingCmd = -1;   // ticki do wpisania komendy (-1 = brak)
    private static int awaiting = 0;      // po wpisaniu komendy akceptuj dowolne okno
    private static int periodicTimer = 0;
    private static long lastCmdMs = 0;    // kiedy ostatnio wpisaliśmy komendę
    private static int curSync = -1;
    private static int sessionTicks = 0;  // ile ticków trwa to okno
    private static int eventDyeClicks = 0; // ile razy kliknięto barwnik w tym "zdarzeniu"
    private static int waitTicks = 0;
    private static int idle = 0;

    public static void toggle() {
        Config c = Config.get();
        c.enabled = !c.enabled;
        periodicTimer = 99999;
        pendingCmd = -1;
        Config.save();
    }

    /** Wpisz komendę od razu (przycisk testowy w GUI). */
    public static void openNow() {
        pendingCmd = 0;
    }

    /** Wywoływane dla każdej wiadomości z czatu / serwera. */
    public static void onMessage(Text t) {
        Config c = Config.get();
        if (!c.enabled || !c.autoOnMessage) return;
        String s = normalize(t.getString());
        // tylko prawdziwe ogłoszenie: "Otchłań została otwarta! ..."
        if (!(s.contains("otchlan") && s.contains("zostala otwarta"))) return;
        // ignoruj powtórki i odpowiedzi serwera zaraz po naszej komendzie
        if (System.currentTimeMillis() - lastCmdMs < c.commandCooldown * 1000L) return;
        if (pendingCmd >= 0) return;
        pendingCmd = Math.max(0, c.reactionDelay);
    }

    private static String normalize(String s) {
        s = s.toLowerCase(Locale.ROOT).replace('ł', 'l');
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        return s.replaceAll("\\p{M}+", "");
    }

    public static void tick(MinecraftClient mc) {
        if (awaiting > 0) awaiting--;
        Config cfg = Config.get();
        if (!cfg.enabled || mc.player == null || mc.interactionManager == null) {
            pendingCmd = -1;
            return;
        }

        if (pendingCmd > 0) {
            pendingCmd--;
        } else if (pendingCmd == 0) {
            pendingCmd = -1;
            sendCommand(mc, cfg);
        }

        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            if (isOtchlan(screen)) process(mc, screen, cfg);
        } else if (mc.currentScreen == null && cfg.periodic && pendingCmd < 0) {
            if (++periodicTimer >= cfg.periodSeconds * 20) {
                periodicTimer = 0;
                sendCommand(mc, cfg);
            }
        }
    }

    private static void sendCommand(MinecraftClient mc, Config cfg) {
        awaiting = 100;
        lastCmdMs = System.currentTimeMillis();
        eventDyeClicks = 0;
        mc.player.networkHandler.sendChatCommand(cfg.command);
    }

    private static boolean isOtchlan(GenericContainerScreen screen) {
        if (awaiting > 0) return true; // właśnie wpisaliśmy komendę – bierz to okno
        String t = normalize(screen.getTitle().getString());
        return t.contains("otchlan");
    }

    private static boolean isDye(ItemStack st) {
        return st.isOf(Items.GREEN_DYE) || st.isOf(Items.LIME_DYE);
    }

    private static void process(MinecraftClient mc, GenericContainerScreen screen, Config cfg) {
        GenericContainerScreenHandler h = screen.getScreenHandler();

        if (h.syncId != curSync) {          // nowe okno
            curSync = h.syncId;
            sessionTicks = 0;
            waitTicks = 0;
            idle = 0;
            sessions++;
        }
        sessionTicks++;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        int slots = h.getRows() * 9;

        // 1) zielony barwnik – z opóźnieniem i z limitem kliknięć na jedno otwarcie
        if (cfg.clickDye && eventDyeClicks < cfg.maxDyeClicks) {
            if (sessionTicks < cfg.dyeDelay) return;
            for (int i = 0; i < slots; i++) {
                if (isDye(h.getSlot(i).getStack())) {
                    mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                    eventDyeClicks++;
                    waitTicks = cfg.afterDyeDelay;
                    return;
                }
            }
            if (sessionTicks < cfg.dyeDelay + 10) return; // poczekaj, aż przycisk się pojawi
        }

        // 2) zbieranie itemów (shift-click)
        Set<Item> wanted = cfg.itemSet();
        int done = 0;
        boolean blocked = false;
        for (int i = 0; i < slots && done < cfg.clicksPerTick; i++) {
            ItemStack st = h.getSlot(i).getStack();
            if (st.isEmpty()) continue;
            if (isDye(st)) continue;
            Item item = st.getItem();
            if (!cfg.collectAll && !wanted.contains(item)) continue;
            if (!canHold(mc, item)) {
                blocked = true;
                continue;
            }
            int before = st.getCount();
            mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            ItemStack now = h.getSlot(i).getStack();
            int after = (!now.isEmpty() && now.isOf(item)) ? now.getCount() : 0;
            collected += Math.max(0, before - after);
            done++;
        }

        if (done == 0) {
            if (++idle >= 15) {   // ok. 0,75 s bez nic do zebrania
                idle = 0;
                if (blocked) {
                    mc.player.sendMessage(Text.literal("§c[Otchłań] Pełny ekwipunek"), true);
                }
                if (cfg.closeWhenDone || blocked) {
                    mc.player.closeHandledScreen();
                    awaiting = 0;
                }
            }
        } else {
            idle = 0;
        }
    }

    private static boolean canHold(MinecraftClient mc, Item item) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) return true;
            if (s.isOf(item) && s.getCount() < s.getMaxCount()) return true;
        }
        return false;
    }

    /** Alt+D w oknie skrzyni: wypisuje na czacie, co mod widzi w oknie (do diagnozy). */
    public static void dump(MinecraftClient mc, GenericContainerScreen screen) {
        if (mc.player == null) return;
        GenericContainerScreenHandler h = screen.getScreenHandler();
        int slots = h.getRows() * 9;
        mc.player.sendMessage(Text.literal("§e[Otchłań] Okno: §f\"" + screen.getTitle().getString()
                + "\" §7(rzędy: " + h.getRows() + ")"), false);
        for (int i = 0; i < slots; i++) {
            ItemStack st = h.getSlot(i).getStack();
            if (st.isEmpty()) continue;
            String id = Registries.ITEM.getId(st.getItem()).toString();
            mc.player.sendMessage(Text.literal("§7[" + i + "] §f" + id + " x" + st.getCount()
                    + " §8" + st.getName().getString()), false);
        }
    }
}
