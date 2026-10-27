package io.hookdev.setpoint.compat.jei;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.AmmoniaReactorBlockEntity;
import io.hookdev.setpoint.block.entity.BurnerBlockEntity;
import io.hookdev.setpoint.block.entity.DieselGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.DistillationColumnBlockEntity;
import io.hookdev.setpoint.block.entity.ElectrolyserBlockEntity;
import io.hookdev.setpoint.block.entity.FertilizerMixerBlockEntity;
import io.hookdev.setpoint.block.entity.FuelCellBlockEntity;
import io.hookdev.setpoint.block.entity.NitrogenGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.PumpjackBlockEntity;
import io.hookdev.setpoint.gas.Gases;
import io.hookdev.setpoint.gas.Liquids;
import io.hookdev.setpoint.physics.BoilerModel;
import io.hookdev.setpoint.physics.TurbineModel;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * JEI shows the crafting recipes by itself; this adds what the machines do, one tab per machine: what goes in and
 * what comes out a tick at full speed. Loaded only when JEI is installed.
 */
@JeiPlugin
public final class SetpointJeiPlugin implements IModPlugin {
    /** Something that goes in or comes out: an item, or a liquid or gas in mB (gas at 1 bar). */
    public record Stuff(@Nullable List<ItemStack> items, @Nullable Fluid fluid, long amount) {
        static Stuff item(ItemLike item, int count) { return new Stuff(List.of(new ItemStack(item, count)), null, count); }
        static Stuff items(ItemLike... items) { return new Stuff(java.util.Arrays.stream(items).map(ItemStack::new).toList(), null, 1); }
        static Stuff fluid(Fluid fluid, long amount) { return new Stuff(null, fluid, amount); }
    }

    /** One thing a machine does. */
    public record Process(List<Stuff> in, List<Stuff> out, String line) {}

    private record Machine(String id, ItemLike icon, IRecipeType<Process> type, List<Process> processes) {}

