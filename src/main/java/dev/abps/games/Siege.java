package dev.abps.games;

import dev.abps.AbpsMod;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Siege: co-op tower defense. Raiders pour through four gates toward the Heart in the middle of a walled courtyard.
 * Every kill adds coins to the party's purse; right-click a tower blueprint on a tower pad to build or upgrade.
 */
final class Siege extends Game {

    static final String MOB_TAG = "abps_siege";
    private static final int FLOOR_R = 26, WALL_R = 29, OUTER = 37;

    enum TowerType {
        ARCHER("Archer Tower", Items.ARROW, 10, 0x8D6E63, Blocks.TARGET.defaultBlockState()),
        FROST("Frost Tower", Items.PRISMARINE_CRYSTALS, 15, 0x80DEEA, Blocks.BLUE_ICE.defaultBlockState()),
        BOMBARD("Bombard Tower", Items.BLAZE_POWDER, 20, 0xFF7043, Blocks.MAGMA_BLOCK.defaultBlockState()),
        HEALER("Healing Shrine", Items.GLISTERING_MELON_SLICE, 15, 0xF48FB1, Blocks.GLOWSTONE.defaultBlockState());

        final String title;
        final Item blueprint;
        final int cost, color;
        final BlockState crown;

        TowerType(String title, Item blueprint, int cost, int color, BlockState crown) {
            this.title = title;
            this.blueprint = blueprint;
            this.cost = cost;
            this.color = color;
            this.crown = crown;
        }

        static TowerType of(ItemStack s) {
            for (TowerType t : values()) if (s.is(t.blueprint)) return t;
            return null;
        }
    }

    static final class Tower {
        final BlockPos pad;
        TowerType type;
        int level;
        int cooldown;

        Tower(BlockPos pad, TowerType type) {
            this.pad = pad;
            this.type = type;
            this.level = 1;
        }

        Vec3 top() {
            return Vec3.atCenterOf(pad).add(0, 3 + level, 0);
        }
    }

    private final int waves, difficulty;
    private final List<BlockPos> pads = new ArrayList<>();
    private final List<Tower> towers = new ArrayList<>();
    private final List<UUID> mobs = new ArrayList<>();
    private final List<EntityType<? extends Mob>> queue = new ArrayList<>();
    private int wave;
    private int heart = 100;
    private final int heartMax = 100;
    private int coins;
    private int prep; // ticks until the next wave
    private int spawnGate;
    private boolean lost;

    Siege(int slot, GameDef def, ServerLevel level, List<ServerPlayer> party) {
        super(slot, def, level, party);
        this.difficulty = def.difficulty();
        this.waves = 5 + difficulty * 5;
        this.coins = 20 + 8 * party.size();
    }

    @Override
    int radius() {
        return OUTER + 4;
    }

    private int y0() {
        return origin.getY();
    }

    private Vec3 heartPos() {
        return new Vec3(origin.getX() + 0.5, y0() + 3, origin.getZ() + 0.5);
    }

    // ------------------------------------------------------------------ arena

