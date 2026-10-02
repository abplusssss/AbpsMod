package dev.abps.dungeon;

import dev.abps.AbpsMod;
import dev.abps.Teleports;
import dev.abps.util.Fx;
import dev.abps.util.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** One party's trip through one dungeon: its rooms, its mobs, its boss and how it's going. */
public final class Run {

    public enum State { BUILDING, ACTIVE, CLEARED, OVER }

    public enum RoomType { START, COMBAT, ELITE, TRAP, TREASURE, BOSS, ARENA }

    public static final String MOB_TAG = "abps_dungeon";

    /** One room of the dungeon. min is its lowest corner; doors are in the middle of its west and east walls. */
    public static final class Room {
        final RoomType type;
        final BlockPos min;
        final int w, h, d;
        boolean started, cleared;
        final Set<UUID> mobs = new HashSet<>();
        long startedAt;
        int trapStep;

        Room(RoomType type, BlockPos min, int w, int h, int d) {
            this.type = type;
            this.min = min;
            this.w = w;
            this.h = h;
            this.d = d;
        }

        int x1() {
            return min.getX() + w - 1;
        }

        int midZ() {
            return min.getZ() + d / 2;
        }

        int floor() {
            return min.getY();
        }

        Vec3 center() {
            return new Vec3(min.getX() + w / 2.0, floor() + 1, min.getZ() + d / 2.0);
        }

        /** Just inside the west door, where players come back to after being downed. */
        Vec3 checkpoint() {
            return new Vec3(min.getX() + 2.5, floor() + 1, midZ() + 0.5);
        }

        boolean inside(Entity e) {
            Vec3 p = e.position();
            return p.x > min.getX() + 0.5 && p.x < x1() + 0.5 && p.z > min.getZ() + 0.5 && p.z < min.getZ() + d - 0.5
                    && p.y >= floor() - 1 && p.y < floor() + h;
        }
    }

    final int slot;
    public final DungeonDef def;
    final ServerLevel level;
    final BlockPos origin;
    final Random rnd = new Random();
    final LinkedHashSet<UUID> players = new LinkedHashSet<>();
    final Map<UUID, dev.abps.data.PlayerData.Loc> returns = new HashMap<>();
    final List<Room> rooms = new ArrayList<>();
    final Builder builder = new Builder();
    final DungeonDef.Palette palette;
    final DungeonDef.Boss boss;
    final List<EntityType<? extends Mob>> mobs;

    State state = State.BUILDING;
    int current;
    int downs, maxDowns;
    long startedAt, finishedAt;
    int wave;
    long nextWaveAt;
    int partySize;
    BossBrain brain;
    ServerBossEvent bar;
    int ticks;
    final int maxX;

    Run(int slot, DungeonDef def, ServerLevel level, List<ServerPlayer> party) {
        this.slot = slot;
        this.def = def;
        this.level = level;
        this.origin = new BlockPos(1000 + slot * 700, 80, 0);
        this.partySize = party.size();
        for (ServerPlayer p : party) {
            players.add(p.getUUID());
            returns.put(p.getUUID(), Teleports.here(p));
        }
        this.maxDowns = 2 + party.size();
        if (def.mode() == DungeonDef.Mode.RANDOM) {
            DungeonDef.Palette[] pals = {DungeonDef.CRYPT, DungeonDef.FROST, DungeonDef.FORGE, DungeonDef.ANCIENT};
            this.palette = pals[rnd.nextInt(pals.length)];
            DungeonDef.Boss[] bosses = DungeonDef.Boss.values();
            this.boss = bosses[rnd.nextInt(bosses.length)];
        } else {
            this.palette = def.palette();
            this.boss = def.boss();
        }
        this.mobs = def.mobs();
        this.maxX = layout();
    }

    // ------------------------------------------------------------------ layout

