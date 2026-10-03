package dev.abps.classes;

import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Gatherer. Cooking: faster kitchens, bigger batches, longer meal buffs, and abilities that feed the whole party. */
public final class Chef extends AttributeClass {

    @Override public String id() { return "chef"; }
    @Override public String name() { return "Chef"; }
    @Override public String color() { return "#FFAB40"; }
    @Override public String color2() { return "#FF5370"; }
    @Override public Item icon() { return Items.CAKE; }
    @Override public String symbol() { return "♨"; }
    @Override public String tagline() { return "Nobody fights hungry on your watch."; }
    @Override public String mastery() { return "Your feasts also give everyone at the table Absorption II."; }
    @Override public Role role() { return Role.GATHERER; }

    private double snackFill(int lvl) { return lerp(lvl, 4, 8); }
    private double spiceTime(int lvl) { return lerp(lvl, 30, 60); }
    private double picnicHeal(int lvl) { return lerp(lvl, 6, 12); }
    private double rushTime(int lvl) { return lerp(lvl, 20, 40); }
    private double banquetTime(int lvl) { return lerp(lvl, 45, 90); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Cooking Pots and Stone Ovens cook twice as fast for you",
                "A 1 in 3 chance of an extra serving from pots, ovens and the cutting board",
                "Meal and drink buffs you eat last 50% longer",
                "Aging barrels you fill finish in half the time",
                "Food fills you up 25% more");
    }

    @Override
    public List<String> negatives() {
        return List.of("Gatherer: deal 25% less damage to players", "Get hungry 15% faster");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Taste Test";
            case 2 -> "Spice Rack";
            case 3 -> "Picnic";
            case 4 -> "Kitchen Rush";
            default -> "Grand Banquet";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Snack on the spot: fill " + num(snackFill(lvl)) + " hunger and as much saturation.";
            case 2 -> "You and everyone within 8 blocks get Speed I and Haste I for " + num(spiceTime(lvl)) + "s.";
            case 3 -> "Lay out a picnic: everyone within 8 blocks heals " + num(picnicHeal(lvl) / 2) + " hearts over 5s.";
            case 4 -> "For " + num(rushTime(lvl)) + "s, pots and ovens you start finish almost at once.";
            default -> "Everyone within 12 blocks is fully fed and gets Regeneration II, Absorption II and Strength I for "
                    + num(banquetTime(lvl)) + "s.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 20;
            case 2 -> 45;
            case 3 -> 60;
            default -> 90;
        };
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:bread", "minecraft:cake", "minecraft:golden_carrot", "minecraft:enchanted_golden_apple"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{64, 8, 16, 1};
    }

    @Override
    public double foodHealMultiplier(PlayerData d) {
        return 1.25;
    }

    @Override
    public double hungerMultiplier() {
        return 1.15;
    }

    @Override
    public double gatherCharge(BlockState state) {
        return Harvester.ripe(state) ? 0.004 : 0;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
    }

    private List<ServerPlayer> table(ServerPlayer p, double r) {
        return level(p).getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(r));
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        if (p.getFoodData().getFoodLevel() >= 20) {
            fail(p, "You're not hungry.");
            return false;
        }
        used(p, 1);
        int fill = (int) Math.round(snackFill(d.level));
        p.getFoodData().setFoodLevel(Math.min(20, p.getFoodData().getFoodLevel() + fill));
        p.getFoodData().setSaturation(Math.min(p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel() + fill));
        Fx.sound(level(p), p, SoundEvents.PLAYER_BURP, 1f, 1.2f);
        Fx.burst(level(p), ParticleTypes.HAPPY_VILLAGER, p.position().add(0, 1.6, 0), 6, 0.3, 0.02);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        used(p, 2);
        int t = (int) (spiceTime(d.level) * 20);
        for (ServerPlayer o : table(p, 8)) {
            o.addEffect(new MobEffectInstance(MobEffects.SPEED, t, 0));
            o.addEffect(new MobEffectInstance(MobEffects.HASTE, t, 0));
            Fx.burst(level(p), ParticleTypes.FLAME, o.position().add(0, 1, 0), 6, 0.3, 0.02);
        }
        Fx.sound(level(p), p, SoundEvents.FIRECHARGE_USE, 0.6f, 1.6f);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        used(p, 3);
        int amp = picnicHeal(d.level) >= 10 ? 2 : 1;
        for (ServerPlayer o : table(p, 8)) {
            o.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, amp));
            Fx.burst(level(p), ParticleTypes.HEART, o.position().add(0, 1.8, 0), 4, 0.4, 0.02);
        }
        Fx.sound(level(p), p, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.4f);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        d.setBuff("kitchen_rush", (long) (rushTime(d.level) * 1000));
        used(p, 4);
        Fx.sound(level(p), p, SoundEvents.FURNACE_FIRE_CRACKLE, 1f, 1.4f);
        return true;
    }

    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        used(p, ULTIMATE);
        ServerLevel level = level(p);
        int t = (int) (banquetTime(d.level) * 20);
        for (ServerPlayer o : table(p, 12)) {
            o.getFoodData().setFoodLevel(20);
            o.getFoodData().setSaturation(20);
            o.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            o.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, t, 1));
            o.addEffect(new MobEffectInstance(MobEffects.STRENGTH, t, 0));
            Fx.burst(level, ParticleTypes.HEART, o.position().add(0, 1.8, 0), 8, 0.5, 0.05);
        }
        Fx.sound(level, p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
        return true;
    }
}
