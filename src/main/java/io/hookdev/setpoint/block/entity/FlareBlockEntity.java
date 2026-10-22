package io.hookdev.setpoint.block.entity;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.advancement.PlantEventTrigger;
import io.hookdev.setpoint.block.FlareBlock;
import io.hookdev.setpoint.block.FlarePartBlock;
import io.hookdev.setpoint.client.ClientHooks;
import io.hookdev.setpoint.gas.GasDraw;
import io.hookdev.setpoint.gas.GasFlow;
import io.hookdev.setpoint.gas.GasRun;
import io.hookdev.setpoint.gas.GasVessel;
import io.hookdev.setpoint.gas.Gases;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * A flare: where a plant burns the gas it cannot keep. Gas reaches it two ways along the flare header: safety valves
 * piped to it send what they blow off, and it takes the excess off every vessel on the header above its set pressure,
 * like a pressure controller to flare. Hydrogen, ammonia and LPG burn; nitrogen and steam just go up the stack.
 * <p>
 * A pilot flame keeps it lit, on a little LPG or hydrogen from its pilot gas line. Without pilot gas it goes out as
 * soon as nothing flows, and gas that comes then goes up unburnt: FLARE OUT. LPG burns sooty, so steam is blown into
 * the flame to burn it clean; without enough steam the flare smokes.
 */
public class FlareBlockEntity extends BlockEntity implements NamedEquipment, Describable {
    /** Set pressures to choose from, in bar: it takes gas off the header above this. */
    public static final double[] SET_PRESSURES = {2, 5, 8, 12};
    /** The pilot burns this much gas a tick, mB. */
    public static final int PILOT_GAS = 1;
    /** Steam it needs per mB of LPG to burn without smoke. */
    public static final double STEAM_PER_LPG = 0.3;
    /** This much LPG a tick without any steam makes the blackest smoke. */
    public static final int SMOKY_LPG = 40;
    /** Burning clean on this much LPG for ten seconds earns the smokeless advancement. */
    public static final int SMOKELESS_LPG = 20;

    private final List<GasRun.End> header = new ArrayList<>();
    private long nextScan;
    private final GasDraw pilotGas = new GasDraw(), steamGas = new GasDraw();
    private final EquipmentName name = new EquipmentName("FL");
    private double setPressure = 8;
    private boolean lit;
    /** What safety valves sent it since its last tick, mB: gas that burns, the LPG of that, and gas that does not. */
    private int reliefBurns, reliefLpg, reliefInert;
    /** Last tick: gas burnt and gas that went up unburnt, mB; how black the smoke is, 0 to 1. */
    private int load, vented, unburnt;
    private float smoke;
    private @Nullable String pilotOn;
    private String status = "PILOT OUT";
    private int smokeless, signal;
    private long burnt;
    private int sentLoad = -1;
    private float sentSmoke;
    private boolean sentLit, sentVent;
    /** Client: the flame as drawn, following what the server says smoothly. */
    private float shownLoad, shownLoadBefore, shownSmoke;
    private boolean venting;