    private int layout() {
        List<RoomType> plan = new ArrayList<>();
        switch (def.mode()) {
            case WAVES -> plan.add(RoomType.ARENA);
            case STORY -> {
                plan.addAll(List.of(RoomType.START, RoomType.COMBAT, RoomType.TRAP, RoomType.COMBAT, RoomType.TREASURE, RoomType.ELITE));
                if (def.difficulty() >= 2) plan.add(3, RoomType.COMBAT);
                if (def.difficulty() >= 3) plan.add(6, RoomType.TRAP);
                plan.add(RoomType.BOSS);
            }
            case RANDOM -> {
                plan.add(RoomType.START);
                int n = 4 + rnd.nextInt(4);
                RoomType[] pool = {RoomType.COMBAT, RoomType.COMBAT, RoomType.COMBAT, RoomType.TRAP, RoomType.ELITE, RoomType.TREASURE};
                for (int i = 0; i < n; i++) plan.add(pool[rnd.nextInt(pool.length)]);
                plan.add(RoomType.BOSS);
            }
        }
        int x = origin.getX();
        for (int i = 0; i < plan.size(); i++) {
            RoomType t = plan.get(i);
            int w, h, d;
            switch (t) {
                case START -> { w = 11; h = 7; d = 11; }
                case TRAP -> { w = 23; h = 7; d = 11; }
                case TREASURE -> { w = 11; h = 7; d = 11; }
                case ELITE -> { w = 17; h = 10; d = 17; }
                case BOSS -> { w = 29; h = 14; d = 29; }
                case ARENA -> { w = 33; h = 12; d = 33; }
                default -> {
                    w = 15 + rnd.nextInt(3) * 2;
                    h = 8;
                    d = 15 + rnd.nextInt(3) * 2;
                }
            }
            BlockPos min = new BlockPos(x, origin.getY(), origin.getZ() - d / 2);
            Room r = new Room(t, min, w, h, d);
            rooms.add(r);
            boolean last = i == plan.size() - 1;
            builder.room(min, w, h, d, palette, rnd, i > 0, !last, t != RoomType.START && t != RoomType.TREASURE);
            decorate(r);
            x += w;
            if (!last) {
                builder.corridor(x, origin.getY(), origin.getZ(), 6, palette, rnd);
                x += 6;
            }
        }
        return x;
    }