    @Override
    void build() {
        int cx = origin.getX(), cz = origin.getZ(), y = y0();
        BlockState smooth = Blocks.SMOOTH_STONE.defaultBlockState(), bricks = Blocks.STONE_BRICKS.defaultBlockState(),
                mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
                path = Blocks.POLISHED_ANDESITE.defaultBlockState(), tiles = Blocks.DEEPSLATE_TILES.defaultBlockState(),
                polished = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), chisel = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        for (int dx = -OUTER; dx <= OUTER; dx++)
            for (int dz = -OUTER; dz <= OUTER; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                boolean gateLane = (Math.abs(dx) <= 2 || Math.abs(dz) <= 2);
                int x = cx + dx, z = cz + dz;
                if (r <= FLOOR_R + 0.5) {
                    BlockState f;
                    if (r < 6) f = (dx + dz & 1) == 0 ? tiles : polished;
                    else if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) f = path;
                    else if ((int) r % 7 == 0) f = bricks;
                    else f = rnd.nextInt(9) == 0 ? Blocks.ANDESITE.defaultBlockState() : smooth;
                    builder.set(x, y, z, f);
                    builder.fill(x, y - 3, z, x, y - 1, z, bricks);
                } else if (r <= WALL_R + 0.5) {
                    builder.set(x, y, z, bricks);
                    builder.fill(x, y - 3, z, x, y - 1, z, bricks);
                    if (gateLane) {
                        // The gate: an arch over a five-wide opening
                        builder.fill(x, y + 6, z, x, y + 7, z, chisel);
                        continue;
                    }
                    for (int h = 1; h <= 6; h++) builder.set(x, y + h, z, rnd.nextInt(6) == 0 ? mossy : rnd.nextInt(8) == 0 ? cracked : bricks);
                    // Battlements on the outer ring
                    if (r > WALL_R - 0.6 && ((int) Math.round(Math.atan2(dz, dx) * 20) & 1) == 0) builder.set(x, y + 7, z, bricks);
                } else if (gateLane && r <= OUTER) {
                    // Lanes out to the spawn pads, with low walls
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    builder.set(x, y, z, edge ? bricks : path);
                    builder.fill(x, y - 3, z, x, y - 1, z, bricks);
                    if (edge && (Math.abs(dx) > 2 || Math.abs(dz) > 2)) builder.set(x, y + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                }
            }
        // Spawn portals at the end of each lane
        for (int[] g : gates()) {
            int gx = cx + g[0], gz = cz + g[1];
            for (int a = -2; a <= 2; a++)
                for (int b = -2; b <= 2; b++) {
                    boolean rim = Math.abs(a) == 2 || Math.abs(b) == 2;
                    builder.set(gx + a, y, gz + b, rim ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
                    builder.fill(gx + a, y - 3, gz + b, gx + a, y - 1, gz + b, bricks);
                }
            builder.set(gx, y + 1, gz, Blocks.SOUL_LANTERN.defaultBlockState());
        }
        // Lanterns along the wall top
        for (int k = 0; k < 32; k++) {
            double a = k * Math.PI * 2 / 32;
            int x = cx + (int) Math.round(Math.cos(a) * (WALL_R - 1.5)), z = cz + (int) Math.round(Math.sin(a) * (WALL_R - 1.5));
            if (Math.abs(x - cx) <= 2 || Math.abs(z - cz) <= 2) continue;
            builder.set(x, y + 7, z, Blocks.LANTERN.defaultBlockState());
        }
        // Light wells in the floor
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            builder.set(cx + (int) Math.round(Math.cos(a) * 21), y, cz + (int) Math.round(Math.sin(a) * 21), Blocks.SEA_LANTERN.defaultBlockState());
        }
        // The Heart on its pedestal
        builder.fill(cx - 2, y + 1, cz - 2, cx + 2, y + 1, cz + 2, Blocks.QUARTZ_BLOCK.defaultBlockState());
        builder.fill(cx - 1, y + 2, cz - 1, cx + 1, y + 2, cz + 1, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        builder.set(cx, y + 3, cz, Blocks.BEACON.defaultBlockState());
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) builder.set(cx + c[0], y + 2, cz + c[1], Blocks.END_ROD.defaultBlockState());
        // Tower pads: four close to the Heart, eight further out
        for (int k = 0; k < 4; k++) pads.add(padAt(k * Math.PI / 2 + Math.PI / 4, 10));
        for (int k = 0; k < 8; k++) pads.add(padAt(k * Math.PI / 4 + Math.PI / 8, 18));
        for (BlockPos p : pads) {
            builder.fill(p.getX() - 1, y, p.getZ() - 1, p.getX() + 1, y, p.getZ() + 1, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            builder.set(p.getX(), y, p.getZ(), Blocks.GILDED_BLACKSTONE.defaultBlockState());
        }
    }

    private BlockPos padAt(double angle, double r) {
        return new BlockPos(origin.getX() + (int) Math.round(Math.cos(angle) * r), y0(), origin.getZ() + (int) Math.round(Math.sin(angle) * r));
    }

