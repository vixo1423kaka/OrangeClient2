package com.example.orangeclient.module.combat;

import com.example.orangeclient.friend.Friends;
import com.example.orangeclient.mixin.LivingEntityAccessor;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.movement.Freecam;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.ColorSetting;
import com.example.orangeclient.setting.ModeSetting;
import com.example.orangeclient.setting.NumberSetting;
import com.example.orangeclient.util.RenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * KillAura z rozbudowanymi ustawieniami: wybor celow, sortowanie, tempo ataku (cooldown),
 * rotacje (Smooth / Snap), ruch (Follow), warunki ataku, lamanie tarczy i wizualizacja celu.
 */
public class KillAura extends Module {
    // ---------- Targeting ----------
    private final NumberSetting range = add(new NumberSetting("Range", 3.0, 1.0, 6.0, 0.1));
    /** Zasieg, gdy miedzy Toba a celem jest sciana (0 = nie bij przez sciany). */
    private final NumberSetting wallRange = add(new NumberSetting("WallRange", 0.0, 0.0, 6.0, 0.1));
    private final NumberSetting fov = add(new NumberSetting("FOV", 180, 10, 360, 5));
    private final ModeSetting sort = add(new ModeSetting("Sort", "Closest", "Closest", "Health", "Angle", "HurtTime", "Armor"));
    /** Trzyma obecny cel, dopoki zyje i jest w zasiegu (zamiast wybierac co tick od nowa). */
    private final BoolSetting keepTarget = add(new BoolSetting("KeepTarget", false));
    private final BoolSetting players = add(new BoolSetting("Players", true));
    private final BoolSetting hostiles = add(new BoolSetting("Hostiles", false));
    private final BoolSetting animals = add(new BoolSetting("Animals", false));
    /** Pozostale moby: wieśniacy, golemy, zwierzeta wodne itd. */
    private final BoolSetting others = add(new BoolSetting("Others", false));
    private final BoolSetting invisibles = add(new BoolSetting("Invisibles", true));
    /** Gracze bez zadnego elementu zbroi. */
    private final BoolSetting naked = add(new BoolSetting("Naked", true));
    private final BoolSetting teammates = add(new BoolSetting("Teammates", false));
    private final BoolSetting ignoreFriends = add(new BoolSetting("IgnoreFriends", true));

    // ---------- Tempo ataku ----------
    private final NumberSetting minCooldown = add(new NumberSetting("MinCooldown", 0.95, 0.1, 1.0, 0.05));
    /** Dodatkowe losowe opoznienie 0..Jitter ms po kazdym uderzeniu. */
    private final NumberSetting jitter = add(new NumberSetting("Jitter", 0, 0, 300, 10));
    /** Bije tylko, gdy cel ma hurtTime <= tej wartosci (10 = wylaczone). */
    private final NumberSetting hurtTimeLimit = add(new NumberSetting("HurtTimeLimit", 10, 0, 10, 1));

    // ---------- Rotacje ----------
    private final ModeSetting rotMode = add(new ModeSetting("Rotation", "Smooth", "Smooth", "Snap", "Off"));
    private final NumberSetting rotSpeed = add(new NumberSetting("RotSpeed", 12, 1, 40, 0.5));
    private final NumberSetting maxStep = add(new NumberSetting("MaxStep", 25, 1, 90, 1));
    private final NumberSetting randomness = add(new NumberSetting("Randomness", 0.6, 0.0, 3.0, 0.1));
    private final ModeSetting aimPoint = add(new ModeSetting("AimPoint", "Chest", "Chest", "Head", "Closest"));
    /**
     * Silent: rotacja do celu idzie TYLKO do serwera (pakiety ruchu + atak), a po stronie klienta nic nie widac -
     * kamera stoi w miejscu (model gracza tez, chyba ze wlaczysz SilentModel).
     * Wylaczone = kamera sama sie obraca do celu.
     */
    private final BoolSetting silent = add(new BoolSetting("Silent", true));
    /** Tylko przy Silent: pokazuje u Ciebie (3. osoba) model obrocony do celu. Domyslnie wylaczone - klient nic nie widzi. */
    private final BoolSetting silentModel = add(new BoolSetting("SilentModel", false));

    // ---------- Warunki ----------
    /**
     * Bije tylko wtedy, gdy uderzenie bedzie krytyczne (spadasz); na ziemi nie bije wcale.
     * Na ulamek ticka wylaczany jest sprint, bo vanilla nie daje critow podczas sprintu.
     */
    private final BoolSetting onlyCrits = add(new BoolSetting("OnlyCrits", false));
    private final BoolSetting pauseOnUse = add(new BoolSetting("PauseOnUse", true));
    private final BoolSetting onlyWeapon = add(new BoolSetting("OnlyWeapon", false));
    /** Gdy cel blokuje tarcza, a masz topor na hotbarze - przelacza na niego na czas ataku. */
    private final BoolSetting breakShield = add(new BoolSetting("BreakShield", false));
    private final BoolSetting swapBack = add(new BoolSetting("SwapBack", true));

    // ---------- Ruch ----------
    /**
     * Follow: sam podchodzi do celu i krazy wokol niego - zawsze w lewo, w odleglosci {@link #CIRCLE_RADIUS}.
     * (Nazwa to tylko "Follow", ale ruch to dokladnie to, co robilo dawne Circle.)
     */
    private final BoolSetting follow = add(new BoolSetting("Follow", false));
    /** Z jakiej odleglosci (do hitboxa) KillAura zaczyna isc za celem. */
    private final NumberSetting moveRange = add(new NumberSetting("MoveRange", 8.0, 3.0, 20.0, 0.5));
    /**
     * Skacze, gdy na drodze (Follow) jest blok wyzszy niz stopien. Razem z OnlyCrits skacze tez sam
     * z ziemi, gdy cel jest w zasiegu - bez tego nie ma jak zadac critu (crit = uderzenie podczas spadania).
     */
    private final BoolSetting autoJump = add(new BoolSetting("AutoJump", true));
    /** Stala odleglosc od srodka celu podczas krazenia. */
    private static final double CIRCLE_RADIUS = 1.0;

