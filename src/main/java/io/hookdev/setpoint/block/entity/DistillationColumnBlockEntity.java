package io.hookdev.setpoint.block.entity;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.ColumnPartBlock;
import io.hookdev.setpoint.block.DistillationColumnBlock;
import io.hookdev.setpoint.gas.GasDraw;
import io.hookdev.setpoint.gas.GasFlow;
import io.hookdev.setpoint.gas.GasTank;
import io.hookdev.setpoint.gas.Gases;
import io.hookdev.setpoint.gas.Liquids;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A crude oil distillation column. Crude oil comes in at the feed; the reboiler heats it with steam, the vapour rises
 * up the trays and the condenser on top cools it with water. What boils at each height comes out there: LPG (a gas) at
 * the top, then naphtha, kerosene and diesel, and the heavy residue at the bottom, in the shares of a real crude unit.
 * It stops when it runs out of feed, steam or cooling water, or when one of its products has nowhere to go.
 */
public class DistillationColumnBlockEntity extends BlockEntity implements Vessel, NamedEquipment, Describable {
    /** The most crude oil it takes in a tick, in mB; the steam and cooling water each needs per mB of crude. */
    public static final int MAX_FEED = 20;
    public static final double STEAM_PER_CRUDE = 0.5, WATER_PER_CRUDE = 0.5;
    /** What a mB of crude oil becomes. LPG leaves as a gas: a mB of liquid LPG is this many mB of gas. */
    public static final double LPG_SHARE = 0.05, NAPHTHA_SHARE = 0.20, KEROSENE_SHARE = 0.15, DIESEL_SHARE = 0.30, RESIDUE_SHARE = 0.30;
    public static final int LPG_GAS_PER_MB = 20;
    public static final int FEED_CAPACITY = 8_000, PRODUCT_CAPACITY = 4_000, WATER_CAPACITY = 4_000;

    /** The column's products that are liquids, in the order of the ports. */
    public enum Product {
        NAPHTHA(ColumnPartBlock.NAPHTHA, NAPHTHA_SHARE), KEROSENE(ColumnPartBlock.KEROSENE, KEROSENE_SHARE),
        DIESEL(ColumnPartBlock.DIESEL, DIESEL_SHARE), RESIDUE(ColumnPartBlock.RESIDUE, RESIDUE_SHARE);
        public final int port;
        public final double share;

        Product(int port, double share) {
            this.port = port;
            this.share = share;
        }

        public Liquids.Liquid liquid() {
            return switch (this) {
                case NAPHTHA -> Liquids.NAPHTHA;
                case KEROSENE -> Liquids.KEROSENE;
                case DIESEL -> Liquids.DIESEL;
                case RESIDUE -> Liquids.RESIDUE;
            };
        }
    }

    private final FluidStacksResourceHandler feed = tank(FEED_CAPACITY, Liquids.CRUDE_OIL.resource());
    private final FluidStacksResourceHandler water = tank(WATER_CAPACITY, FluidResource.of(Fluids.WATER));
    private final Map<Product, FluidStacksResourceHandler> products = new EnumMap<>(Product.class);
    private final Map<Product, Double> owed = new EnumMap<>(Product.class);
    private final GasTank lpg = new GasTank("lpg", 1_000, 10, this::setChanged);
    private final GasFlow lpgFlow = new GasFlow();
    private final GasDraw steam = new GasDraw();
    private final EquipmentName name = new EquipmentName("C");
    private double lpgOwed, steamOwed, waterOwed;
    private String status = "IDLE";
    private int rate;

