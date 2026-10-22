package io.hookdev.setpoint.client;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.FlareBlockEntity;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** The roar of a burning flare: one long loop from its tip, louder and deeper the bigger the flame, gone when it goes out. */
public class FlareSound extends AbstractTickableSoundInstance {
    private final FlareBlockEntity flare;

    public FlareSound(FlareBlockEntity flare) {
        super(SetpointContent.FLARE_ROAR.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.flare = flare;
        this.looping = true;
        this.delay = 0;
        Vec3 tip = flare.tip();
        x = tip.x;
        y = tip.y + 1;
        z = tip.z;
        follow();
    }

    @Override
    public void tick() {
        if (flare.isRemoved() || !flare.lit()) {
            stop();
            return;
        }
        follow();
    }

    /** A pilot flame hisses quietly; a full flare roars and carries for a long way (sounds.json gives it a long reach). */
    private void follow() {
        float load = flare.shownLoad(1);
        volume = load < 1 ? 0.15f : Math.min(2.0f, 0.4f + load / 250f);
        pitch = Math.max(0.6f, 1.0f - load / 2000f);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