    // ---------- Wizualizacja ----------
    private final ModeSetting targetEsp = add(new ModeSetting("TargetESP", "Pulse", "Off", "Box", "Fill", "Ring", "Pulse", "Halo"));
    private Entity espAnimatedTarget;
    private long espAppearStart;
    private static final long ESP_APPEAR_MS = 240L;
    private float espRenderAlpha = 1f;

    private final ColorSetting espColor = add(new ColorSetting("TargetColor", 0xFF7A00, true));
    private final NumberSetting fillAlpha = add(new NumberSetting("FillAlpha", 60, 10, 200, 10));
    /** Czas jednego przejscia pierscienia (gora -> dol lub dol -> gora) w ms. */
    private final NumberSetting animationSpeed = add(new NumberSetting("AnimationSpeed", 1.0, 0.25, 3.0, 0.05));
    /** Dlugosc poswiaty za pierscieniem jako ulamek wysokosci celu. */
    private final NumberSetting espTrail = add(new NumberSetting("ESPTrail", 0.45, 0.1, 1.0, 0.05));
    private final NumberSetting espGlow = add(new NumberSetting("ESPGlow", 140, 20, 255, 10));
    private final NumberSetting espThickness = add(new NumberSetting("ESPThickness", 0.05, 0.01, 0.15, 0.01));

    private long lastHit;
    private long nextDelayMs;
    /** Oczekujący atak; bez dodatkowego opóźnienia, aby nie minąć okna krytyka. */
    private LivingEntity pendingAttackTarget;
    private long pendingAttackAt;
    private static final long ATTACK_START_DELAY_MS = 0L;
    private LivingEntity target;
    private double noiseYaw, noisePitch;
    private long nextNoise;

    // ---------- stan silent rotation ----------
    /** Rotacja, ktora widzi serwer (w trybie Silent). Utrzymywana miedzy tickami, zeby Smooth plynnie dochodzil do celu. */
    private float serverYaw, serverPitch;
    /** Czy w poprzednim ticku mielismy aktywna rotacje do celu. */
    private boolean rotating;
    /** Czy w tym ticku podmienilismy yaw/pitch gracza na czas wysylania pakietow (trzeba je potem przywrocic). */
    private boolean motionActive;
    private float savedYaw, savedPitch;
    /** Poprzednia rotacja serwera (do interpolacji modelu gracza miedzy tickami). */
    private float prevRotYaw, prevRotPitch;

    // ---------- plynne celowanie (liczone co KLATKE, jak w AimAssist) ----------
    /** Wirtualna rotacja dla trybu Silent - aktualizowana co klatke, a w pakiecie ruchu idzie jej najnowsza wartosc. */
    private float vYaw, vPitch;
    private boolean vValid;
    private long lastFrameNs;

    // ---------- stan ruchu ----------
    /** Czy w tym ticku nadpisalismy klawisze ruchu (trzeba je potem przywrocic do prawdziwego stanu). */
    private boolean moveOverride;

    public KillAura() {
        super("KillAura", Category.COMBAT);
    }

    /** Aktualny cel (do uzycia np. w HUD). */
    public LivingEntity getTarget() { return target; }

    @Override
    protected void onEnable() {
        lastHit = 0;
        nextDelayMs = 0;
        pendingAttackTarget = null;
        pendingAttackAt = 0L;
        target = null;
        rotating = false;
        vValid = false;
        lastFrameNs = 0;
    }

    @Override
    protected void onDisable() {
        pendingAttackTarget = null;
        pendingAttackAt = 0L;
        target = null;
        rotating = false;
        vValid = false;
        releaseMovement();
    }

    // ---------- ruch: Follow ----------

    /**
     * Przed odczytem inputu przez gracza ustawiamy klawisze ruchu tak, zeby krazyl wokol celu (Follow)
     * i - jesli wlaczone AutoJump - skakal przez przeszkody / pod crity. Kierunek liczymy w swiecie
     * i przeliczamy na WASD wzgledem prawdziwego yaw gracza (w trybie Silent kamera stoi w miejscu,
     * wiec to ten sam yaw, wg ktorego vanilla liczy ruch).
     */
    @Override
    public void onPreTick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (!canRun(mc, p)) { releaseMovement(); return; }

        boolean steer = false, jump = false;
        boolean fw = false, bk = false, lf = false, rt = false;
        double dirX = 0.0, dirZ = 0.0;

        LivingEntity mt = follow.get() ? findMoveTarget(mc, p) : null;
        if (mt != null) {
            steer = true;
            double dx = mt.getX() - p.getX();
            double dz = mt.getZ() - p.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            double rx = dist > 1.0E-4 ? dx / dist : 0.0;   // wersor w strone celu
            double rz = dist > 1.0E-4 ? dz / dist : 0.0;

            double err = dist - CIRCLE_RADIUS;
            double radial = MathHelper.clamp(err, -1.0, 1.0);        // dociaganie / odsuwanie do promienia
            double tangent = Math.abs(err) > 2.5 ? 0.0 : 1.0;        // daleko od okregu: tylko podejscie
            // styczna "w lewo" wzgledem celu = (rz, -rx)
            double mx = rx * radial + rz * tangent;
            double mz = rz * radial - rx * tangent;

            double len = Math.sqrt(mx * mx + mz * mz);
            if (len > 0.05) {
                dirX = mx / len;
                dirZ = mz / len;
                double yaw = Math.toRadians(silent.get() && rotating ? serverYaw : p.getYaw());
                double sin = Math.sin(yaw), cos = Math.cos(yaw);
                double f = dirX * -sin + dirZ * cos;          // skladowa "do przodu"
                double side = dirX * -cos + dirZ * -sin;      // skladowa "w prawo"
                fw = f > 0.3;
                bk = f < -0.3;
                rt = side > 0.3;
                lf = side < -0.3;
            }
        }