    public DistillationColumnBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.DISTILLATION_COLUMN_ENTITY.get(), pos, state);
        for (Product product : Product.values()) {
            products.put(product, tank(PRODUCT_CAPACITY, product.liquid().resource()));
            owed.put(product, 0.0);
        }
    }

    private FluidStacksResourceHandler tank(int capacity, FluidResource only) {
        return new FluidStacksResourceHandler(1, capacity) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return resource.isEmpty() || resource.equals(only);
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                setChanged();
            }
        };
    }

    /** RUNNING, or why not: NO FEED, NO STEAM, NO COOLING, or FULL and the product that has nowhere to go. */
    public String status() { return status; }
    /** Crude oil taken in the last tick, mB. */
    public int rate() { return rate; }
    public GasTank lpg() { return lpg; }
    public FluidStacksResourceHandler water() { return water; }
    public @Nullable FluidStacksResourceHandler product(int port) {
        for (Product product : Product.values()) if (product.port == port) return products.get(product);
        return null;
    }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    private BlockPos portPos(int x, int back, int up) {
        return DistillationColumnBlock.cell(worldPosition, getBlockState(), x, back, up);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DistillationColumnBlockEntity column) {
        column.tick((ServerLevel) level, state);
    }

    private void tick(ServerLevel level, BlockState state) {
        Direction front = state.getValue(DistillationColumnBlock.FACING);
        rate = 0;
        int crude = feed.getAmountAsInt(0);
        var steamSource = steam.fullest(level, portPos(-1, 0, 0), front, Gases.STEAM.resource());
        int want = Math.min(MAX_FEED, crude);
        String full = null;
        for (Product product : Product.values()) {
            if (products.get(product).getAmountAsInt(0) + Math.ceil(want * product.share) + 1 > PRODUCT_CAPACITY) full = product.name();
        }
        if (lpg.room() < Math.ceil(want * LPG_SHARE * LPG_GAS_PER_MB) + 1) full = "LPG";
        if (io.hookdev.setpoint.control.Esd.tripped(level, worldPosition)) status = "ESD";
        else if (crude == 0) status = "NO FEED";
        else if (steamSource == null || steamSource.amount() < Math.ceil(want * STEAM_PER_CRUDE)) status = "NO STEAM";
        else if (water.getAmountAsInt(0) < Math.ceil(want * WATER_PER_CRUDE)) status = "NO COOLING";
        else if (full != null) status = "FULL " + full;
        else {
            rate = want;
            set(feed, Liquids.CRUDE_OIL.resource(), crude - want);
            steamOwed += want * STEAM_PER_CRUDE;
            steamSource.extract((int) steamOwed);
            steamOwed -= (int) steamOwed;
            waterOwed += want * WATER_PER_CRUDE;
            set(water, FluidResource.of(Fluids.WATER), water.getAmountAsInt(0) - (int) waterOwed);
            waterOwed -= (int) waterOwed;
            for (Product product : Product.values()) {
                double now = owed.get(product) + want * product.share;
                int out = (int) now;
                owed.put(product, now - out);
                FluidStacksResourceHandler tank = products.get(product);
                if (out > 0) set(tank, product.liquid().resource(), tank.getAmountAsInt(0) + out);
            }
            lpgOwed += want * LPG_SHARE * LPG_GAS_PER_MB;
            lpg.insert(Gases.LPG.resource(), (int) lpgOwed);
            lpgOwed -= (int) lpgOwed;
            status = "RUNNING";
        }
        lpgFlow.tick(level, portPos(1, 1, 9), lpg, front);
    }

    private static void set(FluidStacksResourceHandler tank, FluidResource what, int amount) {
        tank.set(0, amount > 0 ? what : FluidResource.EMPTY, Math.max(0, amount));
    }

    @Override
    public void describe(Player player) {
        ChatFormatting colour = status.equals("RUNNING") ? ChatFormatting.WHITE : ChatFormatting.GOLD;
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(colour))
                .append(Component.literal(String.format("feed %d mB/t, crude %.1f B, cooling water %.1f B", rate, feed.getAmountAsInt(0) / 1000.0,
                        water.getAmountAsInt(0) / 1000.0)).withStyle(ChatFormatting.GRAY)));
    }

    // ---------- the feed nozzle: a vessel a pump fills with crude oil ----------
    @Override public FluidStacksResourceHandler fluid() { return feed; }
    @Override public FluidResource resource() { return feed.getResource(0); }
    @Override public int amount() { return feed.getAmountAsInt(0); }
    @Override public double fillFraction() { return amount() / (double) FEED_CAPACITY; }

    @Override
    public boolean canAccept(FluidResource incoming) {
        return incoming.equals(Liquids.CRUDE_OIL.resource()) && amount() < FEED_CAPACITY;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("feed", net.neoforged.neoforge.fluids.FluidStack.OPTIONAL_CODEC, feed.getResource(0).toStack(feed.getAmountAsInt(0)));
        output.store("water", net.neoforged.neoforge.fluids.FluidStack.OPTIONAL_CODEC, water.getResource(0).toStack(water.getAmountAsInt(0)));
        for (Product product : Product.values()) {
            FluidStacksResourceHandler tank = products.get(product);
            output.store(product.name().toLowerCase(), net.neoforged.neoforge.fluids.FluidStack.OPTIONAL_CODEC, tank.getResource(0).toStack(tank.getAmountAsInt(0)));
        }
        lpg.save(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        load(input, "feed", feed);
        load(input, "water", water);
        for (Product product : Product.values()) load(input, product.name().toLowerCase(), products.get(product));
        lpg.load(input);
        name.load(input);
    }

    private static void load(ValueInput input, String key, FluidStacksResourceHandler tank) {
        FluidStack stack = input.read(key, FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        tank.set(0, stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(stack), stack.getAmount());
    }
}
