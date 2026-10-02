package dev.abps.dungeon;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One kind of dungeon: how it is played, what it looks like, what lives in it and who waits at the end. */
public record DungeonDef(String id, String name, String blurb, Mode mode, int difficulty, Palette palette, List<EntityType<? extends Mob>> mobs,
                         Boss boss, String title, String icon, int color) {

    public enum Mode {
        /** A fixed run of rooms that ends with a boss. */
        STORY,
        /** A new layout of rooms every time, then a random boss. */
        RANDOM,
        /** One arena, waves that keep getting harder. Scored by the wave you reach. */
        WAVES
    }

    public enum Boss {
        HOLLOW_KING("The Hollow King", 0x64FFDA),
        GLACIAL_WARDEN("The Glacial Warden", 0x80DEEA),
        INFERNAL_COLOSSUS("The Infernal Colossus", 0xFF6D00);

        public final String title;
        public final int color;

        Boss(String title, int color) {
            this.title = title;
            this.color = color;
        }
    }

    /** The blocks a dungeon is built from. */
    public record Palette(BlockState wall, BlockState wallAlt, BlockState floor, BlockState floorAlt, BlockState pillar, BlockState light,
                          BlockState ceiling, BlockState trim) {
    }

    public static final Palette CRYPT = new Palette(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
            Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
            Blocks.SOUL_LANTERN.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
    public static final Palette FROST = new Palette(Blocks.PACKED_ICE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState(),
            Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.QUARTZ_PILLAR.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(),
            Blocks.PACKED_ICE.defaultBlockState(), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
    public static final Palette FORGE = new Palette(Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.BASALT.defaultBlockState(),
            Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.GILDED_BLACKSTONE.defaultBlockState());
    public static final Palette ANCIENT = new Palette(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
            Blocks.SCULK.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.REINFORCED_DEEPSLATE.defaultBlockState(),
            Blocks.OCHRE_FROGLIGHT.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.CHISELED_DEEPSLATE.defaultBlockState());

    public static final Map<String, DungeonDef> ALL = new LinkedHashMap<>();

    private static void add(DungeonDef d) {
        ALL.put(d.id, d);
    }

    static {
        add(new DungeonDef("crypt", "The Hollow Crypt", "Bones, traps and a king who will not stay dead.", Mode.STORY, 1, CRYPT,
                List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.HUSK), Boss.HOLLOW_KING,
                "Crypt Breaker", "minecraft:skeleton_skull", 0x64FFDA));
        add(new DungeonDef("frost", "Frostbound Halls", "Halls of ice where the cold itself hunts you.", Mode.STORY, 2, FROST,
                List.of(EntityTypes.STRAY, EntityTypes.SKELETON, EntityTypes.ZOMBIE, EntityTypes.SILVERFISH), Boss.GLACIAL_WARDEN,
                "Frostbound", "minecraft:blue_ice", 0x80DEEA));
        add(new DungeonDef("forge", "The Infernal Forge", "A furnace of a fortress. Bring fire resistance.", Mode.STORY, 3, FORGE,
                List.of(EntityTypes.WITHER_SKELETON, EntityTypes.BLAZE, EntityTypes.MAGMA_CUBE, EntityTypes.PIGLIN_BRUTE), Boss.INFERNAL_COLOSSUS,
                "Flamewalker", "minecraft:magma_block", 0xFF6D00));
        add(new DungeonDef("depths", "The Shifting Depths", "Different every time you go down. Any boss could be waiting.", Mode.RANDOM, 2, ANCIENT,
                List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.STRAY, EntityTypes.HUSK, EntityTypes.WITCH), null,
                "Delver", "minecraft:sculk", 0xB388FF));
        add(new DungeonDef("arena", "Endless Arena", "Waves that never stop. How long can you last?", Mode.WAVES, 2, CRYPT,
                List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.HUSK, EntityTypes.STRAY, EntityTypes.WITCH,
                        EntityTypes.VINDICATOR), null, "Gladiator", "minecraft:iron_sword", 0xFFD54F));
    }

    public static DungeonDef get(String id) {
        return id == null ? null : ALL.get(id);
    }

    public String stars() {
        return "★".repeat(difficulty) + "☆".repeat(Math.max(0, 3 - difficulty));
    }
}