    public FlareBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.FLARE_ENTITY.get(), pos, state);
    }

    public boolean lit() { return lit; }
    /** Gas burnt in the last tick, mB. */
    public int load() { return load; }
    /** Gas that went up the stack unburnt in the last tick, mB (an unlit flare, or nitrogen and steam). */
    public int vented() { return vented; }
    public float smoke() { return smoke; }
    public double setPressure() { return setPressure; }
    /** All the gas it has burnt since it was loaded, mB. */
    public long burnt() { return burnt; }
    public String status() { return status; }
    /** The gas the pilot burns, or null when it has none. */
    public @Nullable String pilotGas() { return pilotOn; }

    public BlockPos stack() {
        return FlareBlock.stack(worldPosition, getBlockState());
    }

    /** The top of the tip, where the flame starts. */
    public Vec3 tip() {
        return Vec3.atBottomCenterOf(stack()).add(0, FlareBlock.TIP_TOP / 16.0, 0);
    }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    public static boolean burns(FluidResource gas) {
        Gases.Gas g = Gases.of(gas);
        return g == Gases.HYDROGEN || g == Gases.AMMONIA || g == Gases.LPG;
    }

    /** A safety valve blows off into the flare header: this gas comes to the flare. */
    public void relieve(FluidResource gas, int amount) {
        if (amount <= 0 || gas.isEmpty()) return;
        if (!burns(gas)) reliefInert += amount;
        else {
            reliefBurns += amount;
            if (Gases.of(gas) == Gases.LPG) reliefLpg += amount;
        }
    }

    /** How tall the flame is, in blocks: a small pilot flame, up to sixteen blocks at full blast. */
    public static float flameHeight(float load) {
        return load <= 0.5f ? 0.9f : (float) Math.min(16, 1.5 + 2.2 * Math.sqrt(load / 20.0));
    }

    public static float flameWidth(float height) {
        return 0.4f + height * 0.3f;
    }

    /** Where the wind blows the flame and the smoke to, in degrees, turning slowly over the days. */
    public static float windYaw(long time) {
        return (float) (200 + 70 * Math.sin(time / 6000.0) + 15 * Math.sin(time / 700.0));
    }

    /** How far the wind leans the flame over, in degrees: more in the rain. */
    public static float windLean(Level level, long time) {
        return (level.isRaining() ? 30 : 14) + 4 * (float) Math.sin(time / 37.0);
    }

    // ---------- server ----------
    public static void serverTick(Level level, BlockPos pos, BlockState state, FlareBlockEntity flare) {
        flare.tick((ServerLevel) level, pos, state);
    }

    private @Nullable GasVessel pilotSource(ServerLevel level) {
        BlockState state = getBlockState();
        GasVessel lpg = pilotGas.fullest(level, worldPosition, FlareBlock.pilot(state), Gases.LPG.resource());
        return lpg != null ? lpg : pilotGas.fullest(level, worldPosition, FlareBlock.pilot(state), Gases.HYDROGEN.resource());
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        long time = level.getGameTime();
        if (time >= nextScan) {
            nextScan = time + 20;
            header.clear();
            header.addAll(GasRun.traceAll(level, pos, FlareBlock.header(state)));
        }
        int burnable = reliefBurns, lpg = reliefLpg, inert = reliefInert;
        reliefBurns = reliefLpg = reliefInert = 0;
        // the excess off every vessel on the header, faster the higher it stands over the set pressure
        for (GasRun.End end : header) {
            GasVessel vessel = end.vessel(level);
            if (vessel == null || vessel.amount() == 0 || vessel.pressure() <= setPressure) continue;
            int excess = vessel.amount() - (int) Math.ceil(setPressure * vessel.volume());
            int rate = (int) Math.ceil(GasFlow.CONDUCTANCE * (vessel.pressure() - setPressure) / (1 + end.length() / GasFlow.HALF_LENGTH));
            FluidResource gas = vessel.gas();
            int got = excess > 0 ? vessel.extract(Math.min(excess, rate)) : 0;
            if (got == 0) continue;
            if (!burns(gas)) inert += got;
            else {
                burnable += got;
                if (Gases.of(gas) == Gases.LPG) lpg += got;
            }
        }
        // the pilot, on gas of its own
        GasVessel pilot = pilotSource(level);
        boolean pilotBurns = lit && pilot != null && pilot.amount() >= PILOT_GAS;
        pilotOn = pilot == null ? null : Gases.of(pilot.gas()) == Gases.LPG ? "LPG" : "HYDROGEN";
        if (pilotBurns) pilot.extract(PILOT_GAS);
        if (lit && !pilotBurns && burnable == 0) { // nothing left to keep it alight
            lit = false;
            level.playSound(null, BlockPos.containing(tip()), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 2.0f, 0.6f);
            setChanged();
        }
        load = lit ? burnable : 0;
        burnt += load;
        unburnt = lit ? 0 : burnable;
        vented = inert + unburnt;
        // steam into the flame burns the LPG clean
        float target = 0;
        if (lit && lpg > 0) {
            int need = (int) Math.ceil(lpg * STEAM_PER_LPG);
            int got = steamGas.take(level, pos, FlareBlock.steam(state), Gases.STEAM.resource(), need);
            target = (float) ((1 - got / (double) need) * Math.min(1, lpg / (double) SMOKY_LPG));
        }
        smoke += (target - smoke) * 0.1f;
        if (smoke < 0.01f) smoke = target;
        smokeless = lit && lpg >= SMOKELESS_LPG && smoke < 0.05f ? smokeless + 1 : 0;
        if (smokeless == 200) PlantEventTrigger.near(level, pos, PlantEventTrigger.SMOKELESS);

        status = lit ? (load > 0 ? (smoke > 0.3f ? "SMOKING" : "BURNING") : "PILOT ON")
                : unburnt > 0 ? "FLARE OUT" : pilot != null ? "PILOT OUT" : "NO PILOT GAS";
        if (lit && time % 10 == 0) burnInFlame(level);
        if (lit != tipLit(level, state)) lightTip(level, state);
        int now = comparatorSignal();
        if (now != signal) {
            signal = now;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
        sync(level);
    }

    /** What a comparator reads: 1 for the pilot alone, up to 15 at a thousand mB a tick. */
    public int comparatorSignal() {
        return lit ? Math.clamp((int) Math.ceil(load / 1000.0 * 14) + 1, 1, 15) : 0;
    }

    /** Whatever stands in the flame catches fire. */
    private void burnInFlame(ServerLevel level) {
        float h = flameHeight(load), w = flameWidth(h);
        Vec3 tip = tip();
        AABB flame = new AABB(tip.x - w / 2, tip.y, tip.z - w / 2, tip.x + w / 2, tip.y + h, tip.z + w / 2);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, flame)) {
            if (!entity.fireImmune()) entity.igniteForSeconds(5);
        }
    }

    private boolean tipLit(ServerLevel level, BlockState state) {
        BlockState tip = level.getBlockState(FlareBlock.cell(worldPosition, state, 0, 0, FlareBlock.HIGH - 1));
        return tip.getBlock() instanceof FlarePartBlock && tip.getValue(FlarePartBlock.LIT);
    }

    /** The top of the stack glows while it burns. */
    private void lightTip(ServerLevel level, BlockState state) {
        for (int y = FlareBlock.LIT_FROM; y < FlareBlock.HIGH; y++) {
            BlockPos at = FlareBlock.cell(worldPosition, state, 0, 0, y);
            BlockState part = level.getBlockState(at);
            if (part.getBlock() instanceof FlarePartBlock && part.getValue(FlarePartBlock.TIP)) {
                level.setBlock(at, part.setValue(FlarePartBlock.LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Players see the flame: tell them when it changes enough to see. */
    private void sync(ServerLevel level) {
        boolean vent = vented > 0;
        boolean jump = lit != sentLit || vent != sentVent;
        boolean drift = Math.abs(load - sentLoad) > Math.max(2, sentLoad * 0.1) || Math.abs(smoke - sentSmoke) > 0.08f;
        if (!jump && !(drift && level.getGameTime() % 10 == 0)) return;
        sentLit = lit;
        sentVent = vent;
        sentLoad = load;
        sentSmoke = smoke;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Right-click while it is out: the igniter sends a flame up to the pilot. It needs something to burn. */
    public void ignite(Player player) {
        if (!(level instanceof ServerLevel server) || lit) return;
        if (pilotSource(server) == null && unburnt == 0) {
            player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("NO PILOT GAS").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("  (pipe LPG or hydrogen to its left side)").withStyle(ChatFormatting.DARK_GRAY)));
            return;
        }
        lit = true;
        setChanged();
        server.playSound(null, worldPosition, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
        server.playSound(null, BlockPos.containing(tip()), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 4.0f, 0.6f);
        PlantEventTrigger.near(server, worldPosition, PlantEventTrigger.FLARE_LIT);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("IGNITED").withStyle(ChatFormatting.WHITE)), true);
        }
        sync(server);
    }

    public void setSetPressure(double bar) {
        setPressure = bar;
        setChanged();
    }

    /** Sneak-right-click: the next set pressure. */
    public void nextSetPressure(Player player) {
        int next = 0;
        for (int i = 0; i < SET_PRESSURES.length; i++) if (SET_PRESSURES[i] == setPressure) next = (i + 1) % SET_PRESSURES.length;
        setPressure = SET_PRESSURES[next];
        setChanged();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA).append(Component.literal(String.format(
                    "takes gas off the header above %.0f bar", setPressure)).withStyle(ChatFormatting.WHITE)), true);
        }
    }

    @Override
    public void describe(Player player) {
        ChatFormatting colour = status.equals("FLARE OUT") ? ChatFormatting.LIGHT_PURPLE
                : status.equals("BURNING") || status.equals("PILOT ON") ? ChatFormatting.WHITE : ChatFormatting.GOLD;
        String detail = String.format("burning %d mB/t, venting %d mB/t, pilot on %s, set %.0f bar  (sneak-right-click to change)",
                load, vented, pilotOn == null ? "nothing" : pilotOn, setPressure);
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(colour))
                .append(Component.literal(detail).withStyle(ChatFormatting.GRAY)));
    }

    // ---------- client ----------
    public static void clientTick(Level level, BlockPos pos, BlockState state, FlareBlockEntity flare) {
        flare.shownLoadBefore = flare.shownLoad;
        flare.shownLoad += (flare.load - flare.shownLoad) * 0.08f;
        flare.shownSmoke += (flare.smoke - flare.shownSmoke) * 0.05f;
        flare.particles(level);
        ClientHooks.flareTick(flare);
    }

    /** The flame as drawn, between the last tick and this one. */
    public float shownLoad(float partialTicks) {
        return shownLoadBefore + (shownLoad - shownLoadBefore) * partialTicks;
    }

    public float shownSmoke() { return shownSmoke; }
    public boolean venting() { return venting; }

    /** Sparks and smoke out of the flame, gas out of a dead tip; seen from far away, like the flame itself. */
    private void particles(Level level) {
        RandomSource random = level.getRandom();
        long time = level.getGameTime();
        Vec3 tip = tip();
        double yaw = Math.toRadians(windYaw(time)), wx = -Math.sin(yaw), wz = Math.cos(yaw);
        if (lit) {
            float h = flameHeight(shownLoad);
            if (random.nextFloat() < 0.3f + shownLoad / 400f) {
                level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, tip.x + random.nextGaussian() * 0.2, tip.y + 0.2, tip.z + random.nextGaussian() * 0.2,
                        wx * 0.02, 0.04, wz * 0.02);
            }
            if (shownLoad > 50 && random.nextFloat() < shownLoad / 2000f) {
                level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, tip.x, tip.y + h * 0.3, tip.z, 0, 0, 0);
            }
            if (shownSmoke > 0.05f) {
                double top = tip.y + h * 0.85;
                for (int k = 0; k < Math.ceil(shownSmoke * 3); k++) {
                    if (random.nextFloat() > shownSmoke) continue;
                    level.addAlwaysVisibleParticle(k == 0 ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.LARGE_SMOKE, true,
                            tip.x + wx * h * 0.3 + random.nextGaussian() * 0.4, top, tip.z + wz * h * 0.3 + random.nextGaussian() * 0.4,
                            wx * 0.06, 0.08, wz * 0.06);
                }
            }
        } else if (venting && random.nextFloat() < 0.6f) {
            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, tip.x + random.nextGaussian() * 0.1, tip.y + 0.1, tip.z + random.nextGaussian() * 0.1,
                    wx * 0.03, 0.12, wz * 0.03);
        }
    }

    // ---------- saving, and what players get ----------
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("set_pressure", setPressure);
        output.putBoolean("lit", lit);
        output.putInt("load", load);
        output.putFloat("smoke", smoke);
        output.putBoolean("venting", vented > 0);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        setPressure = input.getDoubleOr("set_pressure", 8);
        lit = input.getBooleanOr("lit", false);
        load = input.getIntOr("load", 0);
        smoke = input.getFloatOr("smoke", 0);
        venting = input.getBooleanOr("venting", false);
        name.load(input);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
