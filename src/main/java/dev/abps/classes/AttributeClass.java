package dev.abps.classes;

import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Tasks;
import dev.abps.util.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Base for every attribute. Hooks do nothing unless a class overrides them.
 * Ability numbers: 1-4 are normal abilities, 5 is the charged ultimate.
 */
public abstract class AttributeClass {

    public static final int ABILITIES = 4;
    public static final int ULTIMATE = 5;

    /** Why the last ability didn't go off. Used by debug mode. */
    public String lastFail;

    // ---- Info ----
    public abstract String id();

    public abstract String name();

    /** Main color as #RRGGBB. */
    public abstract String color();

    /** Second color for gradients. */
    public abstract String color2();

    public abstract Item icon();

    public abstract String symbol();

    public abstract String tagline();

    public abstract List<String> passives(int level);

    public abstract List<String> negatives();

    public abstract String mastery();

    /** Name of ability 1-4, or 5 for the ultimate. */
    public abstract String abilityName(int idx);

    public abstract String abilityDesc(int idx, int level);

    /** Cooldown in seconds at level 1, for abilities 1-4. */
    public abstract double baseCooldown(int idx);

    public int rgb() {
        return Text.parseColor(color());
    }

    public int rgb2() {
        return Text.parseColor(color2());
    }

    public boolean mastered(PlayerData d) {
        return d.level >= AbpsMod.config().maxLevel;
    }

    // ---- Abilities ----
    public boolean useAbility(int idx, ServerPlayer p, PlayerData d) {
        lastFail = null;
        return switch (idx) {
            case 1 -> ability1(p, d);
            case 2 -> ability2(p, d);
            case 3 -> ability3(p, d);
            case 4 -> ability4(p, d);
            case 5 -> ultimate(p, d);
            default -> false;
        };
    }

    protected abstract boolean ability1(ServerPlayer p, PlayerData d);

    protected abstract boolean ability2(ServerPlayer p, PlayerData d);

    protected abstract boolean ability3(ServerPlayer p, PlayerData d);

    protected abstract boolean ability4(ServerPlayer p, PlayerData d);

    /** The charged ultimate. Filled up by dealing damage to players. */
    protected abstract boolean ultimate(ServerPlayer p, PlayerData d);

    // ---- Hooks ----
    /** Stats that are always on. Called after all old modifiers are cleared. */
    public void applyStatic(ServerPlayer p, PlayerData d) {
    }

    /** Runs every 5 ticks. Keep it light. */
    public void tick(ServerPlayer p, PlayerData d) {
    }

    /** Damage multiplier for hits this player deals. */
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        return 1;
    }

    /** Damage multiplier for damage this player takes. 0 or less cancels it. */
    public double incoming(ServerPlayer p, PlayerData d, DamageSource source, float amount) {
        return 1;
    }

    /** After this player took damage. taken is the real damage after armor. */
    public void afterDamaged(ServerPlayer p, PlayerData d, DamageSource source, float taken) {
    }

    /** Return false to cancel damage before it happens (dodges and such). Only called once per hit. */
    public boolean allowDamage(ServerPlayer p, PlayerData d, DamageSource source) {
        return true;
    }

    /** Return false to stop this player from dying. */
    public boolean allowDeath(ServerPlayer p, PlayerData d, DamageSource source) {
        return true;
    }

    /** After a hit this player dealt landed. dealt is the real damage. */
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
    }

    /** Multiplier for healing from a full food bar. */
    public double foodHealMultiplier(PlayerData d) {
        return 1;
    }

    public double hungerMultiplier() {
        return 1;
    }

    public boolean immuneTo(Holder<MobEffect> effect) {
        return false;
    }

    /** When the player starts mining a block. */
    public void onBlockAttack(ServerPlayer p, PlayerData d, BlockState state) {
    }

    /** After a block is broken. */
    public void afterBlockBreak(ServerPlayer p, PlayerData d, ServerLevel level, BlockPos pos, BlockState state) {
    }

    /** Can change the tool used to work out block drops (for extra Fortune). */
    public ItemStack dropTool(ServerPlayer p, PlayerData d, BlockState state, ItemStack tool) {
        return tool;
    }

    /** Can change the list of block drops. */
    public void modifyDrops(ServerPlayer p, PlayerData d, BlockState state, List<ItemStack> drops) {
    }

    /** When an arrow this player fired from a bow first appears. */
    public void onShoot(ServerPlayer p, PlayerData d, AbstractArrow arrow) {
    }

    public void onKill(ServerPlayer p, PlayerData d, LivingEntity victim) {
    }

    /** Return true to stop vanilla from handling the hit (no fire, no normal damage). */
    public boolean onProjectileHit(ServerPlayer p, PlayerData d, Entity projectile, HitResult hit) {
        return false;
    }

    /** Return false to stop the player using this item (like a shield). */
    public boolean allowUseItem(ServerPlayer p, PlayerData d, ItemStack stack) {
        return true;
    }

    /** Return true to cancel durability loss. */
    public boolean saveDurability(ServerPlayer p, PlayerData d, ItemStack stack) {
        return false;
    }

    /** Return true to stop this mob from targeting the player. */
    public boolean ignoredBy(ServerPlayer p, PlayerData d, Mob mob) {
        return false;
    }

    /** Jump pressed in the air (sent by the client mod). */
    public void onAirJump(ServerPlayer p, PlayerData d) {
    }

    /** Undo anything temporary. Called on quit, class change and shutdown. */
    public void cleanup(ServerPlayer p, PlayerData d) {
    }

    // ---- Text helpers ----
    public String colored(String text) {
        return "<" + color() + ">" + text + "</" + color() + ">";
    }

    public String gradient(String text) {
        return "<gradient:" + color() + ":" + color2() + ">" + text + "</gradient>";
    }

    public String display() {
        return gradient("<bold>" + name() + "</bold>");
    }

    // ---- Math helpers ----
    protected double lerp(int level, double atLevel1, double atMax) {
        return atLevel1 + (atMax - atLevel1) * AbpsMod.config().scale(level);
    }

    protected static String pct(double v) {
        return Text.pct(v);
    }

    protected static String num(double v) {
        return Text.num(v);
    }

    protected static String mult(double v) {
        return Text.mult(v);
    }

    protected static long now() {
        return System.currentTimeMillis();
    }

    protected static double rand() {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble();
    }

    protected static ServerLevel level(ServerPlayer p) {
        return (ServerLevel) p.level();
    }

    // ---- Feedback ----
    protected void fail(ServerPlayer p, String text) {
        lastFail = text;
        AbpsMod.service().actionBar(p, "<red>" + text);
        Fx.sound(level(p), p, SoundEvents.NOTE_BLOCK_BASS, 0.7f, 0.8f);
    }

    protected void used(ServerPlayer p, int idx) {
        AbpsMod.service().actionBar(p, gradient("<bold>✦ " + abilityName(idx) + "</bold>") + " <gray>used!");
    }

    protected static float maxHp(LivingEntity e) {
        return e.getMaxHealth();
    }

    protected static void heal(LivingEntity e, double amount) {
        if (!e.isAlive() || amount <= 0) return;
        e.setHealth((float) Math.min(e.getMaxHealth(), e.getHealth() + amount));
    }

    protected static void repeat(int count, long period, IntConsumer body) {
        Tasks.repeat(count, period, body);
    }
}
