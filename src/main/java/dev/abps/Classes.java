package dev.abps;

import dev.abps.classes.Archer;
import dev.abps.classes.Assassin;
import dev.abps.classes.AttributeClass;
import dev.abps.classes.Berserker;
import dev.abps.classes.Chronomancer;
import dev.abps.classes.Cryomancer;
import dev.abps.classes.Paladin;
import dev.abps.classes.Samurai;
import dev.abps.classes.Voidwalker;
import dev.abps.classes.Miner;
import dev.abps.classes.Necromancer;
import dev.abps.classes.Overlord;
import dev.abps.classes.Angler;
import dev.abps.classes.Explorer;
import dev.abps.classes.Harvester;
import dev.abps.classes.Lumberjack;
import dev.abps.classes.Pyromancer;
import dev.abps.classes.Shark;
import dev.abps.classes.Tank;
import dev.abps.classes.Vampire;
import dev.abps.classes.Windwalker;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** All attributes, in menu order. */
public final class Classes {

    private static final Map<String, AttributeClass> CLASSES = new LinkedHashMap<>();

    private Classes() {
    }

    static void init() {
        add(new Miner());
        add(new Vampire());
        add(new Berserker());
        add(new Archer());
        add(new Tank());
        add(new Assassin());
        add(new Pyromancer());
        add(new Windwalker());
        add(new Necromancer());
        add(new Shark());
        add(new Cryomancer());
        add(new Chronomancer());
        add(new Paladin());
        add(new Voidwalker());
        add(new Samurai());
        // Gatherers
        add(new Harvester());
        add(new dev.abps.classes.Chef());
        add(new Lumberjack());
        add(new Angler());
        add(new Explorer());
        add(new Overlord());
    }

    private static void add(AttributeClass c) {
        CLASSES.put(c.id(), c);
    }

    public static AttributeClass get(String id) {
        return id == null ? null : CLASSES.get(id);
    }

    public static Collection<AttributeClass> all() {
        return Collections.unmodifiableCollection(CLASSES.values());
    }

    /** Finds an attribute by id or name, also works with the start of a name. */
    public static AttributeClass find(String input) {
        if (input == null) return null;
        String s = input.toLowerCase(Locale.ROOT).replace(" ", "");
        AttributeClass exact = CLASSES.get(s);
        if (exact != null) return exact;
        for (AttributeClass c : CLASSES.values()) if (c.name().toLowerCase(Locale.ROOT).equals(s)) return c;
        for (AttributeClass c : CLASSES.values()) if (c.id().startsWith(s)) return c;
        return null;
    }
}