        if (p.isOnGround()) {
            // OnlyCrits nie steruje skokiem. Skakanie jest wylacznie zadaniem AutoJump.
            if (autoJump.get()) {
                if (steer && (dirX != 0.0 || dirZ != 0.0) && needsJump(mc, p, dirX, dirZ)) {
                    jump = true;                              // przeszkoda na drodze (Follow)
                } else if (!steer && target != null && manualJumpNeeded(mc, p)) {
                    jump = true;                              // przeszkoda na drodze przy recznym ruchu
                }
            }
        }

        if (!steer && !jump) { releaseMovement(); return; }

        KeyBinding.updatePressedStates();                     // start od prawdziwego stanu klawiszy
        GameOptions o = mc.options;
        if (steer) {
            o.forwardKey.setPressed(fw);
            o.backKey.setPressed(bk);
            o.rightKey.setPressed(rt);
            o.leftKey.setPressed(lf);
            if (fw) o.sprintKey.setPressed(true);
        }
        if (jump) {
            o.jumpKey.setPressed(true);
            forceJump(p);
        }
        moveOverride = true;
    }

    /**
     * Skok wprost, bez polegania na stanie klawisza (stan klawisza bywa nadpisywany przez inne moduly / vanille).
     * Odtwarza to, co robi vanilla przy skoku z ziemi: predkosc w gore + 10 tickow cooldownu.
     */
    private static void forceJump(PlayerEntity p) {
        if (!p.isOnGround() || p.isTouchingWater() || p.isInLava() || p.hasVehicle() || p.isClimbing()) return;
        LivingEntityAccessor acc = (LivingEntityAccessor) p;
        if (acc.getJumpingCooldown() > 0) return;
        acc.invokeJump();
        acc.setJumpingCooldown(10);
    }

    /** Czy przy RECZNYM ruchu (WASD) na drodze jest blok, ktory trzeba przeskoczyc. */
    private static boolean manualJumpNeeded(MinecraftClient mc, PlayerEntity p) {
        if (!(p instanceof net.minecraft.client.network.ClientPlayerEntity cp)) return false;
        float f = cp.input.movementForward, s = cp.input.movementSideways;
        if (f == 0f && s == 0f) return false;
        double yaw = Math.toRadians(p.getYaw());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double dx = s * cos - f * sin;
        double dz = f * cos + s * sin;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) return false;
        return needsJump(mc, p, dx / len, dz / len);
    }

    private void releaseMovement() {
        if (moveOverride) {
            KeyBinding.updatePressedStates();         // przywroc prawdziwy stan klawiszy
            moveOverride = false;
        }
    }

    /**
     * Czy w kierunku ruchu stoi przeszkoda, ktorej nie przejdziemy sami (wyzsza niz stopien 0.6),
     * a po wskoczeniu na nia (ok. 1 blok) jest miejsce na cale cialo. Sprawdzamy bloki z wyprzedzeniem,
     * a nie tylko horizontalCollision, bo ta flaga potrafi migac i spozniac sie o tick.
     */
    private static boolean needsJump(MinecraftClient mc, PlayerEntity p, double dirX, double dirZ) {
        Box b = p.getBoundingBox();
        double dx = dirX * 0.4, dz = dirZ * 0.4;
        Box wall = new Box(b.minX + dx, b.minY + 0.65, b.minZ + dz, b.maxX + dx, b.maxY, b.maxZ + dz);
        boolean blocked = p.horizontalCollision || mc.world.getBlockCollisions(p, wall).iterator().hasNext();
        if (!blocked) return false;
        Box landing = new Box(b.minX + dx, b.minY + 1.01, b.minZ + dz, b.maxX + dx, b.maxY + 1.01, b.maxZ + dz);
        return !mc.world.getBlockCollisions(p, landing).iterator().hasNext();
    }

    /** Cel do chodzenia: obecny cel ataku (jesli w zasiegu ruchu), inaczej najblizszy pasujacy. */
    private LivingEntity findMoveTarget(MinecraftClient mc, PlayerEntity p) {
        double reach = moveRange.get();
        Vec3d eye = p.getEyePos();
        if (target != null && target.isAlive() && !target.isRemoved() && matchesType(p, target)
                && distanceToBox(eye, target.getBoundingBox()) <= reach) {
            return target;
        }
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == p || !le.isAlive() || le.isRemoved()
                    || le.isSpectator() || !matchesType(p, le)) continue;
            double d = distanceToBox(eye, le.getBoundingBox());
            if (d <= reach && d < bestDist) {
                bestDist = d;
                best = le;
            }
        }
        return best;
    }

    /** Wolane z mixina na POCZATKU ClientPlayerEntity.tick() - ZANIM gracz policzy ruch.
     *  Rotacja serwera ustawiana jest tu, zeby tickMovement liczyl sie z jej uzyciem.
     *  Kamera zostaje zapamietana i wraca w postMotion. */
    public void tickRotation(MinecraftClient mc) {
        motionActive = false;
        PlayerEntity p = mc.player;
        if (!canRun(mc, p)) { target = null; rotating = false; vValid = false; return; }

        if (!(keepTarget.get() && target != null && isValid(mc, p, target))) {
            target = findTarget(mc, p);
        }
        if (target == null || rotMode.is("Off")) { rotating = false; vValid = false; return; }

        Vec3d eye = p.getEyePos();
        Vec3d point = pickPoint(eye, p.getRotationVec(1.0f), target.getBoundingBox());

        boolean sil = silent.get();
        savedYaw = p.getYaw();            // prawdziwa kamera (to, co widzi gracz)
        savedPitch = p.getPitch();
        float curYaw = (sil && rotating) ? serverYaw : savedYaw;
        float curPitch = (sil && rotating) ? serverPitch : savedPitch;
        prevRotYaw = curYaw;
        prevRotPitch = curPitch;

        // OnlyCrits nie zmienia stanu sprintu. Gracz moze dalej biec; serwer moze
        // wtedy nie przyznac bonusu krytycznego, ale sam atak nie powinien byc blokowany.

        float[] rot;
        if (rotMode.is("Smooth")) {
            if (sil) {
                if (!vValid) { vYaw = savedYaw; vPitch = savedPitch; vValid = true; }
                rot = new float[]{vYaw, vPitch};
            } else {
                rot = new float[]{savedYaw, savedPitch};   // kamera juz wygladzona przez smoothFrame
            }
        } else {
            rot = computeRotation(eye, point, curYaw, curPitch);
        }

        if (sil) {
            serverYaw = snapToGcd(rot[0]);
            serverPitch = snapToGcd(rot[1]);
        } else {
            if (!rotMode.is("Smooth")) {   // Snap: od razu przestaw kamere na cel
                savedYaw = p.getYaw() + MathHelper.wrapDegrees(rot[0] - p.getYaw());
                savedPitch = MathHelper.clamp(rot[1], -90f, 90f);
            }
            serverYaw = snapToGcd(savedYaw);
            serverPitch = snapToGcd(savedPitch);
        }

        p.setYaw(serverYaw);           // ruch (tickMovement) policzy sie z tej rotacji
        p.setPitch(serverPitch);
        motionActive = true;
        rotating = true;
    }

    /** Wolane z mixina na POCZATKU sendMovementPackets: tylko utrwalamy rotacje w pakiecie
     *  (gdyby cos w trakcie ticku ruszylo yaw gracza). */
    public void preMotion(MinecraftClient mc) {
        if (!motionActive) return;
        PlayerEntity p = mc.player;
        if (p == null) return;
        p.setYaw(serverYaw);
        p.setPitch(serverPitch);
    }

    /**
     * Wolane z mixina na KONCU sendMovementPackets: pakiet rotacji juz poszedl do serwera,
     * wiec dopiero teraz wysylamy atak (kolejnosc: look -> attack), a potem przywracamy prawdziwe katy.
     */
    public void postMotion(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null) { motionActive = false; return; }
        try {
            // Atak nie moze zalezec od tego, czy modul aktualnie obraca gracza.
            // Przy Rotation = Off nadal probujemy uderzyc, uzywajac zwyklej rotacji gracza
            // (lookingAt() wybierze ja, gdy rotating/Silent nie sa aktywne).
            if (isEnabled() && target != null) tryAttack(mc, p);
        } finally {
            if (motionActive) {
                p.setYaw(savedYaw);
                p.setPitch(savedPitch);
                p.prevYaw = savedYaw;
                p.prevPitch = savedPitch;
                motionActive = false;
            }
        }
    }

    private boolean canRun(MinecraftClient mc, PlayerEntity p) {
        if (p == null || mc.world == null || mc.interactionManager == null) return false;
        if (mc.currentScreen != null || Freecam.isActive() || p.isSpectator() || p.isDead()) return false;
        if (pauseOnUse.get() && p.isUsingItem()) return false;
        if (onlyWeapon.get()) {
            ItemStack held = p.getMainHandStack();
            if (!held.isIn(ItemTags.SWORDS) && !held.isIn(ItemTags.AXES)) return false;
        }
        return true;
    }

    private void tryAttack(MinecraftClient mc, PlayerEntity p) {
        LivingEntity currentTarget = target;
        if (currentTarget == null || currentTarget.isRemoved() || !currentTarget.isAlive()) {
            clearPendingAttack();
            return;
        }

        long now = System.currentTimeMillis();
        boolean hasPending = pendingAttackTarget == currentTarget;

        // Validate target/range/aim continuously, including while the delay is running.
        Vec3d eye = p.getEyePos();
        Box box = currentTarget.getBoundingBox();
        double reach = p.canSee(currentTarget) ? range.get() : wallRange.get();
        if (distanceToBox(eye, box) > reach || !lookingAt(p, currentTarget, reach)
                || currentTarget.hurtTime > hurtTimeLimit.get()) {
            clearPendingAttack();
            return;
        }

        // Schedule the attack as soon as target/aim/range/cooldown are valid. With OnlyCrits,
        // do not start the timer only after falling has begun: that consumes part of the
        // short critical window. After the timer expires, keep the request queued until
        // the next valid falling moment rather than cancelling it and missing the jump.
        if (!hasPending) {
            double minC = Math.min(1.0, minCooldown.get() + ThreadLocalRandom.current().nextDouble() * 0.04);
            if (p.getAttackCooldownProgress(0.5f) < minC) return;
            if (now - lastHit < nextDelayMs) return;

            pendingAttackTarget = currentTarget;
            pendingAttackAt = now + ATTACK_START_DELAY_MS;
        }

        if (now < pendingAttackAt) return;

        // Keep the queued attack until both the cooldown and (when enabled) the actual
        // falling window are valid. Never fall back to a normal hit while OnlyCrits is on.
        double minC = Math.min(1.0, minCooldown.get() + 0.04);
        if (p.getAttackCooldownProgress(0.5f) < minC || now - lastHit < nextDelayMs
                || (onlyCrits.get() && !isFallingForCrit(p))) {
            return;
        }

        PlayerInventory inv = p.getInventory();
        int prevSlot = inv.selectedSlot;
        if (breakShield.get() && currentTarget.isBlocking()) {
            int axe = findAxeSlot(inv);
            if (axe != -1) inv.selectedSlot = axe;
        }

        mc.interactionManager.attackEntity(p, currentTarget);
        p.swingHand(Hand.MAIN_HAND);

        if (swapBack.get() && inv.selectedSlot != prevSlot) inv.selectedSlot = prevSlot;

        lastHit = now;
        nextDelayMs = computeNextDelay();
        clearPendingAttack();
    }

    private void clearPendingAttack() {
        pendingAttackTarget = null;
        pendingAttackAt = 0L;
    }

    /**
     * Warunek OnlyCrits: gracz faktycznie opada. Nie opieramy go na fallDistance,
     * bo ta wartosc bywa jeszcze zerowa na poczatku opadania. Nie sprawdzamy tez
     * sprintu: sprint moze wplynac na to, czy serwer uzna cios za krytyczny, ale
     * nie moze calkowicie zatrzymywac wywolania ataku.
     */
    private static boolean isFallingForCrit(PlayerEntity p) {
        return !p.isOnGround() && p.getVelocity().y < -0.003
                && !p.isClimbing() && !p.isTouchingWater() && !p.isInLava()
                && !p.hasVehicle();
    }

    // ---------- rotacja modelu gracza (renderer) ----------

    /** Czy model wlasnego gracza ma byc rysowany z rotacja serwera (Silent + aktywny cel). */
    public boolean isRenderRotating() { return isEnabled() && silent.get() && silentModel.get() && rotating; }
    public float renderYaw(float tickDelta) {
        if (rotMode.is("Smooth") && vValid) return vYaw;       // aktualizowane co klatke - plynny model
        return MathHelper.lerpAngleDegrees(tickDelta, prevRotYaw, serverYaw);
    }
    public float renderPitch(float tickDelta) {
        if (rotMode.is("Smooth") && vValid) return vPitch;
        return MathHelper.lerp(tickDelta, prevRotPitch, serverPitch);
    }

    private long computeNextDelay() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long extra = jitter.get() > 0 ? rnd.nextInt((int) jitter.get() + 1) : 0;
        return extra;
    }

    /** Liczy docelowa rotacje {yaw, pitch} startujac z (curYaw, curPitch) - Snap od razu, Smooth krokami. */
    private void updateNoise() {
        long ms = System.currentTimeMillis();
        if (ms >= nextNoise) {
            double r = randomness.get();
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            noiseYaw = (rnd.nextDouble() - 0.5) * 2 * r;
            noisePitch = (rnd.nextDouble() - 0.5) * 2 * r;
            nextNoise = ms + 120 + rnd.nextInt(180);
        }
    }

    private float[] computeRotation(Vec3d eye, Vec3d point, float curYaw, float curPitch) {
        updateNoise();

        Vec3d d = point.subtract(eye);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0 + noiseYaw);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(d.y, horiz)) + noisePitch);
        targetPitch = MathHelper.clamp(targetPitch, -90f, 90f);

        float newYaw, newPitch;
        if (rotMode.is("Snap")) {
            // wrapDegrees wzgledem curYaw, zeby kat nie "skakal" o 360 miedzy pakietami
            newYaw = curYaw + MathHelper.wrapDegrees(targetYaw - curYaw);
            newPitch = targetPitch;
        } else {
            float dYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
            float dPitch = targetPitch - curPitch;
            float rs = (float)(rotSpeed.get() * (0.9 + ThreadLocalRandom.current().nextDouble() * 0.2));
            double factor = 1.0 - Math.exp(-rs * 0.05); // tick = 50 ms
            double step = maxStep.get();
            newYaw = curYaw + (float) MathHelper.clamp(dYaw * factor, -step, step);
            newPitch = curPitch + (float) MathHelper.clamp(dPitch * factor, -step, step);
        }
        newPitch = MathHelper.clamp(newPitch, -90f, 90f);
        return new float[]{newYaw, newPitch};
    }

    // ---------- wybor celu ----------

    private boolean isValid(MinecraftClient mc, PlayerEntity p, LivingEntity le) {
        if (le == p || !le.isAlive() || le.isSpectator() || le.isRemoved()) return false;
        if (!matchesType(p, le)) return false;

        boolean visible = p.canSee(le);
        double reach = visible ? range.get() : wallRange.get();
        return distanceToBox(p.getEyePos(), le.getBoundingBox()) <= reach;
    }

    private boolean matchesType(PlayerEntity p, LivingEntity le) {
        if (ignoreFriends.get() && Friends.isFriend(le)) return false;
        if (!invisibles.get() && le.isInvisible()) return false;
        if (!teammates.get() && le.isTeammate(p)) return false;

        if (le instanceof PlayerEntity pl) {
            if (!players.get()) return false;
            if (!naked.get() && isNaked(pl)) return false;
            return true;
        }
        if (le instanceof Monster) return hostiles.get();
        if (le instanceof AnimalEntity) return animals.get();
        if (le instanceof MobEntity) return others.get();
        return false;
    }

    private static boolean isNaked(PlayerEntity pl) {
        return pl.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                && pl.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                && pl.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                && pl.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private LivingEntity findTarget(MinecraftClient mc, PlayerEntity p) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f);
        double halfFov = fov.get() / 2.0;

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || !isValid(mc, p, le)) continue;

            Box box = le.getBoundingBox();
            Vec3d center = new Vec3d((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
            double angle = angleBetween(look, center.subtract(eye));
            if (angle > halfFov) continue;

            double score = switch (sort.get()) {
                case "Health" -> le.getHealth() + le.getAbsorptionAmount();
                case "Angle" -> angle;
                case "HurtTime" -> le.hurtTime;
                case "Armor" -> le.getArmor();
                default -> distanceToBox(eye, box);
            };
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    private static int findAxeSlot(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isIn(ItemTags.AXES)) return i;
        }
        return -1;
    }

    // ---------- pomocnicze ----------

    private boolean lookingAt(PlayerEntity p, LivingEntity t, double reach) {
        Vec3d eye = p.getEyePos();
        Vec3d dir = silent.get() && rotating ? p.getRotationVector(serverPitch, serverYaw) : p.getRotationVec(1.0f);
        Vec3d end = eye.add(dir.multiply(reach + 0.5));
        Box box = t.getBoundingBox().expand(0.1);
        return box.contains(eye) || box.raycast(eye, end).isPresent();
    }

    private Vec3d pickPoint(Vec3d eye, Vec3d look, Box box) {
        double cx = (box.minX + box.maxX) / 2, cz = (box.minZ + box.maxZ) / 2;
        double h = box.maxY - box.minY;
        switch (aimPoint.get()) {
            case "Head":
                return new Vec3d(cx, box.minY + h * 0.9, cz);
            case "Closest": {
                double dist = eye.distanceTo(new Vec3d(cx, (box.minY + box.maxY) / 2, cz));
                Vec3d onRay = eye.add(look.multiply(dist));
                double m = 0.08;
                return new Vec3d(
                        MathHelper.clamp(onRay.x, box.minX + m, box.maxX - m),
                        MathHelper.clamp(onRay.y, box.minY + m, box.maxY - m),
                        MathHelper.clamp(onRay.z, box.minZ + m, box.maxZ - m));
            }
            default:
                return new Vec3d(cx, box.minY + h * 0.65, cz);
        }
    }

    private static double angleBetween(Vec3d a, Vec3d b) {
        double la = a.length(), lb = b.length();
        if (la == 0 || lb == 0) return 0;
        return Math.toDegrees(Math.acos(MathHelper.clamp(a.dotProduct(b) / (la * lb), -1.0, 1.0)));
    }

    /** Krok rotacji vanilli zależny od sensitivity - rotacja serwera musi na nim leżeć. */
    private static float gcdStep() {
        float f = (float)(MinecraftClient.getInstance().options.getMouseSensitivity().getValue() * 0.6 + 0.2);
        return f * f * f * 1.2f;
    }

    private static float snapToGcd(float angle) {
        float g = gcdStep();
        return g < 1.0E-4f ? angle : angle - (angle % g);
    }

    private static double distanceToBox(Vec3d p, Box b) {
        double dx = Math.max(Math.max(b.minX - p.x, 0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---------- wizualizacja celu ----------

    private static final int RING_SEGMENTS = 64;

    /**
     * Plynne celowanie jak w AimAssist: rotacja jest przesuwana co KLATKE (nie co tick = 20 razy/s),
     * z wygladzaniem niezaleznym od FPS. Dzieki temu kamera (Silent wylaczony) nie "rwie" -
     * a przy Silent serwer dostaje w kazdym ticku najswiezsza, plynnie dochodzaca rotacje.
     */
    private void smoothFrame(WorldRenderContext ctx, MinecraftClient mc) {
        long now = System.nanoTime();
        double dt = lastFrameNs == 0 ? 0.016 : (now - lastFrameNs) / 1_000_000_000.0;
        lastFrameNs = now;
        dt = Math.min(dt, 0.1);

        PlayerEntity p = mc.player;
        LivingEntity t = target;
        if (!rotating || t == null || !rotMode.is("Smooth") || !canRun(mc, p)) return;
        if (!t.isAlive() || t.isRemoved()) return;

        float td = ctx.tickCounter().getTickDelta(false);
        Vec3d eye = p.getCameraPosVec(td);
        Box box = t.getBoundingBox().offset(t.getLerpedPos(td).subtract(t.getPos()));
        Vec3d point = pickPoint(eye, p.getRotationVec(td), box);

        boolean sil = silent.get();
        if (sil && !vValid) { vYaw = p.getYaw(); vPitch = p.getPitch(); vValid = true; }
        float curYaw = sil ? vYaw : p.getYaw();
        float curPitch = sil ? vPitch : p.getPitch();

        // KLUCZ: jesli promien patrzenia juz trafia w hitbox, przestan krecic -
        // gracz przestalby ruszac mysza, wiec rotacja ma stanac w miejscu.
        Vec3d dirNow = p.getRotationVector(curPitch, curYaw);
        Vec3d rayEnd = eye.add(dirNow.multiply(6.0));
        if (t.getBoundingBox().expand(0.05).raycast(eye, rayEnd).isPresent()) {
            return;   // rotacja zostaje stabilna -> serwer widzi "rece"
        }

        updateNoise();
        Vec3d d = point.subtract(eye);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0 + noiseYaw);
        float targetPitch = MathHelper.clamp((float) (-Math.toDegrees(Math.atan2(d.y, horiz)) + noisePitch), -90f, 90f);

                float dYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
        float dPitch = targetPitch - curPitch;

        float rs = (float)(rotSpeed.get() * (0.9 + ThreadLocalRandom.current().nextDouble() * 0.2));
        double factor = 1.0 - Math.exp(-rs * dt);
        double step = maxStep.get() * dt * 20.0;
        double moveYaw = MathHelper.clamp(dYaw * factor, -step, step);
        double movePitch = MathHelper.clamp(dPitch * factor, -step, step);

        // szum na DELCIE, nie na kacie
        if (Math.abs(moveYaw) > 0.01)   moveYaw   += (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.15;
        if (Math.abs(movePitch) > 0.01) movePitch += (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.15;

        // kwantyzuj DELTE do siatki GCD
        float g = gcdStep();
        if (g > 1.0E-4f) {
            moveYaw   = Math.round(moveYaw / g) * g;
            movePitch = Math.round(movePitch / g) * g;
        }

        if (Math.abs(moveYaw) < 0.001 && Math.abs(movePitch) < 0.001) return;

        if (sil) {
            vYaw += (float) moveYaw;
            vPitch = MathHelper.clamp(vPitch + (float) movePitch, -90f, 90f);
        } else {
            p.changeLookDirection(moveYaw / 0.15, movePitch / 0.15);
        }
    }

    @Override
    public void onWorldRender(WorldRenderContext ctx, MinecraftClient mc) {
        smoothFrame(ctx, mc);
        if (target == null || !target.isAlive() || targetEsp.is("Off")) {
            espAnimatedTarget = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (espAnimatedTarget != target) {
            espAnimatedTarget = target;
            espAppearStart = now;
        }
        float appear = Math.min(1f, (now - espAppearStart) * (float) animationSpeed.get() / (float) ESP_APPEAR_MS);
        // Ease-out cubic: ESP fades/scales in instead of popping into view.
        float appearEase = 1f - (float) Math.pow(1f - appear, 3);
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Matrix4f m = ms.peek().getPositionMatrix();
        Vec3d cam = ctx.camera().getPos();
        float td = ctx.tickCounter().getTickDelta(false);

        Vec3d lerped = target.getLerpedPos(td);
        int rgb = espColor.rgb();
        espRenderAlpha = appearEase;

        if (targetEsp.is("Ring")) {
            renderRing(m, cam, lerped, rgb);
            return;
        }

        Box rawBox = target.getBoundingBox().offset(lerped.subtract(target.getPos()));
        double scale = 0.88 + 0.12 * appearEase;
        Vec3d boxCenter = rawBox.getCenter();
        Box box = new Box(
                boxCenter.x + (rawBox.minX - boxCenter.x) * scale,
                rawBox.minY,
                boxCenter.z + (rawBox.minZ - boxCenter.z) * scale,
                boxCenter.x + (rawBox.maxX - boxCenter.x) * scale,
                rawBox.minY + (rawBox.maxY - rawBox.minY) * scale,
                boxCenter.z + (rawBox.maxZ - boxCenter.z) * scale);
        if (targetEsp.is("Pulse")) {
            float pulse = 0.68f + 0.32f * (float) Math.sin(animTime() / 180.0);
            int alpha = Math.max(30, Math.min(255, (int) Math.round(espGlow.get() * pulse)));
            BufferBuilder b = RenderUtil.begin((float) Math.max(1.0, espThickness.get() * 35.0));
            RenderUtil.box(b, m, cam, box.expand(0.035 + 0.025 * pulse), (((int) (alpha * espRenderAlpha)) << 24) | rgb);
            RenderUtil.end(b);
            BufferBuilder f = RenderUtil.beginFill();
            RenderUtil.fillBox(f, m, cam, box, (((int) (Math.round(fillAlpha.get() * 0.45f * pulse) * espRenderAlpha)) << 24) | rgb);
            RenderUtil.endFill(f);
            return;
        }
        if (targetEsp.is("Halo")) {
            renderHalo(m, cam, lerped, rgb);
            return;
        }
        if (targetEsp.is("Fill")) {
            BufferBuilder f = RenderUtil.beginFill();
            RenderUtil.fillBox(f, m, cam, box, ((int) (fillAlpha.get() * espRenderAlpha) << 24) | rgb);
            RenderUtil.endFill(f);
        }
        if (targetEsp.is("Box")) {
            int glowAlpha = (int) (espGlow.get() * 0.45f * espRenderAlpha);
            BufferBuilder glow = RenderUtil.begin((float) Math.max(1.0, espThickness.get() * 35.0));
            RenderUtil.box(glow, m, cam, box.expand(0.045), (glowAlpha << 24) | rgb);
            RenderUtil.end(glow);
            BufferBuilder b = RenderUtil.begin((float) Math.max(1.0, espThickness.get() * 35.0));
            RenderUtil.box(b, m, cam, box, (((int) (255 * espRenderAlpha)) << 24) | rgb);
            RenderUtil.end(b);
        }
    }

    /** Wspolny zegar animacji, sterowany ustawieniem AnimationSpeed. */
    private double animTime() {
        return System.currentTimeMillis() * animationSpeed.get();
    }

    /** Jeden grubszy, lekko pochylony okrag nad glowa celu, obracajacy sie jak moneta. */
    private void renderHalo(Matrix4f m, Vec3d cam, Vec3d pos, int rgb) {
        double radius = Math.max(0.28, target.getWidth() * 0.78);
        double time = animTime() / 1000.0;
        double y = pos.y + target.getHeight() + 0.20 + Math.sin(time * 2.2) * 0.025;
        double spin = time * 2.8;
        double tilt = Math.toRadians(62.0);

        // Zamiast polegac na szerokosci linii OpenGL (ktora czesc sterownikow ignoruje),
        // budujemy widoczna grubosc z kilku blisko siebie polozonych obwodow.
        double thickness = espThickness.get();
        double coreSpread = 0.012 + thickness * 0.72;
        double glowSpread = coreSpread * 2.4;
        int glowStrength = (int) Math.max(0, Math.min(255, espGlow.get()));
        int color = rgb & 0x00FFFFFF;

        // ESPGlow steruje widocznoscia poswiaty, a ESPThickness jej szerokoscia.
        for (int layer = 4; layer >= 1; layer--) {
            double offset = glowSpread * layer / 4.0;
            int alpha = (int) (glowStrength * (0.055 + 0.025 * (5 - layer)) * espRenderAlpha);
            if (alpha <= 0) continue;
            BufferBuilder glow = RenderUtil.begin(1.0f);
            drawHaloLoop(glow, m, cam, pos, y, radius + offset, spin, tilt, (alpha << 24) | color);
            drawHaloLoop(glow, m, cam, pos, y, Math.max(0.05, radius - offset), spin, tilt, (alpha << 24) | color);
            RenderUtil.end(glow);
        }

        // Rdzen ma kilka obwodow; zmiana ESPThickness wyraznie poszerza widoczny pas.
        int layers = 7;
        for (int layer = -layers; layer <= layers; layer++) {
            double offset = coreSpread * layer / layers;
            int alpha = (int) ((layer == 0 ? 255 : 210 - Math.abs(layer) * 12) * espRenderAlpha);
            BufferBuilder edge = RenderUtil.begin(1.0f);
            drawHaloLoop(edge, m, cam, pos, y, Math.max(0.05, radius + offset), spin, tilt,
                    (alpha << 24) | color);
            RenderUtil.end(edge);
        }
    }

    private void drawHaloLoop(BufferBuilder b, Matrix4f m, Vec3d cam, Vec3d pos, double y,
                              double radius, double spin, double tilt, int argb) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a1 = Math.PI * 2 * i / RING_SEGMENTS;
            double a2 = Math.PI * 2 * (i + 1) / RING_SEGMENTS;
            Vec3d p1 = haloPoint(pos, y, radius, a1, spin, tilt);
            Vec3d p2 = haloPoint(pos, y, radius, a2, spin, tilt);
            RenderUtil.line(b, m, cam, p1, p2, argb);
        }
    }

    /** Punkt na okręgu, którego płaszczyzna jest pochylona i obraca się wokół osi pionowej. */
    private Vec3d haloPoint(Vec3d pos, double y, double radius, double angle, double spin, double tilt) {
        double localX = Math.cos(angle) * radius;
        double localY = Math.sin(angle) * radius;
        double tiltedY = localY * Math.cos(tilt);
        double tiltedSide = localY * Math.sin(tilt);
        double x = localX * Math.cos(spin) + tiltedSide * Math.sin(spin);
        double z = -localX * Math.sin(spin) + tiltedSide * Math.cos(spin);
        return new Vec3d(pos.x + x, y + tiltedY, pos.z + z);
    }

    /**
     * Pierscien jak "scanner": plynnie jedzie po calej wysokosci celu w gore i w dol.
     * Za nim ciagnie sie poswiata - pas, ktorego przezroczystosc spada do zera.
     */
    private void renderRing(Matrix4f m, Vec3d cam, Vec3d pos, int rgb) {
        double height = target.getHeight();
        double radius = target.getWidth() / 2.0 + 0.2;

        // faza 0..2: 0..1 = w gore, 1..2 = w dol; easing sinusem, zeby zwalnial na koncach
        double period = 1200.0 / animationSpeed.get();
        double phase = (System.currentTimeMillis() % (long) (period * 2)) / period;
        boolean up = phase <= 1.0;
        double lin = up ? phase : 2.0 - phase;
        double eased = 0.5 - 0.5 * Math.cos(Math.PI * lin);

        double y = pos.y + height * eased;
        double trailLen = height * espTrail.get();
        double yTrail = up ? Math.max(pos.y, y - trailLen) : Math.min(pos.y + height, y + trailLen);

        int glowA = (int) (espGlow.get() * espRenderAlpha);
        int cStrong = (glowA << 24) | rgb;     // poswiata przy pierscieniu
        int cFade = ((int) (255 * espRenderAlpha) << 24) | rgb;                       // alpha 0 na koncu ogona
        double half = 0.025;                   // zachowany oryginalny wyglad Ring
        int cRing = ((int) (255 * espRenderAlpha) << 24) | rgb;
        int cRingEdge = rgb;

        BufferBuilder b = RenderUtil.beginFill();
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a1 = Math.PI * 2 * i / RING_SEGMENTS;
            double a2 = Math.PI * 2 * (i + 1) / RING_SEGMENTS;
            float x1 = (float) (pos.x + Math.cos(a1) * radius - cam.x);
            float z1 = (float) (pos.z + Math.sin(a1) * radius - cam.z);
            float x2 = (float) (pos.x + Math.cos(a2) * radius - cam.x);
            float z2 = (float) (pos.z + Math.sin(a2) * radius - cam.z);

            // ogon / poswiata
            float yr = (float) (y - cam.y), yt = (float) (yTrail - cam.y);
            b.vertex(m, x1, yr, z1).color(cStrong);
            b.vertex(m, x2, yr, z2).color(cStrong);
            b.vertex(m, x2, yt, z2).color(cFade);
            b.vertex(m, x1, yt, z1).color(cFade);

            // sam pierscien: jasny pas z miekkim brzegiem nad i pod
            float yHi = (float) (y + half - cam.y), yLo = (float) (y - half - cam.y);
            b.vertex(m, x1, yHi, z1).color(cRing);
            b.vertex(m, x2, yHi, z2).color(cRing);
            b.vertex(m, x2, yLo, z2).color(cRing);
            b.vertex(m, x1, yLo, z1).color(cRing);

            // delikatna otoczka powyzej pierscienia (po stronie przeciwnej do ogona)
            float yEdge = (float) (y + (up ? 1 : -1) * 0.07 - cam.y);
            float yBase = up ? yHi : yLo;
            b.vertex(m, x1, yBase, z1).color(0x80000000 | rgb);
            b.vertex(m, x2, yBase, z2).color(0x80000000 | rgb);
            b.vertex(m, x2, yEdge, z2).color(cRingEdge);
            b.vertex(m, x1, yEdge, z1).color(cRingEdge);
        }
        RenderUtil.endFill(b);


    }
}
