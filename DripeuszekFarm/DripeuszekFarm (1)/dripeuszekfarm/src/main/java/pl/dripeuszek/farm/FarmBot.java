package pl.dripeuszek.farm;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarrotsBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class FarmBot {
    private static final double REACH = 4.2;
    private static final int SCAN_INTERVAL = 20;
    private static final int Y_RANGE = 4;

    // tryby: 0 = zbieranie, 1 = sadzenie, 2 = kopanie (motyka)
    @SuppressWarnings("unchecked")
    private static final List<BlockPos>[] lists = new List[]{new ArrayList<BlockPos>(), new ArrayList<BlockPos>(), new ArrayList<BlockPos>()};
    private static final Set<BlockPos> blacklist = new HashSet<>();

    private static int scanTimer = 0;
    private static int cooldown = 0;
    private static boolean walking = false;
    private static BlockPos walkTarget = null;
    private static int walkTicks = 0;
    private static BlockPos lastActed = null;
    private static int acts = 0;

    // odkładanie do skrzyni
    private static long tickCount = 0;
    private static boolean depositing = false;
    private static long chestBlockedUntil = 0;
    private static int depWalkTicks = 0, openTries = 0, chestFails = 0, lastTotal = -1, chestCd = 0;
    private static boolean selectingChest = false;

    // zaznaczanie stref
    private static Zone selecting = null;
    private static String selectingName = "";
    private static int selStep = 0;

    public static void reset(MinecraftClient mc) {
        for (List<BlockPos> l : lists) l.clear();
        blacklist.clear();
        scanTimer = 0;
        cooldown = 0;
        walkTarget = null;
        walkTicks = 0;
        lastActed = null;
        acts = 0;
        depositing = false;
        chestBlockedUntil = 0;
        depWalkTicks = 0;
        openTries = 0;
        chestFails = 0;
        lastTotal = -1;
        chestCd = 0;
        stopWalking(mc);
    }

    // ---------- zaznaczanie ----------

    public static void startSelect(MinecraftClient mc, Zone zone, String name) {
        selecting = zone;
        selectingName = name;
        selStep = 0;
        mc.setScreen(null);
        msg(mc, "Zaznaczanie (" + name + "): spójrz na blok i wciśnij ENTER – punkt A");
    }

    public static void startSelectChest(MinecraftClient mc) {
        selecting = null;
        selectingChest = true;
        mc.setScreen(null);
        msg(mc, "Zaznaczanie skrzyni: spójrz na skrzynię i wciśnij ENTER");
    }

    public static void cancelSelect() {
        selecting = null;
        selectingChest = false;
    }

    public static void confirm(MinecraftClient mc) {
        if ((selecting == null && !selectingChest) || mc.player == null) return;
        HitResult hr = mc.player.raycast(100.0, 0.0f, false);
        if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) {
            msg(mc, "\u00a7cNie patrzysz na żaden blok");
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        if (selectingChest) {
            FarmConfig.chest = pos;
            selectingChest = false;
            FarmConfig.save();
            reset(mc);
            mc.setScreen(new FarmScreen());
            return;
        }
        if (selStep == 0) {
            selecting.a = pos;
            selecting.b = null;
            selStep = 1;
            msg(mc, "Punkt A ustawiony. Teraz spójrz na przeciwległy róg i wciśnij ENTER – punkt B");
        } else {
            selecting.b = pos;
            selecting = null;
            FarmConfig.save();
            reset(mc);
            mc.setScreen(new FarmScreen());
        }
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), true);
    }

    // ---------- główna pętla ----------

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        tickCount++;
        if (!FarmConfig.enabled || p == null || mc.world == null || mc.interactionManager == null) {
            stopWalking(mc);
            return;
        }
        if (mc.currentScreen != null) {
            if (depositing && mc.currentScreen instanceof GenericContainerScreen gcs) handleChest(mc, p, gcs);
            else stopWalking(mc);
            return;
        }
        ClientWorld w = mc.world;

        if (cooldown > 0) cooldown--;
        if (depositing || depositNeeded(p)) {
            if (deposit(mc, p, w)) return;
        }
        if (scanTimer-- <= 0) {
            scan(mc);
            scanTimer = SCAN_INTERVAL;
        }

        boolean haveCarrot = has(p, s -> s.isOf(Items.CARROT));
        boolean haveHoe = has(p, s -> s.getItem() instanceof HoeItem);
        Vec3d eye = p.getEyePos();

        BlockPos best = null;
        int bestMode = -1;
        double bestD = Double.MAX_VALUE;

        for (int m = 0; m < 3; m++) {
            if (m == 1 && !haveCarrot) continue;
            if (m == 2 && !haveHoe) continue;
            final int mode = m;
            lists[m].removeIf(pos -> !valid(mode, pos, w));
            for (BlockPos pos : lists[m]) {
                if (blacklist.contains(pos)) continue;
                double d = eye.squaredDistanceTo(aimPoint(m, pos));
                if (!FarmConfig.walk && d > REACH * REACH) continue;
                if (d < bestD) {
                    bestD = d;
                    best = pos;
                    bestMode = m;
                }
            }
            if (best != null) break; // zbieranie > sadzenie > kopanie
        }

        if (best == null) {
            stopWalking(mc);
            return;
        }

        Vec3d aim = aimPoint(bestMode, best);
        if (eye.distanceTo(aim) > REACH) {
            if (!best.equals(walkTarget)) {
                walkTarget = best;
                walkTicks = 0;
            }
            if (++walkTicks > 200) { // utknął – olewamy ten cel
                blacklist.add(best);
                walkTicks = 0;
                stopWalking(mc);
                return;
            }
            walkTo(mc, p, aim);
            return;
        }

        stopWalking(mc);
        walkTicks = 0;
        if (cooldown > 0) return;

        if (best.equals(lastActed)) acts++;
        else {
            lastActed = best;
            acts = 1;
        }
        if (acts > 8) { // serwer nie przyjmuje akcji – olewamy
            blacklist.add(best);
            acts = 0;
            return;
        }

        if (FarmConfig.rotate) look(p, eye, aim);

        switch (bestMode) {
            case 0 -> {
                mc.interactionManager.attackBlock(best, Direction.UP);
                p.swingHand(Hand.MAIN_HAND);
            }
            case 1 -> {
                if (!selectItem(mc, p, s -> s.isOf(Items.CARROT))) return;
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(aim, Direction.UP, best, false));
                p.swingHand(Hand.MAIN_HAND);
            }
            default -> {
                if (!selectItem(mc, p, s -> s.getItem() instanceof HoeItem)) return;
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(aim, Direction.UP, best, false));
                p.swingHand(Hand.MAIN_HAND);
            }
        }
        cooldown = FarmConfig.delay;
    }

    // ---------- odkładanie do skrzyni ----------

    private static int countCarrots(ClientPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.CARROT)) n += s.getCount();
        }
        return n;
    }

    private static boolean hasMovable(ClientPlayerEntity p, int total) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.CARROT) && total - s.getCount() >= FarmConfig.keep) return true;
        }
        return false;
    }

    private static boolean depositNeeded(ClientPlayerEntity p) {
        if (FarmConfig.chest == null || tickCount < chestBlockedUntil) return false;
        int total = countCarrots(p);
        if (total - FarmConfig.keep >= 64) return true;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getStack(i).isEmpty()) return false; // jest wolne miejsce
        }
        return hasMovable(p, total); // ekwipunek pełny
    }

    /** Idzie do skrzyni i ją otwiera. Zwraca true, gdy zajęło ten tick. */
    private static boolean deposit(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w) {
        BlockPos c = FarmConfig.chest;
        depositing = true;
        if (w.getBlockState(c).isAir()) {
            abortDeposit(mc, "\u00a7cW zaznaczonym miejscu nie ma skrzyni");
            return false;
        }
        Vec3d eye = p.getEyePos();
        Vec3d center = Vec3d.ofCenter(c);
        if (eye.distanceTo(center) > REACH) {
            if (++depWalkTicks > 300) {
                abortDeposit(mc, "\u00a7cNie mogę dojść do skrzyni");
                return false;
            }
            walkTo(mc, p, center);
            return true;
        }
        stopWalking(mc);
        depWalkTicks = 0;
        if (cooldown > 0) return true;
        if (++openTries > 6) {
            abortDeposit(mc, "\u00a7cNie mogę otworzyć skrzyni");
            return false;
        }
        Vec3d top = Vec3d.of(c).add(0.5, 1.0, 0.5);
        look(p, eye, top);
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(top, Direction.UP, c, false));
        p.swingHand(Hand.MAIN_HAND);
        cooldown = 10;
        return true;
    }

    private static void abortDeposit(MinecraftClient mc, String message) {
        depositing = false;
        openTries = 0;
        depWalkTicks = 0;
        chestBlockedUntil = tickCount + 600; // 30 s przerwy
        stopWalking(mc);
        msg(mc, message);
    }

    private static void handleChest(MinecraftClient mc, ClientPlayerEntity p, GenericContainerScreen gcs) {
        openTries = 0;
        if (chestCd > 0) {
            chestCd--;
            return;
        }
        GenericContainerScreenHandler h = gcs.getScreenHandler();
        int chestSlots = h.getRows() * 9;
        int total = countCarrots(p);
        if (total == lastTotal) chestFails++;
        else chestFails = 0;
        lastTotal = total;
        if (chestFails >= 6) {
            finishDeposit(mc, p, true);
            return;
        }
        for (int i = chestSlots; i < h.slots.size(); i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (s.isOf(Items.CARROT) && total - s.getCount() >= FarmConfig.keep) {
                mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, p);
                chestCd = Math.max(1, FarmConfig.delay);
                return;
            }
        }
        finishDeposit(mc, p, false);
    }

    private static void finishDeposit(MinecraftClient mc, ClientPlayerEntity p, boolean chestFull) {
        p.closeHandledScreen();
        depositing = false;
        chestFails = 0;
        lastTotal = -1;
        chestCd = 0;
        if (chestFull) {
            chestBlockedUntil = tickCount + 1200; // 60 s
            msg(mc, "\u00a7cSkrzynia pełna – wracam do farmienia, spróbuję za minutę");
        } else {
            msg(mc, "\u00a7aMarchewki odłożone do skrzyni");
        }
    }

    // ---------- skanowanie ----------

    private static void scan(MinecraftClient mc) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        for (List<BlockPos> l : lists) l.clear();

        BlockPos o = p.getBlockPos();
        int r = FarmConfig.radius;
        long r2 = (long) r * r;

        Zone hz = FarmConfig.harvest;
        Zone pz = FarmConfig.plant.isSet() ? FarmConfig.plant : FarmConfig.harvest;
        Zone tz = FarmConfig.till;

        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = o.getX() - r; x <= o.getX() + r; x++) {
            for (int z = o.getZ() - r; z <= o.getZ() + r; z++) {
                long dx = x - o.getX(), dz = z - o.getZ();
                if (dx * dx + dz * dz > r2) continue;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = o.getY() - Y_RANGE; y <= o.getY() + Y_RANGE; y++) {
                    m.set(x, y, z);
                    BlockState s = w.getBlockState(m);
                    Block b = s.getBlock();
                    if (b instanceof CarrotsBlock c) {
                        if (c.isMature(s) && (!hz.isSet() || hz.contains(m))) lists[0].add(m.toImmutable());
                    } else if (b == Blocks.FARMLAND) {
                        if (w.getBlockState(m.up()).isAir() && (!pz.isSet() || pz.contains(m))) lists[1].add(m.toImmutable());
                    } else if (tz.isSet() && tz.contains(m) && (b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.DIRT_PATH)) {
                        if (w.getBlockState(m.up()).isAir()) lists[2].add(m.toImmutable());
                    }
                }
            }
        }
    }

    private static boolean valid(int mode, BlockPos pos, ClientWorld w) {
        BlockState s = w.getBlockState(pos);
        return switch (mode) {
            case 0 -> s.getBlock() instanceof CarrotsBlock c && c.isMature(s);
            case 1 -> s.isOf(Blocks.FARMLAND) && w.getBlockState(pos.up()).isAir();
            default -> (s.isOf(Blocks.DIRT) || s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.DIRT_PATH))
                    && w.getBlockState(pos.up()).isAir();
        };
    }

    private static Vec3d aimPoint(int mode, BlockPos pos) {
        return mode == 0 ? Vec3d.ofCenter(pos) : Vec3d.of(pos).add(0.5, 1.0, 0.5);
    }

    // ---------- ruch / celowanie ----------

    private static void look(ClientPlayerEntity p, Vec3d eye, Vec3d aim) {
        double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setPitch((float) -Math.toDegrees(Math.atan2(dy, h)));
    }

    private static void walkTo(MinecraftClient mc, ClientPlayerEntity p, Vec3d aim) {
        double dx = aim.x - p.getX(), dz = aim.z - p.getZ();
        p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setPitch(20.0f);
        mc.options.forwardKey.setPressed(true);
        mc.options.jumpKey.setPressed(p.horizontalCollision && p.isOnGround());
        walking = true;
    }

    private static void stopWalking(MinecraftClient mc) {
        if (walking && mc != null && mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
        }
        walking = false;
    }

    // ---------- ekwipunek ----------

    private static boolean has(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (pred.test(inv.getStack(i))) return true;
        }
        return false;
    }

    /** Wybiera przedmiot na pasku; jeśli jest tylko w plecaku, zamienia go na aktualny slot (wtedy zwraca false na ten tick). */
    private static boolean selectItem(MinecraftClient mc, ClientPlayerEntity p, Predicate<ItemStack> pred) {
        PlayerInventory inv = p.getInventory();
        if (pred.test(inv.getStack(inv.selectedSlot))) return true;
        for (int i = 0; i < 9; i++) {
            if (pred.test(inv.getStack(i))) {
                inv.selectedSlot = i;
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (pred.test(inv.getStack(i))) {
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, inv.selectedSlot, SlotActionType.SWAP, p);
                return false;
            }
        }
        return false;
    }
}
