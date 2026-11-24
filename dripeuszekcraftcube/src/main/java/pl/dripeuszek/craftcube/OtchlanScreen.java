package pl.dripeuszek.craftcube;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class OtchlanScreen extends Screen {
    private static final int W = 260, H = 216;
    private static final int PANEL = 0xFF1B1B26, ROW = 0xFF262636;
    private static final int ACCENT = 0xFF8B5CF6, ACCENT2 = 0xFF22D3EE;
    private static final int ON = 0xFF22C55E, OFF = 0xFF3B3B4D;
    private static final int TEXT = 0xFFEDEDF5, MUTED = 0xFF9A9AB0;

    private int x, y;
    private int dragging = -1;

    public OtchlanScreen() {
        super(Text.literal("dripeuszekcraftcube"));
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
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);

        // ramka + tło
        c.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0x668B5CF6);
        c.fill(x, y, x + W, y + H, PANEL);

        // nagłówek
        c.fillGradient(x, y, x + W, y + 44, 0xFF2E1F66, PANEL);
        c.fill(x, y + 44, x + W, y + 46, ACCENT);
        c.drawText(textRenderer, "dripeuszekcraftcube", x + 16, y + 11, TEXT, true);
        c.drawText(textRenderer, "Otchłań • Ziemniaki", x + 16, y + 26, MUTED, true);
        String st = OtchlanModule.enabled ? "AKTYWNY" : "WYŁĄCZONY";
        c.drawText(textRenderer, st, x + W - 16 - textRenderer.getWidth(st), y + 18,
                OtchlanModule.enabled ? ON : MUTED, true);

        // przełączniki
        toggle(c, x + 16, y + 56, W - 32, 28, "Zbieranie ziemniaków",
                OtchlanModule.enabled, in(mx, my, x + 16, y + 56, W - 32, 28));
        toggle(c, x + 16, y + 92, W - 32, 24, "Auto /otchłań",
                OtchlanModule.autoOpen, in(mx, my, x + 16, y + 92, W - 32, 24));

        // suwaki
        slider(c, y + 140, "Kliknięcia na tick", OtchlanModule.clicksPerTick, 1, 54, "");
        slider(c, y + 176, "Otwieraj /otchłań co", OtchlanModule.reopenSeconds, 1, 20, " s");

        // stopka
        c.drawText(textRenderer, "Zebrano: " + OtchlanModule.collected + "   •   R – włącz/wyłącz",
                x + 16, y + 198, MUTED, true);
    }

    private void toggle(DrawContext c, int rx, int ry, int rw, int rh, String label, boolean on, boolean hov) {
        c.fill(rx, ry, rx + rw, ry + rh, ROW);
        c.fill(rx, ry, rx + 4, ry + rh, on ? ON : OFF);
        if (hov) c.fill(rx, ry, rx + rw, ry + rh, 0x18FFFFFF);
        int ty = ry + (rh - 8) / 2;
        c.drawText(textRenderer, label, rx + 14, ty, TEXT, true);
        String s = on ? "WŁ" : "WYŁ";
        c.drawText(textRenderer, s, rx + rw - textRenderer.getWidth(s) - 12, ty, on ? ON : MUTED, true);
    }

    private void slider(DrawContext c, int by, String label, int val, int min, int max, String suffix) {
        int bx = x + 16, bw = W - 32;
        c.drawText(textRenderer, label, bx, by - 14, MUTED, true);
        String v = val + suffix;
        c.drawText(textRenderer, v, bx + bw - textRenderer.getWidth(v), by - 14, TEXT, true);
        c.fill(bx, by, bx + bw, by + 6, ROW);
        int fw = (int) ((val - min) / (float) (max - min) * bw);
        if (fw > 0) c.fillGradient(bx, by, bx + fw, by + 6, ACCENT2, ACCENT);
        c.fill(bx + fw - 2, by - 3, bx + fw + 2, by + 9, TEXT);
    }

    private boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    private void setSlider(int i, double mx) {
        double f = Math.max(0, Math.min(1, (mx - (x + 16)) / (W - 32)));
        if (i == 0) OtchlanModule.clicksPerTick = 1 + (int) Math.round(f * 53);
        else OtchlanModule.reopenSeconds = 1 + (int) Math.round(f * 19);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (in(mx, my, x + 16, y + 56, W - 32, 28)) { OtchlanModule.toggle(); return true; }
            if (in(mx, my, x + 16, y + 92, W - 32, 24)) { OtchlanModule.autoOpen = !OtchlanModule.autoOpen; return true; }
            if (in(mx, my, x + 16, y + 134, W - 32, 18)) { dragging = 0; setSlider(0, mx); return true; }
            if (in(mx, my, x + 16, y + 170, W - 32, 18)) { dragging = 1; setSlider(1, mx); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging >= 0) { setSlider(dragging, mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = -1;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