    private static int[][] gates() {
        return new int[][]{{OUTER - 3, 0}, {-(OUTER - 3), 0}, {0, OUTER - 3}, {0, -(OUTER - 3)}};
    }

    @Override
    Vec3 spawnPoint(ServerPlayer p) {
        double a = rnd.nextDouble() * Math.PI * 2;
        return new Vec3(origin.getX() + 0.5 + Math.cos(a) * 5, y0() + 1, origin.getZ() + 0.5 + Math.sin(a) * 5);
    }

    // ------------------------------------------------------------------ game

    @Override
    void begin() {
        prep = 30 * 20;
        for (ServerPlayer p : online()) {
            for (TowerType t : TowerType.values()) {
                ItemStack s = new ItemStack(t.blueprint);
                s.set(net.minecraft.core.component.DataComponents.ITEM_NAME, dev.abps.util.Text.mm(dev.abps.util.Text.colorTag(t.color) + t.title + " Blueprint"));
                give(p, s);
            }
        }
        tell("<gold><bold>Siege!</bold></gold> <gray>Protect the <aqua>Heart</aqua> in the middle. Kills fill the party purse.");
        tell("<gray>Stand on a <gold>gold-centred pad</gold> and right-click a blueprint to build a tower there. Do it again to upgrade (max level 3).");
        tell("<gray>Costs: <white>Archer 10 · Frost 15 · Bombard 20 · Healing Shrine 15</white>. Upgrades cost the price times the next level.");
    }

    @Override
    String hud() {
        String obj = prep > 0 ? (wave == 0 ? "Build towers! First wave in " : "Next wave in ") + (prep / 20 + 1) + "s"
                : "Wave " + wave + "/" + waves + ": " + mobs.size() + " raiders left";
        String heartCol = heart > 60 ? "<#69F0AE>" : heart > 30 ? "<#FFD54F>" : "<#FF5252>";
        return obj + "|" + heartCol + "❤ Heart " + heart + "/" + heartMax + "</" + "> <dark_gray>·</dark_gray> <gold>⛃ " + coins + " coins";
    }