    private static final List<Machine> MACHINES = new ArrayList<>();

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(Setpoint.MODID, "jei_plugin");
    }

    private static List<Machine> machines() {
        if (!MACHINES.isEmpty()) return MACHINES;
        Fluid water = Fluids.WATER, h2 = Gases.HYDROGEN.fluid().get(), o2 = Gases.OXYGEN.fluid().get(), n2 = Gases.NITROGEN.fluid().get(),
                steam = Gases.STEAM.fluid().get(), nh3 = Gases.AMMONIA.fluid().get(), lpg = Gases.LPG.fluid().get();
        Fluid crude = Liquids.CRUDE_OIL.fluid().get(), naphtha = Liquids.NAPHTHA.fluid().get(), kerosene = Liquids.KEROSENE.fluid().get(),
                diesel = Liquids.DIESEL.fluid().get(), residue = Liquids.RESIDUE.fluid().get();
        int steamRate = BoilerModel.STEAM_RATE, feed = DistillationColumnBlockEntity.MAX_FEED;

        add("electrolyser", SetpointContent.ELECTROLYSER_ITEM.get(), new Process(List.of(Stuff.fluid(water, ElectrolyserBlockEntity.WATER_PER_TICK)),
                List.of(Stuff.fluid(h2, ElectrolyserBlockEntity.HYDROGEN_PER_TICK), Stuff.fluid(o2, ElectrolyserBlockEntity.OXYGEN_PER_TICK)),
                ElectrolyserBlockEntity.ENERGY_PER_TICK + " FE/t in · hydrogen left, oxygen right"));
        add("nitrogen_generator", SetpointContent.NITROGEN_GENERATOR_ITEM.get(), new Process(List.of(),
                List.of(Stuff.fluid(n2, NitrogenGeneratorBlockEntity.NITROGEN_PER_TICK)), NitrogenGeneratorBlockEntity.ENERGY_PER_TICK + " FE/t in · out of the air"));
        add("fuel_cell", SetpointContent.FUEL_CELL_ITEM.get(), new Process(
                List.of(Stuff.fluid(h2, FuelCellBlockEntity.HYDROGEN_PER_TICK), Stuff.fluid(o2, FuelCellBlockEntity.OXYGEN_PER_TICK)),
                List.of(Stuff.fluid(water, FuelCellBlockEntity.WATER_PER_TICK)), FuelCellBlockEntity.POWER + " FE/t out"));
        int n = AmmoniaReactorBlockEntity.NITROGEN_PER_TICK;
        add("ammonia_reactor", SetpointContent.AMMONIA_REACTOR_ITEM.get(), new Process(List.of(Stuff.fluid(n2, n), Stuff.fluid(h2, 3L * n)),
                List.of(Stuff.fluid(nh3, 2L * n)), (5 + 2 * n) + " FE/t in · full speed at 100 bar feed"));
        add("fertilizer_mixer", SetpointContent.FERTILIZER_MIXER_ITEM.get(), new Process(
                List.of(Stuff.fluid(nh3, FertilizerMixerBlockEntity.AMMONIA_PER_BATCH), Stuff.item(Items.BONE_MEAL, 1)),
                List.of(Stuff.item(SetpointContent.FERTILIZER.get(), FertilizerMixerBlockEntity.FERTILIZER_PER_BATCH)), "a batch takes 1 s"));
        add("pumpjack", SetpointContent.PUMPJACK_ITEM.get(), new Process(List.of(),
                List.of(Stuff.fluid(crude, PumpjackBlockEntity.CRUDE_PER_TICK)), PumpjackBlockEntity.POWER + " FE/t in · works anywhere"));
        add("distillation_column", SetpointContent.DISTILLATION_COLUMN_ITEM.get(), new Process(
                List.of(Stuff.fluid(crude, feed), Stuff.fluid(steam, Math.round(feed * DistillationColumnBlockEntity.STEAM_PER_CRUDE)),
                        Stuff.fluid(water, Math.round(feed * DistillationColumnBlockEntity.WATER_PER_CRUDE))),
                List.of(Stuff.fluid(lpg, Math.round(feed * DistillationColumnBlockEntity.LPG_SHARE * DistillationColumnBlockEntity.LPG_GAS_PER_MB)),
                        Stuff.fluid(naphtha, Math.round(feed * DistillationColumnBlockEntity.NAPHTHA_SHARE)),
                        Stuff.fluid(kerosene, Math.round(feed * DistillationColumnBlockEntity.KEROSENE_SHARE)),
                        Stuff.fluid(diesel, Math.round(feed * DistillationColumnBlockEntity.DIESEL_SHARE)),
                        Stuff.fluid(residue, Math.round(feed * DistillationColumnBlockEntity.RESIDUE_SHARE))),
                "steam and cooling water in, each cut out of its own nozzle"));
        add("diesel_generator", SetpointContent.DIESEL_GENERATOR_ITEM.get(), new Process(
                List.of(Stuff.fluid(diesel, DieselGeneratorBlockEntity.DIESEL_PER_TICK)), List.of(), DieselGeneratorBlockEntity.POWER + " FE/t out"));
        add("boiler", SetpointContent.BOILER_ITEM.get(), new Process(List.of(Stuff.items(Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK), Stuff.fluid(water, steamRate)),
                List.of(Stuff.fluid(steam, steamRate)), "any furnace fuel, or a burner underneath"));
        List<Process> burner = new ArrayList<>();
        for (Fluid fuel : List.of(naphtha, kerosene, diesel, residue, crude)) {
            burner.add(new Process(List.of(Stuff.fluid(fuel, BurnerBlockEntity.liquidPerTick(fuel)), Stuff.fluid(water, steamRate)),
                    List.of(Stuff.fluid(steam, steamRate)), "under a boiler: fires it like coal"));
        }
        burner.add(new Process(List.of(Stuff.fluid(lpg, BurnerBlockEntity.LPG_PER_TICK), Stuff.fluid(water, steamRate)), List.of(Stuff.fluid(steam, steamRate)), "LPG from a gas line"));
        burner.add(new Process(List.of(Stuff.fluid(h2, BurnerBlockEntity.HYDROGEN_PER_TICK), Stuff.fluid(water, steamRate)), List.of(Stuff.fluid(steam, steamRate)), "hydrogen from a gas line"));
        add("burner", SetpointContent.BURNER_ITEM.get(), burner.toArray(Process[]::new));
        add("steam_turbine", SetpointContent.STEAM_TURBINE_ITEM.get(), new Process(List.of(Stuff.fluid(steam, TurbineModel.MAX_STEAM)), List.of(),
                Math.round(TurbineModel.MAX_STEAM * TurbineModel.FE_PER_MB) + " FE/t out at 3000 rpm"));
        add("stirling_engine", SetpointContent.STIRLING_ENGINE_ITEM.get(), new Process(List.of(Stuff.items(Items.COAL, Items.CHARCOAL, Items.OAK_LOG)), List.of(),
                io.hookdev.setpoint.block.entity.StirlingEngineBlockEntity.OUTPUT + " FE/t out · any furnace fuel"));
        add("biogas_generator", SetpointContent.BIOGAS_GENERATOR_ITEM.get(), new Process(List.of(Stuff.items(Items.WHEAT_SEEDS, Items.ROTTEN_FLESH, Items.APPLE, Items.KELP)), List.of(),
                io.hookdev.setpoint.block.entity.BiogasGeneratorBlockEntity.OUTPUT + " FE/t out · anything compostable"));
        return MACHINES;
    }

    private static void add(String id, Item icon, Process... processes) {
        MACHINES.add(new Machine(id, icon, IRecipeType.create(Setpoint.MODID, id, Process.class), List.of(processes)));
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        for (Machine machine : machines()) registration.addRecipeCategories(new ProcessCategory(machine, gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (Machine machine : machines()) registration.addRecipes(machine.type(), machine.processes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (Machine machine : machines()) registration.addCraftingStation(machine.type(), machine.icon());
    }

    /** One machine's tab: inputs on the left, an arrow, outputs on the right, a line of text under them. */
    private static final class ProcessCategory extends AbstractRecipeCategory<Process> {
        static final int WIDTH = 182, HEIGHT = 40;

        ProcessCategory(Machine machine, IGuiHelper gui) {
            super(machine.type(), Component.translatable("block.setpoint." + machine.id()), gui.createDrawableItemLike(machine.icon()), WIDTH, HEIGHT);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Process process, IFocusGroup focuses) {
            for (int i = 0; i < process.in().size(); i++) slot(builder.addInputSlot(1 + 18 * i, 2), process.in().get(i));
            for (int i = 0; i < process.out().size(); i++) slot(builder.addOutputSlot(92 + 18 * i, 2), process.out().get(i));
        }

        private static void slot(IRecipeSlotBuilder slot, Stuff stuff) {
            slot.setStandardSlotBackground();
            if (stuff.items() != null) slot.addItemStacks(stuff.items());
            else if (stuff.fluid() != null) slot.setFluidRenderer(stuff.amount(), false, 16, 16).add(stuff.fluid(), stuff.amount());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Process process, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(62, 2);
            builder.addText(List.<FormattedText>of(Component.literal("a tick at full speed · " + process.line())), WIDTH, 16)
                    .setPosition(0, 23).setColor(0xFF5A5A5A);
        }
    }
}
