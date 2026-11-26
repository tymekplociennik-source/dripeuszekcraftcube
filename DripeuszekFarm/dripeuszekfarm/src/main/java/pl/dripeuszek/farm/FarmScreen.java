package pl.dripeuszek.farm;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class FarmScreen extends Screen {
    private static final int W = 320;

    private static final String[] NAMES = {"Kopanie (motyka)", "Sadzenie", "Zbieranie"};
    private static final String[] EMPTY = {
            "brak – nie kopie",
            "brak – sadzi w strefie zbierania / w zasięgu",
            "brak – zbiera w całym zasięgu"
    };
    private int zoneY;

    public FarmScreen() {
        super(Text.literal("DripeuszekFarm"));
    }

    private static Text onOff(String name, boolean v) {
        return Text.literal(name + ": " + (v ? "\u00a7aWŁ" : "\u00a7cWYŁ"));
    }

    private static Zone zone(int i) {
        return i == 0 ? FarmConfig.till : i == 1 ? FarmConfig.plant : FarmConfig.harvest;
    }

    @Override
    protected void init() {
        int x = width / 2 - W / 2;
        int y = height / 2 - 125;

        addDrawableChild(ButtonWidget.builder(onOff("Autofarm", FarmConfig.enabled), b -> {
            FarmConfig.toggle(client);
            b.setMessage(onOff("Autofarm", FarmConfig.enabled));
        }).dimensions(x, y, W, 20).build());

        addDrawableChild(new RadiusSlider(x, y + 26, W, 20));

        addDrawableChild(ButtonWidget.builder(Text.literal("Opóźnienie akcji: " + FarmConfig.delay + " tick"), b -> {
            FarmConfig.delay = FarmConfig.delay >= 10 ? 1 : FarmConfig.delay + 1;
            b.setMessage(Text.literal("Opóźnienie akcji: " + FarmConfig.delay + " tick"));
        }).dimensions(x, y + 52, W, 20).build());

        addDrawableChild(ButtonWidget.builder(onOff("Chodzenie", FarmConfig.walk), b -> {
            FarmConfig.walk = !FarmConfig.walk;
            b.setMessage(onOff("Chodzenie", FarmConfig.walk));
        }).dimensions(x, y + 78, W / 2 - 2, 20).build());

        addDrawableChild(ButtonWidget.builder(onOff("Obracanie", FarmConfig.rotate), b -> {
            FarmConfig.rotate = !FarmConfig.rotate;
            b.setMessage(onOff("Obracanie", FarmConfig.rotate));
        }).dimensions(x + W / 2 + 2, y + 78, W / 2 - 2, 20).build());

        zoneY = y + 110;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            int by = zoneY + i * 38;
            addDrawableChild(ButtonWidget.builder(Text.literal("Zaznacz"), b ->
                    FarmBot.startSelect(client, zone(idx), NAMES[idx])
            ).dimensions(x + W - 142, by, 70, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Wyczyść"), b -> {
                zone(idx).clear();
                FarmConfig.save();
                FarmBot.reset(client);
            }).dimensions(x + W - 68, by, 68, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Zamknij"), b -> close())
                .dimensions(x, zoneY + 3 * 38 + 4, W, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int x = width / 2 - W / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("DripeuszekFarm – autofarm marchewek"), width / 2, height / 2 - 140, 0xFFFFFF);
        for (int i = 0; i < 3; i++) {
            int by = zoneY + i * 38;
            ctx.drawTextWithShadow(textRenderer, Text.literal(NAMES[i]), x, by + 6, 0xFFFFFF);
            String d = zone(i).describe();
            ctx.drawTextWithShadow(textRenderer, Text.literal(d != null ? d : EMPTY[i]), x, by + 23, d != null ? 0x55FF55 : 0xAAAAAA);
        }
    }

    @Override
    public void removed() {
        FarmConfig.save();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static class RadiusSlider extends SliderWidget {
        RadiusSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), (FarmConfig.radius - 1) / 99.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Zasięg: " + FarmConfig.radius + " bloków"));
        }

        @Override
        protected void applyValue() {
            FarmConfig.radius = 1 + (int) Math.round(value * 99);
        }
    }
}