    /** A few extra details so rooms look different from each other. */
    private void decorate(Room r) {
        int y = r.floor();
        Vec3 c = r.center();
        int cx = (int) Math.floor(c.x), cz = (int) Math.floor(c.z);
        switch (r.type) {
            case TREASURE -> {
                builder.set(cx, y + 1, cz, Blocks.CHEST.defaultBlockState());
                builder.set(cx - 1, y, cz, Blocks.GOLD_BLOCK.defaultBlockState());
                builder.set(cx + 1, y, cz, Blocks.GOLD_BLOCK.defaultBlockState());
                builder.set(cx, y, cz - 1, Blocks.GOLD_BLOCK.defaultBlockState());
                builder.set(cx, y, cz + 1, Blocks.GOLD_BLOCK.defaultBlockState());
            }
            case BOSS, ARENA -> {
                // A raised ring in the middle and a ring of pillars around the edge
                int rr = r.type == RoomType.BOSS ? 5 : 7;
                for (int a = 0; a < 360; a += 6) {
                    int px = cx + (int) Math.round(Math.cos(Math.toRadians(a)) * rr), pz = cz + (int) Math.round(Math.sin(Math.toRadians(a)) * rr);
                    builder.set(px, y, pz, palette.trim());
                }
                int pr = r.w / 2 - 4;
                for (int k = 0; k < 8; k++) {
                    double a = Math.PI * 2 * k / 8;
                    int px = cx + (int) Math.round(Math.cos(a) * pr), pz = cz + (int) Math.round(Math.sin(a) * pr);
                    builder.fill(px, y + 1, pz, px, y + r.h - 2, pz, palette.pillar());
                }
            }
            case COMBAT, ELITE -> {
                for (int k = 0; k < 3; k++) {
                    int px = r.min.getX() + 3 + rnd.nextInt(Math.max(1, r.w - 6)), pz = r.min.getZ() + 3 + rnd.nextInt(Math.max(1, r.d - 6));
                    if (Math.abs(pz - r.midZ()) < 2) continue; // keep the path between the doors clear
                    builder.fill(px, y + 1, pz, px, y + 1 + rnd.nextInt(2), pz, palette.wallAlt());
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ people

    public List<ServerPlayer> online() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : players) {
            ServerPlayer p = Dungeons.player(id);
            if (p != null && p.level() == level) out.add(p);
        }
        return out;
    }

    void tell(String msg) {
        for (ServerPlayer p : online()) AbpsMod.service().send(p, msg);
    }

    void bar(String msg) {
        for (ServerPlayer p : online()) AbpsMod.service().actionBar(p, msg);
    }

    void sound(net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        for (ServerPlayer p : online()) Fx.sound(level, p, s, vol, pitch);
    }

    void sound(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> s, float vol, float pitch) {
        for (ServerPlayer p : online()) Fx.sound(level, p, s, vol, pitch);
    }

    public Room room() {
        return current < rooms.size() ? rooms.get(current) : rooms.getLast();
    }

    void enter(ServerPlayer p) {
        Room first = rooms.getFirst();
        Vec3 at = def.mode() == DungeonDef.Mode.WAVES ? first.center() : first.checkpoint();
        p.teleportTo(level, at.x, at.y, at.z, java.util.Set.<Relative>of(), -90, 0, false);
        p.fallDistance = 0;
        Fx.sound(level, p, SoundEvents.END_PORTAL_SPAWN, 0.5f, 1.4f);
        AbpsMod.service().banner(p, "<bold>" + dev.abps.util.Text.colorTag(def.color()) + def.name() + "</bold>", "<gray>" + def.blurb(), def.color(), 70);
    }

    /** Sends someone back where they came from. */
    void sendHome(ServerPlayer p) {
        dev.abps.data.PlayerData.Loc back = returns.get(p.getUUID());
        if (back == null) back = Teleports.spawn();
        ServerLevel l = Teleports.levelOf(back);
        if (l == null) {
            back = Teleports.spawn();
            l = Teleports.levelOf(back);
        }
        if (bar != null) bar.removePlayer(p);
        Dungeons.hideHud(p);
        p.teleportTo(l, back.x(), back.y(), back.z(), java.util.Set.<Relative>of(), back.yaw(), back.pitch(), false);
        p.fallDistance = 0;
        p.clearFire();
    }

    /** Instead of dying in a dungeon you are downed: healed and sent back to the start of the room. */
    boolean down(ServerPlayer p) {
        downs++;
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.removeAllEffects();
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 4));
        Vec3 at = room().checkpoint();
        if (def.mode() == DungeonDef.Mode.WAVES) at = room().center().add(0, 0, 4);
        p.teleportTo(level, at.x, at.y, at.z, java.util.Set.<Relative>of(), -90, 0, false);
        p.fallDistance = 0;
        int left = maxDowns - downs;
        if (left < 0) {
            tell("<red><bold>Your party has fallen.</bold></red> <gray>The dungeon throws you out.");
            fail();
        } else {
            tell("<red>☠ " + p.getName().getString() + " was downed!</red> <gray>" + left + " down" + (left == 1 ? "" : "s") + " left for the party.");
        }
        return false;
    }

    // ------------------------------------------------------------------ mobs

    double hpScale() {
        return (1 + 0.35 * (def.difficulty() - 1)) * (1 + 0.5 * (partySize - 1)) * (def.mode() == DungeonDef.Mode.WAVES ? 1 + wave * 0.06 : 1);
    }

