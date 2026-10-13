package com.example.orangeclient.module.player;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.module.combat.TriggerBot;
import com.example.orangeclient.module.movement.Freecam;
import com.example.orangeclient.setting.BoolSetting;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BeaconBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CommandBlock;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.JigsawBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.StructureBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

/**
 * AntiTrap:
 *  1) Encje (gracze, moby, itemy w ramkach itd.) przestaja blokowac celownik:
 *     klikasz/kopiesz/stawiasz "przez" nie, prosto w blok za nimi.
 *     TriggerBot potrzebuje encji w celowniku, wiec gdy jest wlaczony, ta czesc sie nie wtraca.
 *  2) Z Twojego punktu widzenia (tylko po stronie klienta) znikaja obrazy, item framey
 *     (takze glow item framey) i wszystkie rodzaje minecartow.
 *  3) PPM na bloki z GUI (skrzynki, tabliczki, piece, crafting, hopper...) nic nie otwiera.
 *  4) Automatycznie niszczy pulapki pod Toba:
 *      - stoisz na plotku (takze gdy stoi na nim cobweb, w ktorym tkwisz) -> wyjmuje siekiere i niszczy PLOTEK,
 *        a jesli w tym miejscu jest cobweb, to kopie "przez" niego prosto w plotek (cobweb zostaje nietkniety),
 *      - tkwisz w cobwebie (albo jest tuz pod Toba) i nie ma pod nim plotka -> niszczy cobweb (wyjmuje na niego MIECZ, a bez miecza siekiere).
 *     Kopanie robi sam Minecraft (my tylko podstawiamy mu cel i wciskamy LPM), wiec postep, pakiety
 *     i animacje sa takie jak przy recznym kopaniu.
 */
public class AntiTrap extends Module {
    private final BoolSetting breakFence = add(new BoolSetting("BreakFence", true));
    private final BoolSetting breakWeb = add(new BoolSetting("BreakCobweb", true));
    /** Po zniszczeniu pulapki wraca do slotu, ktory trzymales wczesniej. */
    private final BoolSetting switchBack = add(new BoolSetting("SwitchBack", true));
    /** PPM na skrzynke, tabliczke, piec, crafting itp. nie otwiera GUI (z Shiftem + przedmiotem w rece stawiasz blok jak w vanilli). */
    private final BoolSetting noBlockGui = add(new BoolSetting("NoBlockGui", true));

    private int prevSlot = -1;
    private int autoSlot = -1;
    /** Czy w tym ticku podstawialismy kopanie (zeby potem przywrocic prawdziwy stan LPM). */
    private boolean forcing;
    /** Pulapka, ktora aktualnie kopiemy - GameRendererMixin podstawia ja jako cel celownika (patrz forcedHit). */
    private static volatile BlockHitResult forcedHit;

    public AntiTrap() {
        super("AntiTrap", Category.PLAYER);
    }

    @Override
    protected void onDisable() {
        stopBreaking(MinecraftClient.getInstance());
    }

    /** Na poczatku ticku, przed obsluga klikniec - wtedy vanilla zajmuje sie samym kopaniem. */
    @Override
    public void onPreTick(MinecraftClient mc) {
        BlockPos target = findTarget(mc);
        if (target == null) {
            stopBreaking(mc);
            return;
        }
        selectTool(mc, mc.world.getBlockState(target));
        forcing = true;
        mc.options.attackKey.setPressed(true);
        // Celownik na pulapke, nawet jesli cobweb jest "po drodze" (serwer nie sprawdza, czy cos zaslania blok).
        // Samo ustawienie mc.crosshairTarget tutaj nie wystarcza - vanilla przelicza celownik w tym samym ticku
        // (juz po START_CLIENT_TICK) i nadpisuje go cobwebem. Dlatego cel podstawia GameRendererMixin
        // w findCrosshairTarget, a my ustawiamy go tez od razu (na wypadek kolejnosci wywolan).
        forcedHit = new BlockHitResult(
                new Vec3d(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5),
                Direction.UP, target, false);
        mc.crosshairTarget = forcedHit;
    }

    /** Cel wymuszony przez AntiTrap (kopanie pulapki "przez" cobweb) albo null. Uzywane przez GameRendererMixin. */
    public static BlockHitResult forcedHit() {
        return forcedHit;
    }

    /** Pulapka do zniszczenia: najpierw plotek pod Toba (takze przez cobweb), potem cobweb. */
    private BlockPos findTarget(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return null;
        if (mc.currentScreen != null || Freecam.isActive()) return null;
        if (mc.player.isSpectator() || mc.player.isUsingItem()) return null;

        BlockPos feet = mc.player.getBlockPos();      // stojac na plotku (kolizja 1,5 bloku) jest to blok NAD plotkiem
        BlockPos under = feet.down();
        BlockState feetState = mc.world.getBlockState(feet);
        BlockState underState = mc.world.getBlockState(under);

        // plotek pod Toba - nawet gdy w Twoim bloku stoi cobweb (wtedy kopiemy przez niego)
        // Nie czekamy na ladowanie: wpadajac w cobweb nad plotkiem gracz jest juz w cobwebie (feet), a plotek
        // tuz pod nim, wiec kopiemy plotek od razu - bez warunku isOnGround().
        boolean inWeb = feetState.isOf(Blocks.COBWEB);
        if (breakFence.get() && underState.isIn(BlockTags.FENCES) && (mc.player.isOnGround() || inWeb)) return under;
        if (breakWeb.get() && inWeb) return feet;
        if (breakWeb.get() && underState.isOf(Blocks.COBWEB)) return under;
        return null;
    }

