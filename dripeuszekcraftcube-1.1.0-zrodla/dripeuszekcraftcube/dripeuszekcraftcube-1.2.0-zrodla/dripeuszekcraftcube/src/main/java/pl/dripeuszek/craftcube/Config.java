package pl.dripeuszek.craftcube;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Config {
    public boolean enabled = false;
    public boolean autoOnMessage = true;
    public boolean clickDye = true;
    public boolean closeWhenDone = true;
    public boolean notifications = true;
    public boolean periodic = false;
    public boolean collectAll = false;
    public int periodSeconds = 10;
    public int clicksPerTick = 54;
    public int reactionDelay = 0;
    public int dyeDelay = 10;        // ticki po otwarciu okna, zanim klikniemy barwnik
    public int afterDyeDelay = 10;   // ticki przerwy po kliknięciu barwnika
    public int maxDyeClicks = 1;     // ile razy klikać barwnik na jedno otwarcie otchłani
    public int commandCooldown = 15; // sekundy ignorowania wiadomości po wpisaniu komendy
    public String command = "otchlan";
    public List<String> items = new ArrayList<>(List.of("minecraft:potato"));

    private transient Set<Item> cache;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    public static Config get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("dripeuszekcraftcube.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) {
                    instance = c;
                    instance.sanitize();
                }
            }
        } catch (Exception e) {
            System.err.println("[dripeuszekcraftcube] Nie udało się wczytać configu: " + e);
        }
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (IOException e) {
            System.err.println("[dripeuszekcraftcube] Nie udało się zapisać configu: " + e);
        }
    }

    private void sanitize() {
        if (items == null) items = new ArrayList<>();
        if (command == null || command.isBlank()) command = "otchlan";
        while (command.startsWith("/")) command = command.substring(1);
        clicksPerTick = Math.max(1, Math.min(54, clicksPerTick));
        reactionDelay = Math.max(0, Math.min(20, reactionDelay));
        periodSeconds = Math.max(1, Math.min(60, periodSeconds));
        dyeDelay = Math.max(0, Math.min(40, dyeDelay));
        afterDyeDelay = Math.max(0, Math.min(40, afterDyeDelay));
        maxDyeClicks = Math.max(1, Math.min(5, maxDyeClicks));
        commandCooldown = Math.max(1, Math.min(60, commandCooldown));
        cache = null;
    }

    /** Zbiór itemów do zbierania (w kolejności dodania). */
    public Set<Item> itemSet() {
        if (cache == null) {
            Set<Item> s = new LinkedHashSet<>();
            for (String id : items) {
                Identifier i = Identifier.tryParse(id);
                if (i == null) continue;
                Item it = Registries.ITEM.get(i);
                if (it != Items.AIR) s.add(it);
            }
            cache = s;
        }
        return cache;
    }

    /** Dodaje item do listy albo go usuwa. Zwraca true, jeśli został dodany. */
    public boolean toggleItem(Item item) {
        String id = Registries.ITEM.getId(item).toString();
        boolean added;
        if (items.remove(id)) {
            added = false;
        } else {
            items.add(id);
            added = true;
        }
        cache = null;
        save();
        return added;
    }

    public void clearItems() {
        items.clear();
        cache = null;
        save();
    }
}
