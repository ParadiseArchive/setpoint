package io.hookdev.setpoint.block.entity;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointConfig;
import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.BoilerBlock;
import io.hookdev.setpoint.hydraulics.PressureVessel;
import io.hookdev.setpoint.hydraulics.Vessel;
import io.hookdev.setpoint.menu.BoilerMenu;
import io.hookdev.setpoint.physics.BoilerModel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Coal-fired boiler: a firebox with a fuel slot under a water drum. Pump water into the drum, burn fuel,
 * and steam collects in the boiler for a turbine next to it. The drum is a vessel, so a controller placed
 * against it holds the drum level by itself.
 * <p>
 * The steam is held in the closed drum, so it builds pressure (see {@link BoilerModel}). A high-pressure trip stops
 * the burner before the design pressure; a safety valve on the drum vents it; a pressure controller (PIC) can
 * hold the pressure by turning the burner up and down, like the boiler master on a real plant.
 */
public class BoilerBlockEntity extends BlockEntity implements MenuProvider, Vessel, PressureVessel, NamedEquipment, io.hookdev.setpoint.gas.GasPortBlock.Ports {
    private final EquipmentName name = new EquipmentName("B");

    public static final int WATER_CAPACITY = 8_000;
    /** Room for steam: up to the range of a pressure transmitter. The drum bursts well before it is full. */
    public static final int STEAM_CAPACITY = (int) (BoilerModel.PRESSURE_RANGE * BoilerModel.STEAM_PER_BAR);
    /** A firing rate from a controller holds this long; without one the burner fires flat out. */
    public static final int COMMAND_TICKS = 40;

    // Values shown on the screen (synced as shorts, all well below 32767)
    public static final int DATA_WATER = 0, DATA_STEAM = 1, DATA_TEMPERATURE = 2, DATA_BURN_LEFT = 3, DATA_BURN_TOTAL = 4,
            DATA_FLAGS = 5, DATA_BOILED = 6, DATA_RATE = 7, DATA_COUNT = 8;
    public static final int FLAG_FIRING = 1, FLAG_TRIPPED = 2, FLAG_OVERHEAT = 4, FLAG_PROTECTED = 8, FLAG_HP_TRIPPED = 16,
            FLAG_HP_PROTECTED = 32, FLAG_COMMANDED = 64;

    private final FluidStacksResourceHandler water = new FluidStacksResourceHandler(1, WATER_CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.isEmpty() || resource.getFluid().isSame(Fluids.WATER);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    };

