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
