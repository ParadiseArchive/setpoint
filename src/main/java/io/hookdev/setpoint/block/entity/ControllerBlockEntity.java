package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.advancement.PlantEventTrigger;
import io.hookdev.setpoint.block.ControllerBlock;
import io.hookdev.setpoint.control.PidController;
import io.hookdev.setpoint.control.PidController.Action;
import io.hookdev.setpoint.control.PidController.Mode;
import io.hookdev.setpoint.control.PidController.Tuning;
import io.hookdev.setpoint.control.TagDirectory;
import io.hookdev.setpoint.hydraulics.LoopFinder;
import io.hookdev.setpoint.hydraulics.Vessel;
import io.hookdev.setpoint.menu.FaceplateMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A DCS controller (LIC). Reads its PV from a transmitter tag, drives a valve tag, and in CAS
 * takes its setpoint from another controller's output.
 *
 * <p>Out of the box it links itself: placed against a tank it measures that tank, finds the control
 * valve in the tank's pipe runs, picks reverse or direct action from where the valve sits, and goes
 * to AUTO. Linking by hand with the field communicator switches that off.
 */
public class ControllerBlockEntity extends InstrumentBlockEntity implements MenuProvider {
    public static final double DT = 0.05; // one tick
    public static final double ALARM_DEADBAND = 1.0;

    // Faceplate buttons (sent with the vanilla container button packet)
    public static final int BUTTON_MAN = 0, BUTTON_AUTO = 1, BUTTON_CAS = 2;
    public static final int BUTTON_SP_DOWN_10 = 10, BUTTON_SP_DOWN_1 = 11, BUTTON_SP_UP_1 = 12, BUTTON_SP_UP_10 = 13;
    public static final int BUTTON_OP_DOWN_10 = 20, BUTTON_OP_DOWN_1 = 21, BUTTON_OP_UP_1 = 22, BUTTON_OP_UP_10 = 23;
    public static final int BUTTON_TUNING = 30, BUTTON_ACTION = 31;
    public static final int BUTTON_HI_DOWN = 40, BUTTON_HI_UP = 41, BUTTON_LO_DOWN = 42, BUTTON_LO_UP = 43;

    // Synced values (ints, tenths of a percent)
    public static final int DATA_PV = 0, DATA_SP = 1, DATA_OP = 2, DATA_MODE = 3, DATA_TUNING = 4, DATA_ACTION = 5,
            DATA_HI = 6, DATA_LO = 7, DATA_ALARMS = 8, DATA_VALVE = 9, DATA_COUNT = 10;
    public static final int ALARM_HI = 1, ALARM_LO = 2, ALARM_BAD_PV = 4;

