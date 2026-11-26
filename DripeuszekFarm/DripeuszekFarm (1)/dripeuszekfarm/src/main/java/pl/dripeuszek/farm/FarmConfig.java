package pl.dripeuszek.farm;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;

public class FarmConfig {
    public static boolean enabled = false;
    /** Zasięg szukania marchewek wokół gracza: 1-100 bloków. */
    public static int radius = 20;
    /** Odstęp między akcjami w tickach (1-10). */
    public static int delay = 2;
    public static boolean walk = true;
    public static boolean rotate = true;

    public static final Zone till = new Zone();    // gdzie kopać (motyką robić grządki)
    public static final Zone plant = new Zone();   // gdzie sadzić
    public static final Zone harvest = new Zone(); // gdzie zbierać

    /** Skrzynia, do której odkładamy nadmiar marchewek (null = nie odkładaj). */
    public static BlockPos chest = null;
    /** Ile marchewek zostawić w ekwipunku na sadzenie. */
    public static int keep = 64;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("dripeuszekfarm.json");
    }

    public static void toggle(MinecraftClient mc) {
        enabled = !enabled;
        FarmBot.reset(mc);
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(Text.literal("Autofarm: " + (enabled ? "\u00a7aWŁĄCZONY" : "\u00a7cWYŁĄCZONY")), true);
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("radius", radius);
            o.addProperty("delay", delay);
            o.addProperty("walk", walk);
            o.addProperty("rotate", rotate);
            o.addProperty("keep", keep);
            JsonArray ca = new JsonArray();
            if (chest != null) { ca.add(chest.getX()); ca.add(chest.getY()); ca.add(chest.getZ()); }
            o.add("chest", ca);
            o.add("till", till.toJson());
            o.add("plant", plant.toJson());
            o.add("harvest", harvest.toJson());
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception ignored) {
        }
    }

    public static void load() {
        try {
            if (!Files.exists(file())) return;
            JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            if (o.has("radius")) radius = Math.max(1, Math.min(100, o.get("radius").getAsInt()));
            if (o.has("delay")) delay = Math.max(1, Math.min(10, o.get("delay").getAsInt()));
            if (o.has("walk")) walk = o.get("walk").getAsBoolean();
            if (o.has("rotate")) rotate = o.get("rotate").getAsBoolean();
            if (o.has("keep")) keep = Math.max(0, Math.min(1024, o.get("keep").getAsInt()));
            chest = null;
            JsonElement ce = o.get("chest");
            if (ce != null && ce.isJsonArray() && ce.getAsJsonArray().size() == 3) {
                JsonArray ca = ce.getAsJsonArray();
                chest = new BlockPos(ca.get(0).getAsInt(), ca.get(1).getAsInt(), ca.get(2).getAsInt());
            }
            till.fromJson(o.get("till"));
            plant.fromJson(o.get("plant"));
            harvest.fromJson(o.get("harvest"));
        } catch (Exception ignored) {
        }
    }
}
