package dev.abps.mixin;

import dev.abps.events.DamageHooks;
import dev.abps.util.Targets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /** Class passives change the damage dealt and taken here. */
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float abps$modifyDamage(float amount, ServerLevel level, DamageSource source) {
        return DamageHooks.modify((LivingEntity) (Object) this, source, amount);
    }

    /** Effect immunities, like Vampire darkness. */
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void abps$immunity(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (DamageHooks.immune((LivingEntity) (Object) this, effect)) cir.setReturnValue(false);
    }

    /** Summoned helpers drop nothing. */
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void abps$noMinionLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (((Entity) (Object) this).entityTags().contains(Targets.MINION_TAG)) ci.cancel();
    }

    @Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
    private void abps$noMinionXp(ServerLevel level, Entity killer, CallbackInfo ci) {
        if (((Entity) (Object) this).entityTags().contains(Targets.MINION_TAG)) ci.cancel();
    }
}