    double dmgScale() {
        return 1 + 0.2 * (def.difficulty() - 1) + (def.mode() == DungeonDef.Mode.WAVES ? wave * 0.03 : 0);
    }

    /** Spawns a dungeon mob, made tougher for the dungeon's difficulty and the party size. */
    Mob spawn(EntityType<? extends Mob> type, Vec3 at, double hpMult, String name, Room room) {
        Mob mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (mob == null) return null;
        mob.snapTo(at.x, at.y, at.z, rnd.nextFloat() * 360, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.MOB_SUMMONED, null);
        mob.setPersistenceRequired();
        mob.addTag(MOB_TAG);
        double hp = mob.getMaxHealth() * hpScale() * hpMult;
        Mods.setBase(mob, Attributes.MAX_HEALTH, hp);
        mob.setHealth((float) hp);
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) Mods.scaleBase(mob, Attributes.ATTACK_DAMAGE, dmgScale());
        if (name != null) {
            mob.setCustomName(dev.abps.util.Text.mm(name));
            mob.setCustomNameVisible(true);
        }
        level.addFreshEntity(mob);
        if (room != null) room.mobs.add(mob.getUUID());
        List<ServerPlayer> ps = online();
        if (!ps.isEmpty()) mob.setTarget(ps.get(rnd.nextInt(ps.size())));
        Dungeons.cue(this, Dungeons.CUE_SPAWN, at, at.add(0, 1, 0), mob, 0);
        return mob;
    }

    private Vec3 randomSpot(Room r) {
        for (int k = 0; k < 20; k++) {
            double x = r.min.getX() + 3 + rnd.nextDouble() * (r.w - 6), z = r.min.getZ() + 2 + rnd.nextDouble() * (r.d - 4);
            BlockPos at = BlockPos.containing(x, r.floor() + 1, z);
            if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && x > r.min.getX() + r.w * 0.3) return new Vec3(x, r.floor() + 1, z);
        }
        return r.center();
    }

    private EntityType<? extends Mob> pick() {
        return mobs.get(rnd.nextInt(mobs.size()));
    }

    int alive(Room r) {
        r.mobs.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        return r.mobs.size();
    }

    // ------------------------------------------------------------------ the loop

    void tick() {
        ticks++;
        if (state == State.BUILDING) return;
        if (state == State.OVER) return;
        List<ServerPlayer> ps = online();
        if (ps.isEmpty()) {
            // Everyone left or logged off: the run ends
            if (ticks > 40) end(false);
            return;
        }
        if (bar != null && brain != null) {
            LivingEntity b = brain.boss();
            if (b != null) bar.setProgress(Math.max(0, b.getHealth() / b.getMaxHealth()));
        }
        if (state == State.CLEARED) {
            // Step onto the glowing pad in the middle of the boss room to leave, or wait it out
            Vec3 c = room().center();
            for (ServerPlayer p : ps) {
                if (p.position().distanceToSqr(c.x, c.y, c.z) < 2.2 * 2.2) {
                    players.remove(p.getUUID());
                    sendHome(p);
                    AbpsMod.service().send(p, "<gray>You left " + def.name() + ".");
                }
            }
            if (System.currentTimeMillis() - finishedAt > 90_000) end(true);
            return;
        }
        if (def.mode() == DungeonDef.Mode.WAVES) {
            waves(ps);
            return;
        }
        Room r = room();
        if (!r.started) {
            boolean in = false;
            for (ServerPlayer p : ps) in |= r.inside(p);
            if (in || r.type == RoomType.START) start(r);
            return;
        }
        switch (r.type) {
            case START, TREASURE -> clear(r);
            case TRAP -> trap(r, ps);
            case BOSS -> {
                if (brain != null) brain.tick(ps);
                if (brain != null && brain.dead()) win();
            }
            default -> {
                if (alive(r) == 0) clear(r);
            }
        }
    }

    private void start(Room r) {
        r.started = true;
        r.startedAt = System.currentTimeMillis();
        int n = partySize;
        switch (r.type) {
            case COMBAT -> {
                int count = 3 + def.difficulty() + n * 2 + rnd.nextInt(3);
                for (int k = 0; k < count; k++) spawn(pick(), randomSpot(r), 1, null, r);
                bar("<red>⚔ Clear the room!");
                sound(SoundEvents.RAID_HORN, 0.5f, 1.4f);
            }
            case ELITE -> {
                EntityType<? extends Mob> t = pick();
                Mob elite = spawn(t, r.center().add(r.w * 0.2, 0, 0), 4, "<gold><bold>Elite " + t.getDescription().getString() + "</bold>", r);
                if (elite != null) {
                    Mods.setBase(elite, Attributes.SCALE, 1.5);
                    elite.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 600, 0));
                    elite.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, 0));
                    elite.setGlowingTag(true);
                }
                for (int k = 0; k < 2 + n; k++) spawn(pick(), randomSpot(r), 1, null, r);
                bar("<gold>★ An elite guards this room.");
                sound(SoundEvents.WITHER_AMBIENT, 0.6f, 1.2f);
            }
            case TRAP -> {
                for (int k = 0; k < 1 + n; k++) spawn(pick(), randomSpot(r), 1, null, r);
                bar("<yellow>⚠ Watch the floor! Survive and reach the far door.");
            }
            case TREASURE -> {
                for (ServerPlayer p : online()) Loot.treasure(this, p);
                Dungeons.cue(this, Dungeons.CUE_LOOT, r.center(), r.center().add(0, 1, 0), null, 0);
                sound(SoundEvents.CHEST_OPEN, 1f, 1f);
                sound(SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
            }
            case BOSS -> {
                brain = BossBrain.create(this, boss, r);
                bar = new ServerBossEvent(UUID.randomUUID(), dev.abps.util.Text.mm("<bold>" + boss.title + "</bold>"), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
                for (ServerPlayer p : online()) bar.addPlayer(p);
                // Seal the way back in
                Builder.open(level, r.min.getX(), r.floor(), r.midZ());
                for (int dz = -1; dz <= 1; dz++)
                    for (int dy = 1; dy <= 3; dy++) level.setBlock(new BlockPos(r.min.getX(), r.floor() + dy, r.midZ() + dz), Builder.BARS, 3);
                sound(SoundEvents.WITHER_SPAWN, 0.6f, 0.8f);
                for (ServerPlayer p : online()) AbpsMod.service().banner(p, "<bold>" + dev.abps.util.Text.colorTag(boss.color) + boss.title + "</bold>", "<gray>Boss", boss.color, 60);
            }
            default -> {
            }
        }
    }

    private BossEvent.BossBarColor barColor() {
        return switch (boss) {
            case HOLLOW_KING -> BossEvent.BossBarColor.GREEN;
            case GLACIAL_WARDEN -> BossEvent.BossBarColor.BLUE;
            case INFERNAL_COLOSSUS -> BossEvent.BossBarColor.RED;
        };
    }

    private void clear(Room r) {
        if (r.cleared) return;
        r.cleared = true;
        Builder.open(level, r.x1(), r.floor(), r.midZ());
        if (r.type != RoomType.START) {
            Dungeons.cue(this, Dungeons.CUE_CLEAR, r.center(), new Vec3(r.x1(), r.floor() + 2, r.midZ() + 0.5), null, 0);
            sound(SoundEvents.IRON_DOOR_OPEN, 1f, 0.6f);
            sound(SoundEvents.PLAYER_LEVELUP, 0.5f, 1.3f);
            bar("<green>✔ Room cleared. The way ahead is open.");
        }
        current++;
    }

    /** Trap rooms: floor tiles light up, then burst a moment later. Survive long enough and the door opens. */
    private void trap(Room r, List<ServerPlayer> ps) {
        long ms = System.currentTimeMillis() - r.startedAt;
        long length = 16_000 + def.difficulty() * 3000L;
        if (ticks % 30 == 0) {
            // Mark some tiles; they go off 20 ticks later
            List<BlockPos> tiles = new ArrayList<>();
            int count = 10 + def.difficulty() * 4;
            for (int k = 0; k < count; k++) {
                int x = r.min.getX() + 2 + rnd.nextInt(r.w - 4), z = r.min.getZ() + 1 + rnd.nextInt(r.d - 2);
                tiles.add(new BlockPos(x, r.floor(), z));
            }
            // Always put a couple right under the players, so standing still is never safe
            for (ServerPlayer p : ps) if (r.inside(p)) tiles.add(BlockPos.containing(p.getX(), r.floor(), p.getZ()));
            Map<BlockPos, BlockState> before = new HashMap<>();
            for (BlockPos t : tiles) {
                before.putIfAbsent(t, level.getBlockState(t));
                level.setBlock(t, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                Dungeons.cue(this, Dungeons.CUE_TRAP_MARK, Vec3.atBottomCenterOf(t.above()), Vec3.atBottomCenterOf(t.above()), null, 20);
            }
            dev.abps.util.Tasks.later(20, () -> {
                if (state == State.OVER) return;
                for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
                    BlockPos t = e.getKey();
                    level.setBlock(t, e.getValue(), 3);
                    AABB box = new AABB(t.above()).inflate(0.2, 0.6, 0.2);
                    for (ServerPlayer p : online()) {
                        if (p.getBoundingBox().intersects(box)) {
                            p.hurtServer(level, level.damageSources().magic(), 5f + def.difficulty() * 1.5f);
                            dev.abps.util.Targets.velocity(p, new Vec3(0, 0.6, 0));
                        }
                    }
                    Dungeons.cue(this, Dungeons.CUE_TRAP_BURST, Vec3.atBottomCenterOf(t.above()), Vec3.atBottomCenterOf(t.above()), null, 0);
                }
                sound(SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.6f);
            });
        }
        if (ms > length && alive(r) == 0) clear(r);
        else if (ms > length) bar("<yellow>Defeat the last of the guards!");
    }

    // ------------------------------------------------------------------ waves

    private void waves(List<ServerPlayer> ps) {
        Room r = room();
        if (!r.started) {
            r.started = true;
            nextWaveAt = System.currentTimeMillis() + 4000;
            bar("<gold>Wave 1 starts in 4 seconds...");
        }
        if (brain != null) {
            brain.tick(ps);
            if (brain.dead()) {
                brain = null;
                if (bar != null) {
                    bar.removeAllPlayers();
                    bar = null;
                }
            }
        }
        if (alive(r) > 0 || brain != null) return;
        long now = System.currentTimeMillis();
        if (nextWaveAt == 0) {
            nextWaveAt = now + 5000;
            if (wave > 0) {
                bar("<green>✔ Wave " + wave + " cleared!</green> <gray>Next wave in 5 seconds.");
                sound(SoundEvents.PLAYER_LEVELUP, 0.5f, 1.4f);
                if (wave % 5 == 0) for (ServerPlayer p : ps) Loot.wave(this, p);
            }
            return;
        }
        if (now < nextWaveAt) return;
        nextWaveAt = 0;
        wave++;
        sound(SoundEvents.RAID_HORN, 0.6f, 1f + Math.min(0.6f, wave * 0.02f));
        if (wave % 10 == 0) {
            DungeonDef.Boss[] all = DungeonDef.Boss.values();
            DungeonDef.Boss b = all[(wave / 10 - 1) % all.length];
            brain = BossBrain.create(this, b, r);
            bar = new ServerBossEvent(UUID.randomUUID(), dev.abps.util.Text.mm("<bold>" + b.title + "</bold> <gray>wave " + wave), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.NOTCHED_10);
            for (ServerPlayer p : ps) bar.addPlayer(p);
            for (ServerPlayer p : ps) AbpsMod.service().banner(p, "<bold><light_purple>WAVE " + wave + "</bold>", "<gray>" + b.title + " joins the fight", 0xB388FF, 50);
            return;
        }
        int count = 3 + wave + partySize * 2;
        boolean elite = wave % 5 == 0;
        for (int k = 0; k < count; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, rad = 9 + rnd.nextDouble() * 5;
            Vec3 at = r.center().add(Math.cos(a) * rad, 0, Math.sin(a) * rad);
            Mob m = spawn(pick(), at, 1, null, r);
            if (elite && k == 0 && m != null) {
                m.setCustomName(dev.abps.util.Text.mm("<gold><bold>Elite"));
                m.setCustomNameVisible(true);
                Mods.setBase(m, Attributes.SCALE, 1.5);
                Mods.setBase(m, Attributes.MAX_HEALTH, m.getMaxHealth() * 3);
                m.setHealth(m.getMaxHealth());
                m.setGlowingTag(true);
            }
        }
        for (ServerPlayer p : ps) AbpsMod.service().banner(p, "<bold><gold>WAVE " + wave + "</bold>", "<gray>" + count + " enemies", 0xFFD54F, 30);
    }

    // ------------------------------------------------------------------ the end

    private void win() {
        state = State.CLEARED;
        finishedAt = System.currentTimeMillis();
        long time = finishedAt - startedAt;
        Room r = room();
        if (bar != null) bar.removeAllPlayers();
        r.cleared = true;
        // The exit: a ring of glowing blocks around the middle of the boss room
        Vec3 c = r.center();
        int cx = (int) Math.floor(c.x), cz = (int) Math.floor(c.z);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) level.setBlock(new BlockPos(cx + dx, r.floor(), cz + dz), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx, r.floor(), cz), Blocks.BEACON.defaultBlockState(), 3);
        Dungeons.cue(this, Dungeons.CUE_WIN, c, c.add(0, 1, 0), null, 0);
        sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        for (ServerPlayer p : online()) {
            AbpsMod.service().banner(p, "<bold><gradient:#FFD54F:#FF6D00>DUNGEON CLEARED</gradient></bold>", "<gray>" + def.name() + " in "
                    + dev.abps.util.Text.time(time), 0xFFD54F, 80);
            Loot.clear(this, p, time);
        }
        Dungeons.recordClear(this, time);
        tell("<gray>Step onto the light in the middle of the room to leave, or wait 90 seconds.");
    }

    void fail() {
        if (state == State.OVER) return;
        if (def.mode() == DungeonDef.Mode.WAVES) {
            Dungeons.recordWave(this, wave);
            for (ServerPlayer p : online()) AbpsMod.service().banner(p, "<bold><gold>Wave " + wave + "</gold></bold>", "<gray>That's how far you got", 0xFFD54F, 70);
        }
        end(false);
    }

    /** Sends everyone home, removes the mobs and starts tearing the dungeon down. */
    void end(boolean cleared) {
        if (state == State.OVER) return;
        state = State.OVER;
        if (bar != null) bar.removeAllPlayers();
        for (ServerPlayer p : online()) sendHome(p);
        players.clear();
        for (Room r : rooms) {
            for (UUID id : r.mobs) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
        }
        if (brain != null) brain.remove();
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(origin.getX() - 4, origin.getY() - 4, origin.getZ() - 40, maxX + 4, origin.getY() + 30, origin.getZ() + 40),
                e -> !(e instanceof ServerPlayer))) e.discard();
        // Tear down: one big queued fill, a few thousand blocks a tick
        Builder clear = new Builder();
        clear.clear(origin.getX(), origin.getY(), origin.getZ() - 20, maxX + 1, origin.getY() + 15, origin.getZ() + 20);
        Dungeons.teardown(slot, clear);
    }
}