    private final ItemStacksResourceHandler fuel = new ItemStacksResourceHandler(1) {
        @Override
        public boolean isValid(int index, ItemResource resource) {
            return !resource.isEmpty() && accepts(resource.toStack());
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };

    private double temperature = BoilerModel.AMBIENT;
    private int steam;
    private int burnLeft;
    private int burnTotal;
    private boolean firing;
    private boolean tripped;
    private int boiled;
    private boolean pressureTripped;
    private double rate = 1.0; // firing rate asked for by a pressure controller, 0 to 1
    private long rateUntil = -1;
    private double steamCredit, fuelCredit; // fractions of a mB and of a burn tick at part fire
    /** Oxygen for the burner, from a gas line at the back: this much a tick at full fire, and then the coal lasts longer. */
    public static final int OXYGEN_PER_TICK = 5;
    public static final double OXYGEN_FUEL_SAVING = 1.5;
    private final io.hookdev.setpoint.gas.GasDraw oxygen = new io.hookdev.setpoint.gas.GasDraw();
    private boolean enriched;
    private @Nullable Boolean protectionOverride; // game tests switch the trips off without touching the config
    private @Nullable Boolean pressureTripOverride;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_WATER -> amount();
                case DATA_STEAM -> steam;
                case DATA_TEMPERATURE -> (int) Math.round(temperature * 10);
                case DATA_BURN_LEFT -> Math.min(Short.MAX_VALUE, burnLeft);
                case DATA_BURN_TOTAL -> Math.min(Short.MAX_VALUE, burnTotal);
                case DATA_FLAGS -> (firing ? FLAG_FIRING : 0) | (tripped ? FLAG_TRIPPED : 0)
                        | (temperature > BoilerModel.OVERHEAT ? FLAG_OVERHEAT : 0) | (protection() ? FLAG_PROTECTED : 0)
                        | (pressureTripped ? FLAG_HP_TRIPPED : 0) | (pressureProtection() ? FLAG_HP_PROTECTED : 0) | (commanded() ? FLAG_COMMANDED : 0);
                case DATA_BOILED -> boiled;
                case DATA_RATE -> (int) Math.round(firingRate() * 100);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.BOILER_ENTITY.get(), pos, state);
    }

    public static boolean accepts(ItemStack stack) {
        // Lava buckets would leave a bucket behind; solid fuels only, like the Stirling engine.
        return stack.has(DataComponents.COOKING_FUEL) && !stack.is(Items.LAVA_BUCKET);
    }

    // ---------- vessel: the water drum ----------
    @Override public FluidStacksResourceHandler fluid() { return water; }
    @Override public FluidResource resource() { return water.getResource(0); }
    @Override public int amount() { return water.getAmountAsInt(0); }
    @Override public double fillFraction() { return amount() / (double) WATER_CAPACITY; }

    @Override
    public boolean canAccept(FluidResource incoming) {
        return amount() < WATER_CAPACITY && incoming.getFluid().isSame(Fluids.WATER);
    }

    public ItemStacksResourceHandler fuel() { return fuel; }
    public ContainerData data() { return data; }
    public int steam() { return steam; }
    public double temperature() { return temperature; }
    public boolean firing() { return firing; }
    /** Firing with oxygen: a hotter flame that gets more steam out of the coal. */
    public boolean enriched() { return enriched; }
    public boolean tripped() { return tripped; }
    public boolean pressureTripped() { return pressureTripped; }
    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    @Override public double pressure() { return BoilerModel.pressure(steam); }
    @Override public double designPressure() { return BoilerModel.DESIGN_PRESSURE; }

    /** A safety valve lets steam out. */
    @Override
    public int vent(int amount) {
        return takeSteam(amount);
    }

    public boolean pressureProtection() {
        return pressureTripOverride != null ? pressureTripOverride : SetpointConfig.boilerHighPressureTrip();
    }

    public void setPressureTripOverride(@Nullable Boolean on) {
        pressureTripOverride = on;
    }

    /** A pressure controller sets the firing rate, 0 to 1. It holds for a moment; then the burner goes back to full fire. */
    public void command(double rate, long gameTime) {
        this.rate = Math.max(0, Math.min(1, rate));
        this.rateUntil = gameTime + COMMAND_TICKS;
    }

    public boolean commanded() {
        return level != null && level.getGameTime() <= rateUntil;
    }

    /** How hard the burner fires when it fires, 0 to 1. */
    public double firingRate() {
        return commanded() ? rate : 1.0;
    }

    public void setSteam(int steam) {
        this.steam = Math.max(0, Math.min(STEAM_CAPACITY, steam));
    }

    public boolean protection() {
        return protectionOverride != null ? protectionOverride : SetpointConfig.boilerLowLevelTrip();
    }

    public void setProtectionOverride(@Nullable Boolean on) {
        protectionOverride = on;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    /** The drum's steam as a gas vessel, for the steam outlet on top: it gives steam to the line and takes none. */
    private final io.hookdev.setpoint.gas.GasVessel steamOutlet = new io.hookdev.setpoint.gas.GasVessel() {
        @Override public FluidResource gas() { return steam > 0 ? io.hookdev.setpoint.gas.Gases.STEAM.resource() : FluidResource.EMPTY; }
        @Override public int amount() { return steam; }
        @Override public int volume() { return BoilerModel.STEAM_PER_BAR; }
        @Override public double maxPressure() { return BoilerModel.DESIGN_PRESSURE; }
        @Override public boolean canTake(FluidResource gas) { return false; }
        @Override public int insert(FluidResource gas, int amount) { // only steam that did not fit somewhere comes back
            if (!io.hookdev.setpoint.gas.Gases.STEAM.resource().equals(gas)) return 0;
            setSteam(steam + amount);
            return amount;
        }
        @Override public int extract(int amount) { return takeSteam(amount); }
    };
    private final io.hookdev.setpoint.gas.GasFlow steamFlow = new io.hookdev.setpoint.gas.GasFlow();

    @Override
    public io.hookdev.setpoint.gas.@Nullable GasVessel port(Direction side) {
        return side == Direction.UP ? steamOutlet : null;
    }

    /** Steam let into the gas line on top in the last tick, mB. */
    public int steamOut() {
        return steamFlow.lastFlow();
    }

    /** A turbine takes steam out of the boiler. Returns how much it got. */
    public int takeSteam(int wanted) {
        int taken = Math.max(0, Math.min(wanted, steam));
        steam -= taken;
        if (taken > 0) setChanged();
        return taken;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity boiler) {
        boiler.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        steamFlow.tick(level, pos, steamOutlet, Direction.UP); // steam into a gas pipe on top, if there is one
        int waterNow = amount();
        boolean protect = protection();

        // Cold water on a red-hot dry drum: it all turns to steam at once.
        if (!protect && BoilerModel.flashes(temperature, waterNow)) {
            burst(level, pos);
            return;
        }

        tripped = protect && BoilerModel.tripped(tripped, fillFraction());
        pressureTripped = pressureProtection() && BoilerModel.pressureTripped(pressureTripped, pressure());
        double fire = firingRate();
        boolean wantFire = !tripped && !pressureTripped && fire > 0.005 && !io.hookdev.setpoint.control.Esd.tripped(level, pos); // an ESD puts the fire out

        if (burnLeft <= 0 && wantFire) {
            ItemResource resource = fuel.getResource(0);
            if (!resource.isEmpty()) {
                int ticks = burnTicks(level, resource.toStack());
                try (Transaction transaction = Transaction.openRoot()) {
                    if (fuel.extract(0, resource, 1, transaction) == 1) {
                        transaction.commit();
                        burnLeft = ticks;
                        burnTotal = ticks;
                    }
                }
            }
        }

        firing = burnLeft > 0 && wantFire;
        enriched = false;
        if (firing) { // with oxygen from the line at the back the flame is hotter: the coal lasts half as long again
            int need = (int) Math.ceil(OXYGEN_PER_TICK * fire);
            var o2 = oxygen.fullest(level, pos, state.getValue(BoilerBlock.FACING).getOpposite(), io.hookdev.setpoint.gas.Gases.OXYGEN.resource());
            if (o2 != null && o2.amount() >= need) {
                o2.extract(need);
                enriched = true;
            }
        }
        if (firing) { // at part fire the coal lasts longer and makes steam slower
            fuelCredit += enriched ? fire / OXYGEN_FUEL_SAVING : fire;
            while (fuelCredit >= 1 && burnLeft > 0) { burnLeft--; fuelCredit--; }
            steamCredit += BoilerModel.STEAM_RATE * fire;
        }

        temperature = BoilerModel.nextTemperature(temperature, firing, waterNow);
        int rateNow = (int) Math.floor(steamCredit);
        boiled = BoilerModel.boil(temperature, firing, waterNow, STEAM_CAPACITY - steam, rateNow);
        steamCredit = temperature < BoilerModel.BOILING || !firing ? 0 : steamCredit - boiled;
        if (steamCredit > BoilerModel.STEAM_RATE) steamCredit = BoilerModel.STEAM_RATE;
        if (boiled > 0) {
            water.set(0, waterNow - boiled == 0 ? FluidResource.EMPTY : FluidResource.of(Fluids.WATER), waterNow - boiled);
            steam += boiled;
        }
        if (BoilerModel.bursts(temperature) || BoilerModel.burstsFromPressure(pressure())) {
            burst(level, pos);
            return;
        }

        if (state.getValue(BoilerBlock.LIT) != firing) {
            level.setBlock(pos, state.setValue(BoilerBlock.LIT, firing), Block.UPDATE_CLIENTS);
        }
        effects(level, pos);
        if (level.getGameTime() % 20 == 0) setChanged();
    }

    private int burnTicks(ServerLevel level, ItemStack stack) {
        LootContext context = new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, getBlockState())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .create(LootContextParamSets.BLOCK_INTERACT))
                .create(Optional.empty());
        return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
    }

    private void effects(ServerLevel level, BlockPos pos) {
        long time = level.getGameTime();
        if (firing && (time + pos.hashCode()) % 30 == 0 && level.getRandom().nextFloat() < 0.6f) {
            level.playSound(null, pos, SetpointContent.FIRE_CRACKLE.get(), SoundSource.BLOCKS, 0.3f, 0.9f);
        }
        if (firing && time % 10 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 1, 0.1, 0.02, 0.1, 0.01);
        }
        if (boiled > 0 && time % 40 == 0) {
            level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.25f, 0.8f);
        }
        if (pressure() > BoilerModel.DESIGN_PRESSURE + 1 && time % 10 == 0) {
            // over its design pressure: the drum groans before it gives way
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.3f, 0.5f);
        }
        if (temperature > BoilerModel.OVERHEAT && time % 6 == 0) {
            // A dry drum glowing hot: a warning before it gives way.
            level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.2, 0.05, 0.2, 0.0);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 0.6f);
        }
    }

    private void burst(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0f, Level.ExplosionInteraction.BLOCK);
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.8, 0.8, 0.8, 0.1);
    }

    /** Puts as much of the held stack in the fuel slot as fits. Returns how many went in. */
    public int insertFromHand(ItemStack stack) {
        if (stack.isEmpty() || !accepts(stack)) return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = fuel.insert(0, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(tag());
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BoilerMenu(containerId, inventory, this);
    }

    /** Drop the fuel when the block is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            ItemStack stack = fuel.getResource(0).toStack(fuel.getAmountAsInt(0));
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        water.serialize(output);
        fuel.serialize(output);
        output.putDouble("temperature", temperature);
        output.putInt("steam", steam);
        output.putInt("burn_ticks", burnLeft);
        output.putInt("burn_ticks_total", burnTotal);
        output.putBoolean("tripped", tripped);
        output.putBoolean("pressure_tripped", pressureTripped);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        water.deserialize(input);
        fuel.deserialize(input);
        temperature = input.getDoubleOr("temperature", BoilerModel.AMBIENT);
        steam = input.getIntOr("steam", 0);
        burnLeft = input.getIntOr("burn_ticks", 0);
        burnTotal = input.getIntOr("burn_ticks_total", burnLeft);
        tripped = input.getBooleanOr("tripped", false);
        pressureTripped = input.getBooleanOr("pressure_tripped", false);
        name.load(input);
    }
}
