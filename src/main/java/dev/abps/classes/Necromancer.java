package dev.abps.classes;

import dev.abps.AbpsMod;
import dev.abps.data.PlayerData;
import dev.abps.util.Fx;
import dev.abps.util.Minions;
import dev.abps.util.Mods;
import dev.abps.util.Targets;
import dev.abps.util.Tasks;
import dev.abps.util.Text;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class Necromancer extends AttributeClass {

    private static final int MAX_MINIONS = 14;

    @Override public String id() { return "necromancer"; }
    @Override public String name() { return "Necromancer"; }
    @Override public String color() { return "#64FFDA"; }
    @Override public String color2() { return "#311B92"; }
    @Override public Item icon() { return Items.WITHER_SKELETON_SKULL; }
    @Override public String symbol() { return "☠"; }
    @Override public String tagline() { return "Raise an army of the dead."; }
    @Override public String mastery() { return "Monsters you or your minions kill have a 40% chance to rise as a minion for 20s."; }

    private double xpBonus(int lvl) { return lerp(lvl, 0.30, 0.80); }
    private double minionTime(int lvl) { return lerp(lvl, 25, 40); }
    private double minionDamage(int lvl) { return lerp(lvl, 4, 8); }
    private double minionHealth(int lvl) { return Math.round(lerp(lvl, 24, 40)); }
    private double explodeDamage(int lvl) { return lerp(lvl, 3, 6); }
    private double drain(int lvl) { return lerp(lvl, 6, 10); }
    private int zombies(int lvl) { return lvl >= 20 ? 6 : lvl >= 10 ? 5 : 4; }
    private int skeletons(int lvl) { return lvl >= 15 ? 3 : 2; }
    private double knightHealth(int lvl) { return Math.round(lerp(lvl, 60, 100)); }
    private double knightDamage(int lvl) { return lerp(lvl, 10, 14); }
    private double soulBlast(int lvl) { return lerp(lvl, 8, 11); }

    @Override
    public List<String> passives(int lvl) {
        return List.of(
                "Your minions heal you for 25% of the damage they deal",
                "Minions have " + num(minionHealth(lvl)) + " health, deal " + num(minionDamage(lvl)) + " damage and move 20% faster",
                "You deal +3% damage for each minion you have (up to +30%)",
                "Minions explode when their time runs out, dealing " + num(explodeDamage(lvl)) + " damage",
                "Undead mobs ignore you unless you attack one",
                "Immune to Wither",
                "+" + pct(xpBonus(lvl)) + " XP from kills");
    }

    @Override
    public List<String> negatives() {
        return List.of(
                "Natural healing is 20% weaker",
                "Iron golems attack you on sight");
    }

    @Override
    public String abilityName(int idx) {
        return switch (idx) {
            case 1 -> "Raise Army";
            case 2 -> "Soul Drain";
            case 3 -> "Sic 'Em";
            case 4 -> "Death Knight";
            default -> "Army of the Damned";
        };
    }

    @Override
    public String abilityDesc(int idx, int lvl) {
        return switch (idx) {
            case 1 -> "Summon an army of " + zombies(lvl) + " zombies and " + skeletons(lvl) + " skeletons that fight for you for " + num(minionTime(lvl)) + "s.";
            case 2 -> "Drain " + num(drain(lvl)) + " health from what you look at and heal yourself. Your minions heal fully and get Strength for 8s.";
            case 3 -> "Send every minion after what you look at. They get Speed II and Strength II for 8s.";
            case 4 -> "Summon a giant Death Knight with " + num(knightHealth(lvl)) + " health that hits for " + num(knightDamage(lvl)) + " and withers enemies. Lasts 45s.";
            default -> "A wave of souls bursts out. Every enemy within 10 blocks takes " + num(soulBlast(lvl))
                    + " damage and a vex rises from each (up to 6) to fight for you. Your whole army gets Strength II.";
        };
    }

    @Override
    public double baseCooldown(int idx) {
        return switch (idx) {
            case 1 -> 60;
            case 2 -> 20;
            case 3 -> 20;
            default -> 240;
        };
    }

    @Override
    public double foodHealMultiplier(PlayerData d) {
        return 0.8;
    }

    @Override
    public boolean immuneTo(Holder<MobEffect> effect) {
        return effect.equals(MobEffects.WITHER);
    }

    @Override
    public boolean ignoredBy(ServerPlayer p, PlayerData d, Mob mob) {
        return Targets.UNDEAD.contains(mob.getType()) && mob.getType() != EntityTypes.WITHER && now() - d.lastUndeadHit > 10_000;
    }

    @Override
    public double outgoing(ServerPlayer p, PlayerData d, LivingEntity victim, Hit hit) {
        return 1 + 0.03 * Math.min(10, d.minions.size());
    }

    @Override
    public void afterHit(ServerPlayer p, PlayerData d, LivingEntity victim, float dealt, Hit hit) {
        if (Targets.UNDEAD.contains(victim.getType())) d.lastUndeadHit = now();
    }

    /** A minion of this player dealt damage. */
    public void minionDealt(ServerPlayer p, PlayerData d, float dealt) {
        heal(p, dealt * 0.25);
    }

    @Override
    public void onKill(ServerPlayer p, PlayerData d, LivingEntity victim) {
        if (victim instanceof Enemy) {
            int bonus = (int) Math.round(5 * xpBonus(d.level));
            if (bonus > 0) ExperienceOrb.award(level(p), victim.position(), bonus);
        }
        tryRise(p, d, victim);
    }

    private static boolean isBoss(LivingEntity e) {
        return e instanceof WitherBoss || e instanceof EnderDragon || e instanceof Warden || e instanceof ElderGuardian;
    }

    /** Mastery: fallen monsters can come back as minions. Also called when a minion gets a kill. */
    public void tryRise(ServerPlayer p, PlayerData d, LivingEntity dead) {
        if (!mastered(d) || !(dead instanceof Enemy) || isBoss(dead) || Targets.minionOwner(dead) != null) return;
        if (d.minions.size() >= MAX_MINIONS || rand() >= 0.40) return;
        Vec3 at = dead.position();
        boolean skeleton = dead instanceof AbstractSkeleton;
        Tasks.later(1, () -> {
            if (p.isRemoved()) return;
            spawnMinion(p, d, at, 20_000, skeleton ? EntityTypes.SKELETON : EntityTypes.ZOMBIE);
            AbpsMod.service().actionBar(p, gradient("<bold>A fallen enemy rises for you!"));
        });
    }

    @Override
    public void tick(ServerPlayer p, PlayerData d) {
        // Iron golems hunt necromancers
        GameType gm = p.gameMode();
        if (d.tickCount % 8 == 0 && (gm == GameType.SURVIVAL || gm == GameType.ADVENTURE)) {
            for (IronGolem g : p.level().getEntitiesOfClass(IronGolem.class, p.getBoundingBox().inflate(16))) {
                if (g.getTarget() == null) g.setTarget(p);
            }
        }
        double boom = explodeDamage(d.level);
        boolean expired = Minions.tick(p, d, mob -> explode(p, mob.position(), boom));
        if (expired && d.minions.isEmpty()) AbpsMod.service().actionBar(p, gradient("Your army crumbles to dust."));
        if (!d.minions.isEmpty() && d.tickCount % 4 == 0) {
            ServerLevel level = level(p);
            for (UUID id : d.minions.keySet()) {
                Entity e = level.getEntity(id);
                if (e != null) Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, e.position().add(0, 0.2, 0), 1, 0.2, 0.01);
            }
        }
    }

    private void explode(ServerPlayer p, Vec3 at, double dmg) {
        ServerLevel level = level(p);
        for (LivingEntity e : Targets.enemiesNear(p, at, 3.5)) Targets.damage(e, dmg, p);
        dev.abps.util.Fancy.impact(level, at.add(0, 0.8, 0), 1.2f, 0x64FFDA, 0x311B92);
        Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, at.add(0, 0.8, 0), 20, 0.5, 0.06);
        Fx.burst(level, ParticleTypes.EXPLOSION, at.add(0, 0.8, 0), 1, 0, 0);
        Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.6f);
    }

    /** Gives a minion something to hold. Nothing goes on their heads, so they look like plain undead. */
    private void equip(Mob mob, ItemStack hand) {
        if (hand != null) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, hand);
            mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
        }
    }

    /** Common setup for every summoned undead. Returns false if the world wouldn't let it spawn. */
    private boolean finish(ServerPlayer p, PlayerData d, Mob mob, String name, double hp, double dmg, long lifeMs) {
        mob.setCustomName(Text.mm(gradient(name)));
        mob.setCustomNameVisible(false); // name shows only when you look at it, so an army doesn't flood the screen
        mob.setCanPickUpLoot(false);
        Mods.setBase(mob, Attributes.ATTACK_DAMAGE, dmg);
        Mods.setBase(mob, Attributes.MAX_HEALTH, hp);
        Mods.scaleBase(mob, Attributes.MOVEMENT_SPEED, 1.2);
        Mods.setBase(mob, Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0);
        mob.setHealth((float) hp);
        Targets.markMinion(mob, p.getUUID());
        if (!level(p).addFreshEntity(mob)) {
            Targets.forgetMinion(mob.getUUID());
            return false;
        }
        d.minions.put(mob.getUUID(), now() + lifeMs);
        return true;
    }

    private void spawnMinion(ServerPlayer p, PlayerData d, Vec3 at, long lifeMs, EntityType<? extends Mob> type) {
        ServerLevel level = level(p);
        Mob mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (mob == null) return;
        mob.snapTo(at.x, at.y, at.z, p.getYRot(), 0);
        boolean skeleton = mob instanceof AbstractSkeleton;
        // Sun burning is turned off for minions in MobMixin
        equip(mob, skeleton ? new ItemStack(Items.BOW) : null);
        if (!finish(p, d, mob, p.getName().getString() + "'s Minion", minionHealth(d.level), minionDamage(d.level), lifeMs)) return;
        Fx.burst(level, ParticleTypes.SOUL, at.add(0, 0.5, 0), 15, 0.3, 0.5, 0.3, 0.03);
        Fx.burst(level, ParticleTypes.SCULK_SOUL, at.add(0, 0.2, 0), 6, 0.3, 0.1, 0.3, 0.02);
    }

    @Override
    protected boolean ability1(ServerPlayer p, PlayerData d) {
        Minions.removeAll(p, d);
        int z = zombies(d.level);
        int s = skeletons(d.level);
        int n = z + s;
        long life = (long) (minionTime(d.level) * 1000);
        ServerLevel level = level(p);
        dev.abps.util.Fancy.sigil(level, p.position(), 5, 10, 0x64FFDA, 0x311B92, 60);
        // Rise one after another for a nicer effect
        Tasks.repeat(n, 2, i -> {
            if (p.isRemoved()) return;
            double a = Math.PI * 2 * i / n;
            double r = i % 2 == 0 ? 2.0 : 3.0;
            Vec3 at = p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            spawnMinion(p, d, at, life, i >= z ? EntityTypes.SKELETON : EntityTypes.ZOMBIE);
            dev.abps.util.Vfx.pillar(level, at, 0.5, 4, dev.abps.util.Vfx.tint(0x64FFDA), 4, 6, 8, 0x64FFDA);
        });
        Fx.ring(level, ParticleTypes.SOUL_FIRE_FLAME, p.position(), 3, 36);
        Fx.spiral(level, Fx.dust(0x64FFDA, 1.2f), p.position(), 3, 2.5, 40, 0);
        Fx.sound(level, p, SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 0.8f);
        Fx.sound(level, p, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.4f, 1.6f);
        used(p, 1);
        return true;
    }

    @Override
    protected boolean ability2(ServerPlayer p, PlayerData d) {
        LivingEntity t = Targets.lookTarget(p, 24);
        if (t == null) {
            noTarget(p, 24);
            return false;
        }
        float before = t.getHealth();
        Targets.damage(t, drain(d.level), p);
        t.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
        heal(p, Math.max(0, before - t.getHealth()));
        ServerLevel level = level(p);
        for (UUID id : d.minions.keySet()) {
            if (level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                mob.setHealth(mob.getMaxHealth());
                mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 0));
                Fx.burst(level, ParticleTypes.SOUL, mob.getEyePosition(), 6, 0.2, 0.3, 0.2, 0.02);
            }
        }
        Fx.line(level, ParticleTypes.SOUL, t.getEyePosition(), p.getEyePosition(), 0.5);
        Fx.line(level, Fx.dust(0x64FFDA, 0.8f), t.getEyePosition(), p.getEyePosition(), 0.3);
        dev.abps.util.Fancy.laser(level, t.getEyePosition(), p.getEyePosition(), 0.14f, 0x64FFDA, 0x7C4DFF, 12);
        dev.abps.util.Fancy.impact(level, t.getEyePosition(), 1.4f, 0x64FFDA, 0x7C4DFF);
        Fx.sound(level, p, SoundEvents.WITHER_SHOOT, 0.6f, 1.4f);
        used(p, 2);
        return true;
    }

    @Override
    protected boolean ability3(ServerPlayer p, PlayerData d) {
        if (d.minions.isEmpty()) {
            fail(p, "You have no minions. Use Raise Army first.");
            return false;
        }
        LivingEntity t = Targets.lookTarget(p, 32);
        if (t == null) {
            noTarget(p, 32);
            return false;
        }
        d.lastHit = t.getUUID();
        d.lastHitTime = now();
        ServerLevel level = level(p);
        for (UUID id : d.minions.keySet()) {
            if (!(level.getEntity(id) instanceof Mob mob) || !mob.isAlive()) continue;
            mob.setTarget(t);
            mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 160, 1));
            mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 1));
            Fx.burst(level, ParticleTypes.ANGRY_VILLAGER, mob.getEyePosition().add(0, 0.4, 0), 1, 0, 0);
            Fx.line(level, Fx.dust(0x311B92, 0.6f), mob.getEyePosition(), t.getEyePosition(), 0.8);
            dev.abps.util.Vfx.beam(level, mob.getEyePosition(), t.getEyePosition(), 0.06f, dev.abps.util.Vfx.tint(0x7C4DFF), 10, 0x7C4DFF);
        }
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
        dev.abps.util.Fancy.chains(level, t, 100, 0x7C4DFF, 0x64FFDA);
        Fx.sound(level, p, SoundEvents.WITHER_AMBIENT, 0.6f, 1.6f);
        used(p, 3);
        return true;
    }

    @Override
    protected boolean ability4(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 flat = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        Vec3 at = p.position().add(flat.lengthSqr() < 0.01 ? Vec3.ZERO : flat.normalize().scale(2));
        Mob knight = EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (knight == null) return false;
        knight.snapTo(at.x, at.y, at.z, p.getYRot(), 0);
        equip(knight, new ItemStack(Items.NETHERITE_SWORD));
        Mods.setBase(knight, Attributes.SCALE, 1.4);
        Mods.setBase(knight, Attributes.KNOCKBACK_RESISTANCE, 0.8);
        if (!finish(p, d, knight, p.getName().getString() + "'s Death Knight", knightHealth(d.level), knightDamage(d.level), 45_000)) {
            fail(p, "The Death Knight couldn't rise here.");
            return false;
        }
        Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, at.add(0, 1, 0), 60, 0.6, 1.2, 0.6, 0.05);
        Fx.burst(level, ParticleTypes.EXPLOSION, at, 2, 0.3, 0);
        Fx.sound(level, at, SoundEvents.WITHER_SPAWN, 0.7f, 1.2f);
        dev.abps.util.Fancy.sigil(level, at, 4, 8, 0x64FFDA, 0x311B92, 40);
        dev.abps.util.Vfx.pillar(level, at, 1.0, 10, dev.abps.util.Vfx.tint(0x64FFDA), 4, 10, 10, 0x64FFDA);
        dev.abps.util.Vfx.jaws(level, at, 2.5, 10, 3, dev.abps.util.Vfx.tint(0x311B92), 0x64FFDA);
        dev.abps.util.Vfx.sphere(level, at.add(0, 1, 0), 0.5, 4, 30, dev.abps.util.Vfx.tint(0x64FFDA), 0.2f, 16, 0x64FFDA);
        Fx.shakeNear(level, at, 12, 8, 0.6f);
        used(p, 4);
        return true;
    }

    // ---- Ultimate: Army of the Damned ----
    @Override
    protected boolean ultimate(ServerPlayer p, PlayerData d) {
        ServerLevel level = level(p);
        Vec3 c = p.position();
        List<LivingEntity> hit = Targets.enemiesNear(p, c, 10);
        double dmg = soulBlast(d.level);
        int vexes = 0;
        for (LivingEntity e : hit) {
            Targets.damage(e, dmg, p);
            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            Fx.line(level, ParticleTypes.SOUL, c.add(0, 1, 0), e.position().add(0, 1, 0), 0.6);
            if (vexes < 6 && d.minions.size() < MAX_MINIONS) {
                Mob vex = EntityTypes.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
                if (vex != null) {
                    vex.snapTo(e.getX(), e.getY() + 1, e.getZ(), 0, 0);
                    if (finish(p, d, vex, "Spirit", 14, minionDamage(d.level), 15_000)) vexes++;
                }
            }
        }
        for (UUID id : d.minions.keySet()) {
            if (level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 1));
                mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
            }
        }
        Tasks.repeat(6, 2, step -> Fx.ring(level, ParticleTypes.SOUL_FIRE_FLAME, c, 1.5 + step * 1.6, 20 + step * 8));
        dev.abps.util.Fancy.sigil(level, c, 10, 14, 0x64FFDA, 0x311B92, 40);
        dev.abps.util.Vfx.sphere(level, c.add(0, 1, 0), 1, 10, 50, dev.abps.util.Vfx.tint(0x64FFDA), 0.24f, 20, 0x64FFDA);
        Fx.spiral(level, ParticleTypes.SOUL, c, 3, 4, 60, 0);
        Fx.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 0.6f);
        Fx.sound(level, c, SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 0.6f);
        Fx.shakeNear(level, c, 14, 10, 0.7f);
        used(p, ULTIMATE);
        return true;
    }

    @Override
    public void cleanup(ServerPlayer p, PlayerData d) {
        Minions.removeAll(p, d);
    }

    @Override
    protected void flavor(net.minecraft.server.level.ServerPlayer p, int idx, net.minecraft.server.level.ServerLevel level,
                          net.minecraft.world.phys.Vec3 at, boolean ult) {
        for (int i = 0; i < (ult ? 10 : 5); i++) {
            double a = Math.PI * 2 * i / (ult ? 10 : 5);
            net.minecraft.world.phys.Vec3 base = at.add(Math.cos(a) * (ult ? 5 : 3), 0.1, Math.sin(a) * (ult ? 5 : 3));
            dev.abps.util.Vfx.pillar(level, base, 0.12, ult ? 5 : 3, dev.abps.util.Vfx.tint(0x64FFDA), 4, 8, 8, 0x64FFDA);
            dev.abps.util.Vfx.beam(level, base.add(0, ult ? 5 : 3, 0), at.add(0, 1.6, 0), 0.05f, net.minecraft.world.level.block.Blocks.CONCRETE.purple().defaultBlockState(), 12, 0x7C4DFF);
        }
        dev.abps.util.Vfx.vortex(level, at, ult ? 5 : 3, ult ? 18 : 9, net.minecraft.world.level.block.Blocks.SOUL_SAND.defaultBlockState(), 0.15f, ult ? 70 : 30, 1.2, 0x64FFDA);
    }

    @Override
    public String[] upgradeItems() {
        return new String[]{"minecraft:bone", "minecraft:soul_sand", "minecraft:wither_skeleton_skull", "minecraft:echo_shard"};
    }

    @Override
    public int[] upgradeCounts() {
        return new int[]{40, 32, 2, 3};
    }
}