    /**
     * Wyjmuje narzedzie z paska: miecz na cobweba, siekiere na plotek
     * (jesli AutoTool jest wlaczony, to on decyduje o narzedziu).
     */
    private void selectTool(MinecraftClient mc, BlockState state) {
        AutoTool autoTool = ModuleManager.get(AutoTool.class);
        if (autoTool != null && autoTool.isEnabled()) return;

        PlayerInventory inv = mc.player.getInventory();
        int cur = inv.selectedSlot;
        int bestAxe = pickBest(inv, state, state.isOf(Blocks.COBWEB) ? ItemTags.SWORDS : ItemTags.AXES);
        // brak miecza na pasku -> cobweba tez da sie zniszczyc siekiera
        if (bestAxe == -1 && state.isOf(Blocks.COBWEB)) bestAxe = pickBest(inv, state, ItemTags.AXES);
        if (bestAxe != -1 && bestAxe != cur) {
            if (prevSlot == -1) prevSlot = cur;
            inv.selectedSlot = bestAxe;
            autoSlot = bestAxe;
        }
    }

    /** Slot paska z najszybszym narzedziem z danego tagu (albo -1). */
    private static int pickBest(PlayerInventory inv, BlockState state, TagKey<Item> tag) {
        int best = -1;
        float bestSpeed = 0f;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty() || !st.isIn(tag)) continue;
            if (st.isDamageable() && st.getMaxDamage() - st.getDamage() <= 1) continue;   // nie psujemy ostatniego uzycia
            float speed = st.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        return best;
    }

    private void stopBreaking(MinecraftClient mc) {
        forcedHit = null;
        if (forcing) {
            forcing = false;
            // KeyBinding.updatePressedStates() obsluguje tylko klawiature, nie przyciski myszy - LPM zostawal
            // "wcisniety" po zniszczeniu pulapki. Dlatego przywracamy prawdziwy stan recznie.
            if (mc != null) restoreAttackKey(mc);
        }
        if (prevSlot != -1 && mc != null && mc.player != null) {
            PlayerInventory inv = mc.player.getInventory();
            // wracamy tylko, jesli gracz sam nie zmienil slotu w miedzyczasie
            if (switchBack.get() && inv.selectedSlot == autoSlot) inv.selectedSlot = prevSlot;
        }
        prevSlot = -1;
        autoSlot = -1;
    }

    /** Ustawia attackKey na to, co faktycznie robi gracz (fizyczny stan klawisza/przycisku myszy). */
    private static void restoreAttackKey(MinecraftClient mc) {
        KeyBinding kb = mc.options.attackKey;
        if (mc.currentScreen != null) {
            kb.setPressed(false);
            return;
        }
        long handle = mc.getWindow().getHandle();
        InputUtil.Key k = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
        boolean down;
        if (k.getCategory() == InputUtil.Type.MOUSE) {
            down = GLFW.glfwGetMouseButton(handle, k.getCode()) == GLFW.GLFW_PRESS;
        } else if (k.getCode() == GLFW.GLFW_KEY_UNKNOWN) {
            down = false;
        } else {
            down = InputUtil.isKeyPressed(handle, k.getCode());
        }
        kb.setPressed(down);
    }

    /** Czy kliknieciu PPM w ten blok trzeba zablokowac (bo otworzyloby GUI). Uzywane przez ClientPlayerInteractionManagerMixin. */
    public static boolean blocksGui(PlayerEntity player, BlockHitResult hit) {
        AntiTrap m = ModuleManager.get(AntiTrap.class);
        if (m == null || !m.isEnabled() || !m.noBlockGui.get()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || hit == null) return false;
        // Shift + cos w rece: vanilla i tak pomija GUI bloku (stawianie blokow przy skrzynce) - nie wtracamy sie
        if (player.isSneaking() && !(player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty())) return false;
        return opensGui(mc.world, hit.getBlockPos());
    }

    private static boolean opensGui(World world, BlockPos pos) {
        BlockState st = world.getBlockState(pos);
        Block b = st.getBlock();
        if (b instanceof AbstractSignBlock || b instanceof EnderChestBlock || b instanceof CommandBlock
                || b instanceof StructureBlock || b instanceof JigsawBlock || b instanceof LecternBlock
                || b instanceof BeaconBlock || b instanceof CrafterBlock) return true;
        // skrzynki, beczki, shulkery, piece, hoppery, dispensery, crafting table, kowadla, enchanting itd.
        return st.createScreenHandlerFactory(world, pos) != null;
    }

    /** Czy encje maja przestac blokowac celownik. */
    public static boolean active() {
        AntiTrap m = ModuleManager.get(AntiTrap.class);
        if (m == null || !m.isEnabled()) return false;
        TriggerBot tb = ModuleManager.get(TriggerBot.class);
        return tb == null || !tb.isEnabled();
    }

    /** Czy dana encja ma byc niewidoczna (obraz, item frame, dowolny minecart). */
    public static boolean hides(Entity entity) {
        if (!(entity instanceof AbstractMinecartEntity
                || entity instanceof ItemFrameEntity      // obejmuje tez GlowItemFrameEntity
                || entity instanceof PaintingEntity)) return false;
        AntiTrap m = ModuleManager.get(AntiTrap.class);
        return m != null && m.isEnabled();
    }
}
