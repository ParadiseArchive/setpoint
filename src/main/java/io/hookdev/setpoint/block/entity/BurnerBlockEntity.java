package io.hookdev.setpoint.block.entity;

import java.util.Map;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.GasMakerBlock;
import io.hookdev.setpoint.gas.GasDraw;
import io.hookdev.setpoint.gas.GasVessel;
import io.hookdev.setpoint.gas.Gases;
import io.hookdev.setpoint.gas.Liquids;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * The oil and gas burner under a boiler. Its fuel tank takes any liquid fuel from the distillation column (or crude
 * oil); gas comes from a gas line. The boiler above asks it for fire every tick it has no coal burning.
 */
public class BurnerBlockEntity extends BlockEntity implements Vessel, NamedEquipment, Describable {
    public static final int CAPACITY = 4_000;
    /**
     * What the burner uses a tick at full fire. Liquids in mB: three diesel a tick boils as much steam as a coal fire,
     * which a turbine turns into the same power per mB as the diesel generator. Gases in mB at 1 bar.
     */
    private static final Map<Fluid, Integer> LIQUIDS = Map.of(
            Liquids.CRUDE_OIL.fluid().get(), 5, Liquids.NAPHTHA.fluid().get(), 3, Liquids.KEROSENE.fluid().get(), 3,
            Liquids.DIESEL.fluid().get(), 3, Liquids.RESIDUE.fluid().get(), 4);
    public static final int LPG_PER_TICK = 40, HYDROGEN_PER_TICK = 300;

    private final FluidStacksResourceHandler fuel = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.isEmpty() || LIQUIDS.containsKey(resource.getFluid());
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    };
    private final GasDraw[] lines = { new GasDraw(), new GasDraw(), new GasDraw() };
    private final EquipmentName name = new EquipmentName("B");
    private double credit; // a fraction of a mB at part fire
    private long lastBurn = -10;
    private String status = "IDLE";

    public BurnerBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.BURNER_ENTITY.get(), pos, state);
    }

    /** What a liquid fuel takes a tick at full fire, or 0 when it does not burn here. */
    public static int liquidPerTick(Fluid fluid) {
        return LIQUIDS.getOrDefault(fluid, 0);
    }

    /** BURNING, NO FUEL, IDLE (nothing asks for fire), NO BOILER. */
    public String status() { return status; }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    /**
     * The boiler above wants this much fire (0 to 1) for one tick. Burns liquid fuel first, then LPG, then hydrogen
     * from the gas lines; returns whether it burned.
     */
    public boolean burn(ServerLevel level, double fire) {
        int perTick = liquidPerTick(resource().getFluid());
        if (perTick > 0) {
            double wanted = credit + perTick * fire;
            int whole = (int) Math.floor(wanted);
            if (amount() >= whole) {
                credit = wanted - whole;
                int left = amount() - whole;
                fuel.set(0, left > 0 ? resource() : FluidResource.EMPTY, left);
                return burning(level, "BURNING " + resource().getFluid().getFluidType().getDescription().getString().toUpperCase());
            }
        }
        BlockState state = getBlockState();
        Direction front = state.getValue(GasMakerBlock.FACING);
        Direction[] sides = { front.getOpposite(), front.getClockWise(), front.getCounterClockWise() };
        for (var gas : new Gases.Gas[] { Gases.LPG, Gases.HYDROGEN }) {
            int need = (int) Math.ceil((gas == Gases.LPG ? LPG_PER_TICK : HYDROGEN_PER_TICK) * fire);
            for (int i = 0; i < sides.length; i++) {
                GasVessel from = lines[i].fullest(level, worldPosition, sides[i], gas.resource());
                if (from != null && from.amount() >= need) {
                    from.extract(need);
                    return burning(level, gas == Gases.LPG ? "BURNING LPG" : "BURNING HYDROGEN");
                }
            }
        }
        status = "NO FUEL";
        return false;
    }

    private boolean burning(ServerLevel level, String what) {
        status = what;
        lastBurn = level.getGameTime();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BurnerBlockEntity burner) {
        boolean lit = level.getGameTime() - burner.lastBurn < 2;
        if (!lit && !(level.getBlockEntity(pos.above()) instanceof BoilerBlockEntity)) burner.status = "NO BOILER";
        else if (!lit && !burner.status.equals("NO FUEL")) burner.status = "IDLE";
        if (state.getValue(GasMakerBlock.RUNNING) != lit) level.setBlock(pos, state.setValue(GasMakerBlock.RUNNING, lit), Block.UPDATE_CLIENTS);
        // residue and crude burn dirty: a little black smoke out of the sides
        if (lit && level.getGameTime() % 8 == 0 && level instanceof ServerLevel server && (burner.status.contains("RESIDUE") || burner.status.contains("CRUDE"))) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 1, 0.4, 0.1, 0.4, 0.01);
        }
    }

    @Override
    public void describe(Player player) {
        ChatFormatting colour = status.startsWith("BURNING") ? ChatFormatting.WHITE : ChatFormatting.GOLD;
        String tank = amount() > 0 ? String.format("%s %.1f B", resource().getFluid().getFluidType().getDescription().getString().toLowerCase(), amount() / 1000.0) : "tank empty";
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(colour))
                .append(Component.literal(tank).withStyle(ChatFormatting.GRAY)));
    }

    // ---------- the fuel tank: a vessel a pump fills with liquid fuel ----------
    @Override public FluidStacksResourceHandler fluid() { return fuel; }
    @Override public FluidResource resource() { return fuel.getResource(0); }
    @Override public int amount() { return fuel.getAmountAsInt(0); }
    @Override public double fillFraction() { return amount() / (double) CAPACITY; }

    @Override
    public boolean canAccept(FluidResource incoming) {
        return liquidPerTick(incoming.getFluid()) > 0 && (amount() == 0 || incoming.equals(resource())) && amount() < CAPACITY;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fuel.serialize(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fuel.deserialize(input);
        name.load(input);
    }
}
