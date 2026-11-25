package pl.dripeuszek.craftcube;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class OtchlanScreen extends Screen {
    private static final int W = 430, H = 310, HEADER = 44, SIDE = 104;

    private static final int BG = 0xFF13131B, PANEL = 0xFF1A1A25, CARD = 0xFF222230, CARD_HOVER = 0xFF2A2A3B;
    private static final int BORDER = 0xFF30304A;
    private static final int ACCENT = 0xFF8B5CF6, ACCENT2 = 0xFF22D3EE, GREEN = 0xFF22C55E, RED = 0xFFEF4444;
    private static final int TEXT = 0xFFEDEDF5, MUTED = 0xFF8E8EA8, TRACK = 0xFF3A3A4E;

    private static final String[] TABS = {"Ogólne", "Itemy", "Ustawienia"};

    private record Hit(int x, int y, int w, int h, Runnable action) {}
    private record SliderHit(int x, int y, int w, int h, Consumer<Double> setter) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<SliderHit> sliderHits = new ArrayList<>();
    private final Map<String, Float> anims = new HashMap<>();
    private final Map<Item, ItemStack> stacks = new HashMap<>();

    private SliderHit dragging;
    private int tab = 0;
    private float scroll = 0f;
    private float dt = 0.016f;
    private long last, openStart;
    private int x, y;
    private int clipT = Integer.MIN_VALUE, clipB = Integer.MAX_VALUE;
    private static final int SETTINGS_H = 390;
    private String tip;
    private int tipX, tipY;

    public OtchlanScreen() {
        super(Text.literal("dripeuszekcraftcube"));
        openStart = System.nanoTime();
        last = openStart;
    }

    @Override
    protected void init() {
        x = (width - W) / 2;
        y = (height - H) / 2;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        Config.save();
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
        hits.clear();
        sliderHits.clear();
        tip = null;

        float open = ease(Math.min(1f, (now - openStart) / 200_000_000f));
        boolean animating = open < 1f;
        if (animating) {
            float sc = 0.94f + 0.06f * open;
            c.getMatrices().push();
            c.getMatrices().translate(width / 2f, height / 2f, 0f);
            c.getMatrices().scale(sc, sc, 1f);
            c.getMatrices().translate(-width / 2f, -height / 2f, 0f);
        }
        drawWindow(c, mx, my, now);
        if (animating) c.getMatrices().pop();
        drawTooltip(c);
    }

    private void drawWindow(DrawContext c, int mx, int my, long now) {
        Config cfg = Config.get();

        // tło + obramowanie
        roundRect(c, x - 1, y - 1, W + 2, H + 2, withAlpha(ACCENT, 0x66));
        roundRect(c, x, y, W, H, BG);

        // nagłówek
        c.fillGradient(x + 1, y + 1, x + W - 1, y + HEADER, 0xFF2C1E63, 0xFF181822);
        c.fill(x + 1, y + HEADER - 1, x + W - 1, y + HEADER, withAlpha(ACCENT, 0x99));

        c.fillGradient(x + 14, y + 10, x + 38, y + 34, ACCENT, ACCENT2);
        c.drawCenteredTextWithShadow(textRenderer, "D", x + 26, y + 18, 0xFFFFFFFF);
        c.drawText(textRenderer, "dripeuszekcraftcube", x + 46, y + 11, TEXT, true);
        c.drawText(textRenderer, "Otchłań • auto zbieranie", x + 46, y + 24, MUTED, true);

        // pigułka statusu
        boolean on = cfg.enabled;
        float pt = anim("pill", on ? 1f : 0f);
        int pillW = 96, pillH = 20, pillX = x + W - 36 - pillW, pillY = y + 12;
        boolean pillHover = in(mx, my, pillX, pillY, pillW, pillH);
        roundRect(c, pillX, pillY, pillW, pillH,
                lerpColor(pillHover ? 0x66808098 : 0x44808098, pillHover ? 0x6622C55E : 0x4422C55E, pt));
        double pulse = on ? 0.65 + 0.35 * Math.sin(now / 1_000_000_000.0 * 4.0) : 0.4;
        c.fill(pillX + 8, pillY + 7, pillX + 14, pillY + 13,
                withAlpha(lerpColor(MUTED, GREEN, pt), (int) (255 * pulse)));
        c.drawText(textRenderer, on ? "AKTYWNY" : "WYŁĄCZONY", pillX + 20, pillY + 6,
                lerpColor(MUTED, GREEN, pt), true);
        hits.add(new Hit(pillX, pillY, pillW, pillH, () -> { click(); OtchlanModule.toggle(); }));

        // przycisk zamknięcia
        int cbx = x + W - 30, cby = y + 12;
        boolean cbHover = in(mx, my, cbx, cby, 20, 20);
        float cbt = anim("close", cbHover ? 1f : 0f);
        roundRect(c, cbx, cby, 20, 20, lerpColor(0x00000000, withAlpha(RED, 0xAA), cbt));
        c.drawCenteredTextWithShadow(textRenderer, "×", cbx + 10, cby + 6, TEXT);
        hits.add(new Hit(cbx, cby, 20, 20, this::close));

        drawSidebar(c, mx, my);

        int cx = x + SIDE + 14, cy = y + HEADER + 14, cw = W - SIDE - 28, ch = H - HEADER - 28;
        switch (tab) {
            case 0 -> drawGeneral(c, mx, my, cx, cy, cw);
            case 1 -> drawItems(c, mx, my, cx, cy, cw, ch);
            default -> drawSettings(c, mx, my, cx, cy, cw, ch);
        }
    }

    private void drawSidebar(DrawContext c, int mx, int my) {
        c.fill(x + 1, y + HEADER, x + SIDE, y + H - 1, PANEL);
        c.fill(x + SIDE, y + HEADER, x + SIDE + 1, y + H - 1, BORDER);

        for (int i = 0; i < TABS.length; i++) {
            int tx = x + 8, ty = y + HEADER + 12 + i * 30, tw = SIDE - 16, th = 26;
            boolean sel = tab == i;
            boolean hov = in(mx, my, tx, ty, tw, th);
            float st = anim("tabsel" + i, sel ? 1f : 0f);
            float ht = anim("tabhov" + i, hov ? 1f : 0f);
            roundRect(c, tx, ty, tw, th, lerpColor(PANEL, CARD, Math.max(st, ht * 0.6f)));
            int barH = (int) (18 * st);
            if (barH > 0) {
                c.fillGradient(tx + 2, ty + 13 - barH / 2, tx + 5, ty + 13 + barH / 2, ACCENT2, ACCENT);
            }
            c.drawText(textRenderer, TABS[i], tx + 12, ty + 9, lerpColor(MUTED, TEXT, Math.max(st, ht * 0.5f)), true);
            if (i == 1) {
                String n = String.valueOf(Config.get().itemSet().size());
                c.drawText(textRenderer, n, tx + tw - 8 - textRenderer.getWidth(n), ty + 9, ACCENT2, true);
            }
            final int idx = i;
            hits.add(new Hit(tx, ty, tw, th, () -> { click(); tab = idx; scroll = 0f; }));
        }

        // statystyki
        c.drawText(textRenderer, "Zebrano", x + 14, y + H - 64, MUTED, true);
        c.drawText(textRenderer, String.format(Locale.ROOT, "%,d", OtchlanModule.collected), x + 14, y + H - 53, ACCENT2, true);
        c.drawText(textRenderer, "Sesje", x + 14, y + H - 38, MUTED, true);
        c.drawText(textRenderer, String.format(Locale.ROOT, "%,d", OtchlanModule.sessions), x + 14, y + H - 27, ACCENT2, true);
    }

    // ------------------------------------------------------------ zakładki

    private void drawGeneral(DrawContext c, int mx, int my, int cx, int cy, int cw) {
        Config cfg = Config.get();
        boolean on = cfg.enabled;
        float t = anim("power", on ? 1f : 0f);
        boolean hov = in(mx, my, cx, cy, cw, 56);

        roundRect(c, cx, cy, cw, 56, hov ? CARD_HOVER : CARD);
        border(c, cx, cy, cw, 56, lerpColor(BORDER, GREEN, t));
        c.drawText(textRenderer, "Zbieranie z otchłani", cx + 14, cy + 14, TEXT, true);
        c.drawText(textRenderer, "Klawisz R – szybkie włączanie", cx + 14, cy + 30, MUTED, true);
        drawSwitch(c, cx + cw - 14 - 40, cy + 18, 40, 20, t);
        hits.add(new Hit(cx, cy, cw, 56, () -> { click(); OtchlanModule.toggle(); }));

        int ry = cy + 68;
        ry = toggleRow(c, mx, my, cx, ry, cw, "Auto /otchlan po wiadomości", "Reaguje na ogłoszenie na czacie",
                cfg.autoOnMessage, v -> cfg.autoOnMessage = v);
        ry = toggleRow(c, mx, my, cx, ry, cw, "Klikaj zielony barwnik", "Klika przycisk po otwarciu okna",
                cfg.clickDye, v -> cfg.clickDye = v);
        ry = toggleRow(c, mx, my, cx, ry, cw, "Zamykaj okno po zebraniu", "Zamyka, gdy nic już nie zostało",
                cfg.closeWhenDone, v -> cfg.closeWhenDone = v);
        ry = toggleRow(c, mx, my, cx, ry, cw, "Powiadomienia", "Komunikaty nad paskiem ekwipunku",
                cfg.notifications, v -> cfg.notifications = v);
        ry = toggleRow(c, mx, my, cx, ry, cw, "Zbieraj wszystko", "Ignoruje listę itemów (do testów)",
                cfg.collectAll, v -> cfg.collectAll = v);
    }

    private void drawItems(DrawContext c, int mx, int my, int cx, int cy, int cw, int ch) {
        List<Item> list = new ArrayList<>(Config.get().itemSet());
        c.drawText(textRenderer, "Zbierane itemy (" + list.size() + ")", cx, cy + 5, TEXT, true);

        int bw2 = 60, bw1 = 62;
        button(c, mx, my, cx + cw - bw2, cy, bw2, 18, "Wyczyść", RED, () -> Config.get().clearItems());
        button(c, mx, my, cx + cw - bw2 - 4 - bw1, cy, bw1, 18, "+ Z ręki", ACCENT, this::addFromHand);

        roundRect(c, cx, cy + 24, cw, 20, CARD);
        c.drawText(textRenderer, "Alt+C nad itemem w oknie – dodaje lub usuwa", cx + 8, cy + 30, MUTED, true);

        int gy = cy + 52, gh = ch - 52;
        int cols = Math.max(1, cw / 34);
        int rows = (list.size() + cols - 1) / cols;
        int contentH = rows * 34;
        float maxScroll = Math.max(0, contentH - gh);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        if (list.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, "Lista jest pusta", cx + cw / 2, gy + gh / 2 - 12, MUTED);
            c.drawCenteredTextWithShadow(textRenderer, "Najedź na item i naciśnij Alt+C", cx + cw / 2, gy + gh / 2 + 2, MUTED);
            return;
        }

        c.enableScissor(cx, gy, cx + cw, gy + gh);
        for (int i = 0; i < list.size(); i++) {
            int ix = cx + (i % cols) * 34;
            int iy = gy + (i / cols) * 34 - (int) scroll;
            if (iy + 30 < gy || iy > gy + gh) continue;

            Item item = list.get(i);
            boolean hov = in(mx, my, ix, iy, 30, 30) && my >= gy && my < gy + gh;
            roundRect(c, ix, iy, 30, 30, hov ? CARD_HOVER : CARD);
            if (hov) {
                border(c, ix, iy, 30, 30, RED);
                tip = stack(item).getName().getString() + " – kliknij, aby usunąć";
                tipX = mx;
                tipY = my;
            }
            c.drawItem(stack(item), ix + 7, iy + 7);

            int hy = Math.max(iy, gy);
            int hh = Math.min(iy + 30, gy + gh) - hy;
            hits.add(new Hit(ix, hy, 30, hh, () -> { click(); Config.get().toggleItem(item); }));
        }
        c.disableScissor();

        if (maxScroll > 0) {
            int barH = Math.max(14, (int) (gh * (gh / (float) contentH)));
            int barY = gy + (int) ((gh - barH) * (scroll / maxScroll));
            c.fill(cx + cw - 3, gy, cx + cw, gy + gh, withAlpha(TRACK, 0x88));
            c.fill(cx + cw - 3, barY, cx + cw, barY + barH, ACCENT);
        }
    }

    private void drawSettings(DrawContext c, int mx, int my, int cx, int cy, int cw, int ch) {
        Config cfg = Config.get();
        float maxScroll = Math.max(0, SETTINGS_H - ch);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        int vw = cw - (maxScroll > 0 ? 8 : 0);
        int ry = cy - (int) scroll;

        c.enableScissor(cx, cy, cx + cw, cy + ch);
        clipT = cy;
        clipB = cy + ch;

        ry = sliderRow(c, mx, my, cx, ry, vw, "Kliknięcia na tick", cfg.clicksPerTick, 1, 54,
                String.valueOf(cfg.clicksPerTick), v -> cfg.clicksPerTick = v);
        ry = sliderRow(c, mx, my, cx, ry, vw, "Opóźnienie reakcji na wiadomość", cfg.reactionDelay, 0, 20,
                cfg.reactionDelay + " tick", v -> cfg.reactionDelay = v);
        ry = sliderRow(c, mx, my, cx, ry, vw, "Opóźnienie przed barwnikiem", cfg.dyeDelay, 0, 40,
                cfg.dyeDelay + " tick", v -> cfg.dyeDelay = v);
        ry = sliderRow(c, mx, my, cx, ry, vw, "Przerwa po barwniku", cfg.afterDyeDelay, 0, 40,
                cfg.afterDyeDelay + " tick", v -> cfg.afterDyeDelay = v);
        ry = sliderRow(c, mx, my, cx, ry, vw, "Kliknięcia barwnika na otwarcie", cfg.maxDyeClicks, 1, 50,
                String.valueOf(cfg.maxDyeClicks), v -> cfg.maxDyeClicks = v);
        ry = sliderRow(c, mx, my, cx, ry, vw, "Cooldown po komendzie", cfg.commandCooldown, 1, 60,
                cfg.commandCooldown + " s", v -> cfg.commandCooldown = v);
        ry = toggleRow(c, mx, my, cx, ry, vw, "Cykliczne otwieranie", "Otwiera bez czekania na wiadomość",
                cfg.periodic, v -> cfg.periodic = v);
        ry = sliderRow(c, mx, my, cx, ry, vw, "Cyklicznie co", cfg.periodSeconds, 1, 60,
                cfg.periodSeconds + " s", v -> cfg.periodSeconds = v);

        button(c, mx, my, cx, ry + 4, vw, 22, "Testuj /" + cfg.command + " teraz", ACCENT2, OtchlanModule::openNow);
        c.drawText(textRenderer, "Alt+D w oknie – wypisuje zawartość na czacie", cx, ry + 34, MUTED, true);

        c.disableScissor();
        clipT = Integer.MIN_VALUE;
        clipB = Integer.MAX_VALUE;

        if (maxScroll > 0) {
            int barH = Math.max(14, (int) (ch * (ch / (float) SETTINGS_H)));
            int barY = cy + (int) ((ch - barH) * (scroll / maxScroll));
            c.fill(cx + cw - 3, cy, cx + cw, cy + ch, withAlpha(TRACK, 0x88));
            c.fill(cx + cw - 3, barY, cx + cw, barY + barH, ACCENT);
        }
    }

    // ------------------------------------------------------------- widgety

    private int toggleRow(DrawContext c, int mx, int my, int rx, int ry, int rw, String label, String desc,
                          boolean value, Consumer<Boolean> setter) {
        boolean hov = in(mx, my, rx, ry, rw, 28);
        roundRect(c, rx, ry, rw, 28, hov ? CARD_HOVER : CARD);
        c.drawText(textRenderer, label, rx + 10, ry + 5, TEXT, true);
        c.drawText(textRenderer, desc, rx + 10, ry + 16, MUTED, true);
        float t = anim("sw:" + label, value ? 1f : 0f);
        drawSwitch(c, rx + rw - 10 - 34, ry + 7, 34, 14, t);
        addHit(rx, ry, rw, 28, () -> { click(); setter.accept(!value); Config.save(); });
        return ry + 32;
    }

    private int sliderRow(DrawContext c, int mx, int my, int rx, int ry, int rw, String label, int val, int min, int max,
                          String valText, Consumer<Integer> setter) {
        roundRect(c, rx, ry, rw, 40, CARD);
        c.drawText(textRenderer, label, rx + 10, ry + 6, TEXT, true);
        c.drawText(textRenderer, valText, rx + rw - 10 - textRenderer.getWidth(valText), ry + 6, ACCENT2, true);

        int bx = rx + 10, by = ry + 26, bw = rw - 20;
        float f = (val - min) / (float) (max - min);
        int fw = (int) (f * bw);
        roundRect(c, bx, by, bw, 6, TRACK);
        if (fw > 0) c.fillGradient(bx, by, bx + fw, by + 6, ACCENT2, ACCENT);
        boolean hov = in(mx, my, bx - 4, ry + 18, bw + 8, 20) || (dragging != null);
        roundRect(c, bx + fw - 4, by - 3, 8, 12, hov ? 0xFFFFFFFF : TEXT);
        addSlider(bx - 4, ry + 18, bw + 8, 20,
                d -> setter.accept(min + (int) Math.round(d * (max - min))));
        return ry + 44;
    }

    private void button(DrawContext c, int mx, int my, int bx, int by, int bw, int bh, String label, int accent, Runnable action) {
        boolean hov = in(mx, my, bx, by, bw, bh);
        float t = anim("btn:" + label, hov ? 1f : 0f);
        roundRect(c, bx, by, bw, bh, lerpColor(CARD, withAlpha(accent, 0xFF) & 0x00FFFFFF | 0x66000000, t * 0.8f));
        border(c, bx, by, bw, bh, lerpColor(withAlpha(accent, 0x66), accent, t));
        c.drawCenteredTextWithShadow(textRenderer, label, bx + bw / 2, by + (bh - 8) / 2 + 1, TEXT);
        addHit(bx, by, bw, bh, () -> { click(); action.run(); });
    }

    private void addHit(int hx, int hy, int hw, int hh, Runnable action) {
        int y1 = Math.max(hy, clipT), y2 = Math.min(hy + hh, clipB);
        if (y2 <= y1) return;
        hits.add(new Hit(hx, y1, hw, y2 - y1, action));
    }

    private void addSlider(int hx, int hy, int hw, int hh, Consumer<Double> setter) {
        int y1 = Math.max(hy, clipT), y2 = Math.min(hy + hh, clipB);
        if (y2 <= y1) return;
        sliderHits.add(new SliderHit(hx, y1, hw, y2 - y1, setter));
    }

    private void drawSwitch(DrawContext c, int sx, int sy, int sw, int sh, float t) {
        roundRect(c, sx, sy, sw, sh, lerpColor(TRACK, GREEN, t));
        int knob = sh - 4;
        int kx = sx +
