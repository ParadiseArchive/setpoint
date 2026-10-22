package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.GasMakerBlock;
import io.hookdev.setpoint.gas.GasDraw;
import io.hookdev.setpoint.gas.GasVessel;
import io.hookdev.setpoint.gas.Gases;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A fertilizer mixer: bone meal (the phosphate) and ammonia from the gas line on your left (the nitrogen) make NPK
 * fertilizer. Put bone meal in by hand or with a hopper; take the fertilizer out by hand, or with a hopper below.
 */
public class FertilizerMixerBlockEntity extends BlockEntity implements NamedEquipment, Describable {
    public static final int INPUT = 0, OUTPUT = 1;
    /** One batch: one bone meal and this much ammonia (mB at 1 bar, taken a little every tick) make this many fertilizer. */
    public static final int AMMONIA_PER_BATCH = 200, TICKS_PER_BATCH = 20, FERTILIZER_PER_BATCH = 2;

    private final ItemStacksResourceHandler items = new ItemStacksResourceHandler(2) {
        @Override
        public boolean isValid(int index, ItemResource resource) {
            return index == INPUT ? resource.isEmpty() || resource.is(Items.BONE_MEAL) : resource.isEmpty() || resource.is(SetpointContent.FERTILIZER.get());
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };
    private final GasDraw ammonia = new GasDraw();
    private final EquipmentName name = new EquipmentName("MX");
    private int progress;
    private String status = "IDLE";

    public FertilizerMixerBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.FERTILIZER_MIXER_ENTITY.get(), pos, state);
    }

    public ItemStacksResourceHandler items() { return items; }
    /** RUNNING, or why not: NO BONE MEAL, NO AMMONIA, FULL. */
    public String status() { return status; }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FertilizerMixerBlockEntity mixer) {
        mixer.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean running = false;
        int out = items.getAmountAsInt(OUTPUT);
        GasVessel source = ammonia.fullest(level, pos, GasMakerBlock.left(state), Gases.AMMONIA.resource());
        int perTick = AMMONIA_PER_BATCH / TICKS_PER_BATCH;
        if (items.getAmountAsInt(INPUT) == 0) status = "NO BONE MEAL";
        else if (out + FERTILIZER_PER_BATCH > SetpointContent.FERTILIZER.get().getDefaultMaxStackSize()) status = "FULL";
        else if (source == null || source.amount() < perTick) status = "NO AMMONIA";
        else {
            source.extract(perTick);
            running = true;
            status = "RUNNING";
            if (++progress >= TICKS_PER_BATCH) {
                progress = 0;
                int left = items.getAmountAsInt(INPUT) - 1;
                items.set(INPUT, left > 0 ? ItemResource.of(Items.BONE_MEAL) : ItemResource.EMPTY, left);
                items.set(OUTPUT, ItemResource.of(SetpointContent.FERTILIZER.get()), out + FERTILIZER_PER_BATCH);
            }
        }
        if (state.getValue(GasMakerBlock.RUNNING) != running) level.setBlock(pos, state.setValue(GasMakerBlock.RUNNING, running), Block.UPDATE_CLIENTS);
    }

    /** Bone meal from your hand into the mixer; returns how many went in. */
    public int insert(ItemStack stack) {
        if (!stack.is(Items.BONE_MEAL)) return 0;
        int have = items.getAmountAsInt(INPUT), room = Items.BONE_MEAL.getDefaultMaxStackSize() - have, moved = Math.min(room, stack.getCount());
        if (moved > 0) items.set(INPUT, ItemResource.of(Items.BONE_MEAL), have + moved);
        return moved;
    }

    /** The fertilizer made so far, out of the mixer. */
    public ItemStack takeOutput() {
        int count = items.getAmountAsInt(OUTPUT);
        if (count == 0) return ItemStack.EMPTY;
        items.set(OUTPUT, ItemResource.EMPTY, 0);
        return new ItemStack(SetpointContent.FERTILIZER.get(), count);
    }

    @Override
    public void describe(Player player) {
        ChatFormatting colour = status.equals("RUNNING") ? ChatFormatting.WHITE : ChatFormatting.GOLD;
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(colour))
                .append(Component.literal(String.format("bone meal %d, fertilizer %d", items.getAmountAsInt(INPUT), items.getAmountAsInt(OUTPUT))).withStyle(ChatFormatting.GRAY)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        items.serialize(output);
        name.save(output);
        output.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.deserialize(input);
        name.load(input);
        progress = input.getIntOr("progress", 0);
    }
}
