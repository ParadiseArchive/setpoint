package io.hookdev.setpoint.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.hookdev.setpoint.Setpoint;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The advancement trigger as Minecraft 26.1.2 wants it; PlantEventTrigger has the events. */
public abstract class PlantEventTriggerBase extends SimpleCriterionTrigger<PlantEventTriggerBase.TriggerInstance> {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Setpoint.MODID);
    public static final DeferredHolder<CriterionTrigger<?>, PlantEventTrigger> PLANT_EVENT = TRIGGERS.register("plant_event", PlantEventTrigger::new);

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String event) {
        trigger(player, instance -> instance.event().map(event::equals).orElse(true));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<String> event) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.optionalFieldOf("event").forGetter(TriggerInstance::event)
        ).apply(i, TriggerInstance::new));
    }
}