    @Override
    void update() {
        mobs.removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        if (prep > 0) {
            if (--prep == 0) startWave();
            else if (prep % 20 == 0 && prep <= 100) sound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.8f, 1.6f);
        } else {
            if (!queue.isEmpty() && ticks % Math.max(6, 16 - wave / 2) == 0) spawnNext();
            if (queue.isEmpty() && mobs.isEmpty()) waveCleared();
        }
        if (ticks % 10 == 0) steer();
        for (Tower t : towers) fire(t);
        if (ticks % 20 == 0) {
            sendHud(Math.max(1, wave), waves, 0);
            Fx.burst(level, ParticleTypes.END_ROD, heartPos().add(0, 0.6, 0), 2, 0.25, 0.02);
        }
    }

    private void startWave() {
        wave++;
        int party = Math.max(1, online().size());
        int count = 5 + wave * 2 + (party - 1) * 3 + difficulty * 2;
        queue.clear();
        for (int i = 0; i < count; i++) queue.add(pickMob());
        if (wave % 5 == 0) for (int i = 0; i < Math.max(1, wave / 5 + difficulty - 1); i++) queue.add(EntityTypes.RAVAGER);
        if (wave >= 10 && difficulty >= 2) queue.add(EntityTypes.EVOKER);
        banner("<bold><#FF8A65>Wave " + wave + "</#FF8A65></bold>", "<gray>" + queue.size() + " raiders incoming", 0xFF8A65);
        sound(SoundEvents.RAID_HORN.value(), 1f, 1f);
    }

    private EntityType<? extends Mob> pickMob() {
        List<EntityType<? extends Mob>> pool = new ArrayList<>(List.of(EntityTypes.HUSK, EntityTypes.HUSK, EntityTypes.SPIDER));
        if (wave >= 3) pool.addAll(List.of(EntityTypes.PILLAGER, EntityTypes.SKELETON, EntityTypes.ZOMBIE));
        if (wave >= 6) pool.addAll(List.of(EntityTypes.VINDICATOR, EntityTypes.WITCH));
        if (wave >= 9) pool.addAll(List.of(EntityTypes.VINDICATOR, EntityTypes.PILLAGER, EntityTypes.CAVE_SPIDER));
        if (wave >= 14) pool.addAll(List.of(EntityTypes.WITHER_SKELETON, EntityTypes.VINDICATOR));
        return pool.get(rnd.nextInt(pool.size()));
    }

    private void spawnNext() {
        EntityType<? extends Mob> type = queue.removeFirst();
        int[] g = gates()[spawnGate++ % 4];
        Vec3 at = new Vec3(origin.getX() + g[0] + 0.5 + rnd.nextDouble() - 0.5, y0() + 1, origin.getZ() + g[1] + 0.5 + rnd.nextDouble() - 0.5);
        Mob mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (mob == null) return;
        mob.snapTo(at.x, at.y, at.z, rnd.nextFloat() * 360, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.MOB_SUMMONED, null);
        mob.setPersistenceRequired();
        mob.addTag(MOB_TAG);
        int party = Math.max(1, online().size());
        double hp = mob.getMaxHealth() * (1 + 0.08 * wave + 0.25 * (difficulty - 1) + 0.35 * (party - 1));
        Mods.setBase(mob, Attributes.MAX_HEALTH, hp);
        mob.setHealth((float) hp);
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) Mods.scaleBase(mob, Attributes.ATTACK_DAMAGE, 1 + 0.04 * wave + 0.15 * (difficulty - 1));
        if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the sun
            mob.setDropChance(EquipmentSlot.HEAD, 0f);
        }
        level.addFreshEntity(mob);
        mobs.add(mob.getUUID());
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, at.add(0, 1, 0), 16, 0.4, 0.05);
    }

    /** Raiders go for the Heart unless a player is right in their face. */
    private void steer() {
        Vec3 h = heartPos();
        for (UUID id : mobs) {
            if (!(level.getEntity(id) instanceof Mob mob)) continue;
            LivingEntity t = mob.getTarget();
            double toHeart = Math.hypot(mob.getX() - h.x, mob.getZ() - h.z);
            if (toHeart < 3.6) {
                if (ticks % 20 == 0) hitHeart(mob);
                continue;
            }
            if (t == null || !t.isAlive() || t.distanceTo(mob) > 7) {
                mob.setTarget(null);
                mob.getNavigation().moveTo(h.x, y0() + 1, h.z, 1.0);
            }
        }
    }

    private void hitHeart(Mob mob) {
        int dmg = mob.getType() == EntityTypes.RAVAGER ? 6 : mob.getType() == EntityTypes.EVOKER ? 4 : 2;
        heart = Math.max(0, heart - dmg);
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        Fx.burst(level, ParticleTypes.DAMAGE_INDICATOR, heartPos().add(0, 0.6, 0), 4, 0.4, 0.05);
        sound(SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 0.7f);
        if (heart <= 0) lose();
        else if (heart <= 30 && ticks % 60 == 0) bar("<#FF5252><bold>The Heart is failing!");
    }

    /** Called by Games when one of this Siege's raiders dies. */
    void onKill(Mob mob) {
        int gain = mob.getType() == EntityTypes.RAVAGER ? 15 : mob.getType() == EntityTypes.EVOKER ? 10
                : mob.getType() == EntityTypes.VINDICATOR || mob.getType() == EntityTypes.WITHER_SKELETON ? 4 : 2;
        coins += gain;
    }

    private void waveCleared() {
        if (wave >= waves) {
            win();
            return;
        }
        int bonus = 8 + wave;
        coins += bonus;
        heart = Math.min(heartMax, heart + 5);
        prep = 20 * 20;
        tell("<green>Wave " + wave + " cleared!</green> <gray>+" + bonus + " coins, the Heart mends a little. Next wave in 20s.");
        sound(SoundEvents.PLAYER_LEVELUP, 0.7f, 1.2f);
    }

    // ------------------------------------------------------------------ towers

    /** Right-clicking a blueprint: build or upgrade on the pad you're standing on or next to. */
    boolean use(ServerPlayer p, ItemStack stack) {
        TowerType type = TowerType.of(stack);
        if (type == null || !isGameItem(stack)) return false;
        BlockPos pad = null;
        double best = 9;
        for (BlockPos b : pads) {
            double d = Vec3.atBottomCenterOf(b).distanceToSqr(p.getX(), b.getY(), p.getZ());
            if (d < best) {
                best = d;
                pad = b;
            }
        }
        if (pad == null) {
            AbpsMod.service().actionBar(p, "<red>Stand on a tower pad (the gold-centred squares) first.");
            return true;
        }
        Tower existing = null;
        for (Tower t : towers) if (t.pad.equals(pad)) existing = t;
        if (existing != null && existing.type != type) {
            AbpsMod.service().actionBar(p, "<red>That pad already has a " + existing.type.title + ".");
            return true;
        }
        int lvl = existing == null ? 1 : existing.level + 1;
        if (lvl > 3) {
            AbpsMod.service().actionBar(p, "<gray>That tower is fully upgraded.");
            return true;
        }
        int cost = type.cost * lvl;
        if (coins < cost) {
            AbpsMod.service().actionBar(p, "<red>Needs " + cost + " coins (the party has " + coins + ").");
            return true;
        }
        coins -= cost;
        Tower t = existing;
        if (t == null) {
            t = new Tower(pad, type);
            towers.add(t);
        } else {
            t.level = lvl;
        }
        raise(t);
        tell("<gray>" + p.getName().getString() + " " + (lvl == 1 ? "built" : "upgraded") + " a " + dev.abps.util.Text.colorTag(type.color) + type.title
                + "</" + "> <gray>(level " + lvl + ", -" + cost + " coins)");
        Fx.sound(level, Vec3.atCenterOf(pad), SoundEvents.ANVIL_USE, 0.6f, 1.4f);
        return true;
    }

    /** Puts the tower's blocks up: a column that grows a block taller each level, crowned by its core. */
    private void raise(Tower t) {
        int x = t.pad.getX(), y = y0(), z = t.pad.getZ();
        BlockState body = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), wall = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
        int h = 2 + t.level;
        for (int k = 1; k < h; k++) level.setBlock(new BlockPos(x, y + k, z), body, 3);
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            for (int k = 1; k <= t.level; k++) level.setBlock(new BlockPos(x + c[0], y + k, z + c[1]), wall, 3);
        }
        level.setBlock(new BlockPos(x, y + h, z), t.type.crown, 3);
        if (t.level >= 2) level.setBlock(new BlockPos(x, y + h + 1, z), Blocks.LANTERN.defaultBlockState(), 3);
        Fx.burst(level, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(t.pad).add(0, h, 0), 12, 0.6, 0.05);
    }

    private List<LivingEntity> raidersNear(Vec3 at, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(r), m -> m.isAlive() && m.entityTags().contains(MOB_TAG))) {
            if (m.position().distanceToSqr(at) <= r * r) out.add(m);
        }
        return out;
    }

    private void hurt(LivingEntity e, float dmg) {
        e.hurtServer(level, level.damageSources().magic(), dmg);
    }

    private void fire(Tower t) {
        if (t.cooldown > 0) {
            t.cooldown--;
            return;
        }
        Vec3 top = t.top();
        switch (t.type) {
            case ARCHER -> {
                LivingEntity target = nearest(raidersNear(top, 14 + 2 * t.level), top);
                if (target == null) return;
                Fx.line(level, ParticleTypes.CRIT, top, target.position().add(0, target.getBbHeight() * 0.6, 0), 0.5);
                Fx.sound(level, top, SoundEvents.ARROW_SHOOT, 0.5f, 1.2f);
                hurt(target, 3 + 2 * t.level);
                t.cooldown = 22 - 5 * t.level;
            }
            case FROST -> {
                List<LivingEntity> near = raidersNear(top, 6 + t.level);
                if (near.isEmpty()) return;
                for (LivingEntity e : near) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, t.level));
                    hurt(e, t.level);
                }
                Fx.ring(level, ParticleTypes.SNOWFLAKE, Vec3.atBottomCenterOf(t.pad).add(0, 1.2, 0), 6 + t.level, 40);
                Fx.sound(level, top, SoundEvents.GLASS_BREAK, 0.4f, 1.8f);
                t.cooldown = 40;
            }
            case BOMBARD -> {
                LivingEntity target = nearest(raidersNear(top, 16), top);
                if (target == null) return;
                Vec3 at = target.position();
                Fx.line(level, ParticleTypes.FLAME, top, at.add(0, 0.5, 0), 0.6);
                for (LivingEntity e : raidersNear(at, 3)) hurt(e, 5 + 3 * t.level);
                Fx.burst(level, ParticleTypes.EXPLOSION, at.add(0, 0.5, 0), 1, 0, 0);
                Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.5f);
                t.cooldown = 70 - 10 * t.level;
            }
            case HEALER -> {
                boolean any = false;
                for (ServerPlayer p : online()) {
                    if (p.position().distanceToSqr(top) <= 100 && p.getHealth() < p.getMaxHealth()) {
                        p.heal(1.5f * t.level);
                        any = true;
                    }
                }
                if (heart < heartMax && heartPos().distanceToSqr(top) <= 400) {
                    heart = Math.min(heartMax, heart + t.level);
                    any = true;
                }
                if (any) Fx.burst(level, ParticleTypes.HEART, top, 3, 0.4, 0.02);
                t.cooldown = 60;
            }
        }
    }

    private static LivingEntity nearest(List<LivingEntity> list, Vec3 at) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double d = e.position().distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ ending

    @Override
    boolean allowDeath(ServerPlayer p) {
        heart = Math.max(0, heart - 3);
        tell("<red>☠ " + p.getName().getString() + " fell!</red> <gray>The Heart loses 3 as they're pulled back to it.");
        respawn(p);
        if (heart <= 0) lose();
        return false;
    }

    private void win() {
        banner("<bold><gradient:#FFD54F:#FF6D00>SIEGE HELD</gradient></bold>", "<gray>All " + waves + " waves beaten", 0xFFD54F);
        sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        reward(true);
        Games.recordWave(this, wave);
        killAll();
        finish(100);
    }

    private void lose() {
        if (lost) return;
        lost = true;
        banner("<bold><#FF5252>THE HEART FELL</#FF5252></bold>", "<gray>You held " + Math.max(0, wave - 1) + " of " + waves + " waves", 0xFF5252);
        sound(SoundEvents.WITHER_DEATH, 0.6f, 1.2f);
        reward(false);
        Games.recordWave(this, Math.max(0, wave - 1));
        killAll();
        finish(100);
    }

    private void killAll() {
        for (UUID id : mobs) {
            var e = level.getEntity(id);
            if (e != null) e.discard();
        }
        mobs.clear();
        queue.clear();
    }

    private void reward(boolean won) {
        int held = won ? waves : Math.max(0, wave - 1);
        for (ServerPlayer p : online()) {
            List<ItemStack> loot = new ArrayList<>();
            loot.add(new ItemStack(Items.EMERALD, Math.max(1, held * 2)));
            if (held >= 5) loot.add(new ItemStack(Items.DIAMOND, difficulty + held / 5));
            if (held >= 5) loot.add(new ItemStack(dev.abps.content.ModContent.RUBY, Math.max(1, difficulty * held / 6)));
            if (won && difficulty >= 3) loot.add(new ItemStack(Items.NETHERITE_SCRAP, 2));
            if (won) loot.add(new ItemStack(Items.GOLDEN_APPLE, difficulty));
            StringBuilder sb = new StringBuilder();
            for (ItemStack s : loot) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(s.getCount()).append("x ").append(s.getHoverName().getString());
                if (!p.getInventory().add(s.copy())) p.drop(s.copy(), false);
            }
            p.giveExperiencePoints(held * 25);
            AbpsMod.service().send(p, "<gold>Siege rewards:</gold> <white>" + sb);
        }
    }

    @SuppressWarnings("unused")
    private static Component unused() {
        return Component.empty();
    }
}
