package pl.dripeuszek.farm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.util.math.BlockPos;

/** Prostopadłościan zaznaczony dwoma punktami (A i B). */
public class Zone {
    public BlockPos a, b;

    public boolean isSet() {
        return a != null && b != null;
    }

    /** Pion ma 1 blok luzu (żeby zaznaczenie "na ziemi" łapało i grządkę, i roślinę). */
    public boolean contains(BlockPos p) {
        if (!isSet()) return false;
        return p.getX() >= Math.min(a.getX(), b.getX()) && p.getX() <= Math.max(a.getX(), b.getX())
                && p.getZ() >= Math.min(a.getZ(), b.getZ()) && p.getZ() <= Math.max(a.getZ(), b.getZ())
                && p.getY() >= Math.min(a.getY(), b.getY()) - 1 && p.getY() <= Math.max(a.getY(), b.getY()) + 1;
    }

    public void clear() {
        a = null;
        b = null;
    }

    public String describe() {
        if (!isSet()) return null;
        return "A: " + a.getX() + " " + a.getY() + " " + a.getZ()
                + "  |  B: " + b.getX() + " " + b.getY() + " " + b.getZ();
    }

    public JsonArray toJson() {
        JsonArray arr = new JsonArray();
        if (isSet()) {
            arr.add(a.getX()); arr.add(a.getY()); arr.add(a.getZ());
            arr.add(b.getX()); arr.add(b.getY()); arr.add(b.getZ());
        }
        return arr;
    }

    public void fromJson(JsonElement el) {
        clear();
        if (el == null || !el.isJsonArray()) return;
        JsonArray arr = el.getAsJsonArray();
        if (arr.size() != 6) return;
        a = new BlockPos(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt());
        b = new BlockPos(arr.get(3).getAsInt(), arr.get(4).getAsInt(), arr.get(5).getAsInt());
    }
}
