package dev.abps.util;

import dev.abps.AbpsMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;

/** Adds and removes attribute modifiers made by this mod. They are transient, so they never save to disk. */
public final class Mods {

    private Mods() {
    }

    public static Identifier key(String id) {
        return AbpsMod.id(id);
    }

    public static void set(LivingEntity e, Holder<Attribute> attribute, String id, double amount, Operation op) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst == null) return;
        Identifier key = key(id);
        AttributeModifier old = inst.getModifier(key);
        if (old != null) {
            if (old.amount() == amount && old.operation() == op) return;
            inst.removeModifier(key);
        }
        inst.addTransientModifier(new AttributeModifier(key, amount, op));
        clampHealth(e, attribute);
    }

    public static void remove(LivingEntity e, Holder<Attribute> attribute, String id) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null && inst.hasModifier(key(id))) {
            inst.removeModifier(key(id));
            clampHealth(e, attribute);
        }
    }

    /** Sets the modifier when on is true, removes it when false. */
    public static void toggle(LivingEntity e, boolean on, Holder<Attribute> attribute, String id, double amount, Operation op) {
        if (on) set(e, attribute, id, amount, op);
        else remove(e, attribute, id);
    }

    /** Removes every modifier this mod added to the entity. */
    public static void clearAll(LivingEntity e) {
        for (Holder<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.asHolderIdMap()) {
            AttributeInstance inst = e.getAttribute(attribute);
            if (inst == null) continue;
            for (AttributeModifier mod : new ArrayList<>(inst.getModifiers())) {
                if (mod.id().getNamespace().equals(AbpsMod.MOD_ID)) inst.removeModifier(mod.id());
            }
        }
        if (e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
    }

    private static void clampHealth(LivingEntity e, Holder<Attribute> attribute) {
        if (attribute == Attributes.MAX_HEALTH && e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
    }

    public static double value(LivingEntity e, Holder<Attribute> attribute) {
        AttributeInstance inst = e.getAttribute(attribute);
        return inst == null ? 0 : inst.getValue();
    }

    public static void setBase(LivingEntity e, Holder<Attribute> attribute, double value) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) inst.setBaseValue(value);
    }

    public static void scaleBase(LivingEntity e, Holder<Attribute> attribute, double factor) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) inst.setBaseValue(inst.getBaseValue() * factor);
    }

    public static final Operation ADD = Operation.ADD_VALUE;
    public static final Operation MULT = Operation.ADD_MULTIPLIED_TOTAL;
}