    private final PidController pid = new PidController();
    private String pvTag = "";
    private String mvTag = "";
    private String casTag = "";
    private Mode mode = Mode.MAN;
    private Action action = Action.REVERSE;
    private Tuning tuning = Tuning.NORMAL;
    private double sp = 50;
    private double hiLimit = 90;
    private double loLimit = 10;
    private double pv = Double.NaN;
    private boolean autoLink = true;
    private boolean autoAction = true;
    private double valvePosition = -1;
    private int alarms;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PV -> Double.isNaN(pv) ? -1 : tenths(pv);
                case DATA_SP -> tenths(sp);
                case DATA_OP -> tenths(pid.output());
                case DATA_MODE -> mode.ordinal();
                case DATA_TUNING -> tuning.ordinal();
                case DATA_ACTION -> action.ordinal();
                case DATA_HI -> tenths(hiLimit);
                case DATA_LO -> tenths(loLimit);
                case DATA_ALARMS -> alarms;
                case DATA_VALVE -> valvePosition < 0 ? -1 : tenths(valvePosition * 100);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Values only travel server -> client; the client changes things with buttons.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.CONTROLLER_ENTITY.get(), pos, state, "LIC");
    }

    private static int tenths(double percent) {
        return (int) Math.round(percent * 10);
    }

    public ContainerData data() { return data; }
    public double output() { return pid.output(); }
    public int alarms() { return alarms; }
    public double setpoint() { return sp; }
    public Mode mode() { return mode; }
    public String pvTag() { return pvTag; }
    public String mvTag() { return mvTag; }
    public String casTag() { return casTag; }
    public double pv() { return pv; }

    public void setPvTag(String tag) { pvTag = tag; setChanged(); }
    public void setAutoLink(boolean autoLink) { this.autoLink = autoLink; setChanged(); }
    public boolean autoLink() { return autoLink; }
    public void setMvTag(String tag) { mvTag = tag; setChanged(); }
    public void setCasTag(String tag) { casTag = tag; setChanged(); }
    public void setMode(Mode mode) { this.mode = mode; setChanged(); }
    public void setAction(Action action) { this.action = action; setChanged(); }
    public void setSetpoint(double percent) { sp = PidController.clamp(percent); setChanged(); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ControllerBlockEntity controller) {
        controller.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        pv = measure(level);
        if (autoLink && level.getGameTime() % 20 == 0) discover(level);
        boolean badPv = Double.isNaN(pv);
        if (badPv && mode != Mode.MAN) mode = Mode.MAN; // shed to manual on a bad PV, like a real DCS

        if (mode == Mode.CAS) {
            ControllerBlockEntity master = TagDirectory.find(level, casTag, ControllerBlockEntity.class);
            if (master == null || master == this) mode = Mode.AUTO; // lost the master: fall back to AUTO
            else sp = master.output();
        }

        pid.update(mode, action, tuning, badPv ? sp : pv, sp, DT);

        ControlValveBlockEntity valve = TagDirectory.find(level, mvTag, ControlValveBlockEntity.class);
        if (valve != null) {
            valve.command(pid.output() / 100.0, level.getGameTime());
            valvePosition = valve.position();
        } else {
            valvePosition = -1;
        }

        int newAlarms = badPv ? ALARM_BAD_PV : 0;
        if (!badPv) {
            boolean wasHi = (alarms & ALARM_HI) != 0, wasLo = (alarms & ALARM_LO) != 0;
            if (pv > hiLimit || (wasHi && pv > hiLimit - ALARM_DEADBAND)) newAlarms |= ALARM_HI;
            if (pv < loLimit || (wasLo && pv < loLimit + ALARM_DEADBAND)) newAlarms |= ALARM_LO;
        }
        alarms = newAlarms;
        boolean alarmLight = alarms != 0;
        if (state.getValue(ControllerBlock.ALARM) != alarmLight) {
            level.setBlock(pos, state.setValue(ControllerBlock.ALARM, alarmLight), Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % 20 == 0) setChanged();
        // a loop that really holds its setpoint, with the valve somewhere in the middle
        if (level.getGameTime() % 100 == 0 && mode == Mode.AUTO && !badPv && valve != null && Math.abs(pv - sp) < 1.0
                && valvePosition > 0.02 && valvePosition < 0.98 && level instanceof ServerLevel server) {
            PlantEventTrigger.near(server, pos, PlantEventTrigger.LOOP_HOLDING);
        }
    }

    private @Nullable Vessel adjacentTank(Level level) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof Vessel vessel) return vessel;
        }
        return null;
    }

    /** PV in percent: from the transmitter tag, or straight from the tank this controller sits against. */
    private double measure(Level level) {
        if (!pvTag.isEmpty()) {
            LevelTransmitterBlockEntity transmitter = TagDirectory.find(level, pvTag, LevelTransmitterBlockEntity.class);
            Double measured = transmitter == null ? null : transmitter.percent();
            return measured == null ? Double.NaN : measured;
        }
        Vessel tank = adjacentTank(level);
        return tank == null ? Double.NaN : tank.fillFraction() * 100.0;
    }

    /** Self-linking: find the tank to measure and the valve that moves its level. */
    private void discover(Level level) {
        Vessel tank = adjacentTank(level);
        if (tank == null) {
            LevelTransmitterBlockEntity transmitter = pvTag.isEmpty()
                    ? TagDirectory.nearest(level, worldPosition, 8, LevelTransmitterBlockEntity.class)
                    : TagDirectory.find(level, pvTag, LevelTransmitterBlockEntity.class);
            if (transmitter == null) return;
            pvTag = transmitter.tag();
            tank = transmitter.tank();
        } else {
            pvTag = ""; // measure the tank we sit against
        }
        if (tank == null) return;
        LoopFinder.ValveLink link = LoopFinder.findValve(level, tank.getBlockPos());
        if (link == null || !(level.getBlockEntity(link.valve()) instanceof ControlValveBlockEntity valve)) return;
        boolean newLink = !valve.tag().equals(mvTag);
        mvTag = valve.tag();
        if (autoAction) action = link.feedsTank() ? Action.REVERSE : Action.DIRECT;
        if (newLink && mode == Mode.MAN && !Double.isNaN(pv)) mode = Mode.AUTO;
        setChanged();
    }

    /** Where the PV comes from, for the faceplate footer. */
    public String pvSource() {
        if (!pvTag.isEmpty()) return pvTag;
        return level != null && adjacentTank(level) != null ? "LOCAL" : "";
    }

    /** A faceplate button was pressed. */
    public void handleButton(int button) {
        switch (button) {
            case BUTTON_MAN -> mode = Mode.MAN;
            case BUTTON_AUTO -> { if (!Double.isNaN(pv)) mode = Mode.AUTO; }
            case BUTTON_CAS -> { if (!Double.isNaN(pv) && !casTag.isEmpty()) mode = Mode.CAS; }
            case BUTTON_SP_DOWN_10 -> nudgeSetpoint(-10);
            case BUTTON_SP_DOWN_1 -> nudgeSetpoint(-1);
            case BUTTON_SP_UP_1 -> nudgeSetpoint(1);
            case BUTTON_SP_UP_10 -> nudgeSetpoint(10);
            case BUTTON_OP_DOWN_10 -> nudgeOutput(-10);
            case BUTTON_OP_DOWN_1 -> nudgeOutput(-1);
            case BUTTON_OP_UP_1 -> nudgeOutput(1);
            case BUTTON_OP_UP_10 -> nudgeOutput(10);
            case BUTTON_TUNING -> tuning = tuning.next();
            case BUTTON_ACTION -> {
                action = action == Action.DIRECT ? Action.REVERSE : Action.DIRECT;
                autoAction = false; // the engineer knows better
            }
            case BUTTON_HI_DOWN -> hiLimit = Math.max(loLimit + 5, hiLimit - 5);
            case BUTTON_HI_UP -> hiLimit = Math.min(100, hiLimit + 5);
            case BUTTON_LO_DOWN -> loLimit = Math.max(0, loLimit - 5);
            case BUTTON_LO_UP -> loLimit = Math.min(hiLimit - 5, loLimit + 5);
            default -> { return; }
        }
        setChanged();
    }

    private void nudgeSetpoint(double delta) {
        if (mode != Mode.CAS) sp = PidController.clamp(Math.round(sp) + delta);
    }

    private void nudgeOutput(double delta) {
        if (mode == Mode.MAN) pid.setOutput(Math.round(pid.output()) + delta);
    }

    /** Extra data the client faceplate needs once, when it opens. */
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeUtf(tag());
        buf.writeUtf(pvSource());
        buf.writeUtf(mvTag);
        buf.writeUtf(casTag);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(tag());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FaceplateMenu(containerId, inventory, this);
    }

    @Override
    public void describe(Player player) {
        String pvText = Double.isNaN(pv) ? "BAD PV" : String.format("PV %.1f %%", pv);
        player.sendSystemMessage(Component.empty().append(tagLabel())
                .append(Component.literal(mode + "  ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.format("%s  SP %.1f %%  OP %.1f %%", pvText, sp, pid.output())).withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal(String.format("PV %s  MV %s  CAS %s",
                pvSource().isEmpty() ? "-" : pvSource(), mvTag.isEmpty() ? "-" : mvTag, casTag.isEmpty() ? "-" : casTag)).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("pv_tag", pvTag);
        output.putString("mv_tag", mvTag);
        output.putString("cas_tag", casTag);
        output.putString("mode", mode.name());
        output.putString("action", action.name());
        output.putString("tuning", tuning.name());
        output.putDouble("sp", sp);
        output.putDouble("op", pid.output());
        output.putDouble("hi", hiLimit);
        output.putDouble("lo", loLimit);
        output.putBoolean("auto_link", autoLink);
        output.putBoolean("auto_action", autoAction);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pvTag = input.getStringOr("pv_tag", "");
        mvTag = input.getStringOr("mv_tag", "");
        casTag = input.getStringOr("cas_tag", "");
        mode = parse(Mode.class, input.getStringOr("mode", ""), Mode.MAN);
        action = parse(Action.class, input.getStringOr("action", ""), Action.REVERSE);
        tuning = parse(Tuning.class, input.getStringOr("tuning", ""), Tuning.NORMAL);
        sp = input.getDoubleOr("sp", 50);
        pid.setOutput(input.getDoubleOr("op", 0));
        hiLimit = input.getDoubleOr("hi", 90);
        loLimit = input.getDoubleOr("lo", 10);
        autoLink = input.getBooleanOr("auto_link", true);
        autoAction = input.getBooleanOr("auto_action", true);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
