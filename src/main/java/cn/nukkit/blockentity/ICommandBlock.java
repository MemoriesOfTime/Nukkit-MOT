package cn.nukkit.blockentity;

import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;

public interface ICommandBlock extends CommandSender {

    int CURRENT_VERSION = 10;

    int MODE_NORMAL = 0;
    int MODE_REPEATING = 1;
    int MODE_CHAIN = 2;

    String TAG_COMMAND = "Command";
    String TAG_CUSTOM_NAME = "CustomName";
    String TAG_SUCCESS_COUNT = "SuccessCount";
    String TAG_LAST_OUTPUT = "LastOutput";
    String TAG_LAST_OUTPUT_PARAMS = "LastOutputParams";
    String TAG_TRACK_OUTPUT = "TrackOutput";
    String TAG_LAST_EXECUTION = "LastExecution";
    String TAG_AUTO = "auto";
    String TAG_CONDITIONAL_MODE = "conditionalMode";
    String TAG_CONDITION_MET = "conditionMet";
    String TAG_POWERED = "powered";
    String TAG_TICK_DELAY = "TickDelay";
    String TAG_EXECUTE_ON_FIRST_TICK = "ExecuteOnFirstTick";
    String TAG_LP_COMMAND_MODE = "LPCommandMode";
    String TAG_LP_CONDIONAL_MODE = "LPCondionalMode";
    String TAG_LP_REDSTONE_MODE = "LPRedstoneMode";
    String TAG_VERSION = "Version";

    boolean execute(int chain);

    default boolean trigger(int chain) {
        int delay = this.getTickDelay();
        if (delay > 0) {
            Server.getInstance().getScheduler().scheduleDelayedTask(
                    new CommandBlockTrigger(this, chain), delay);
            return false;
        }
        return this.execute(chain);
    }

    default boolean trigger() {
        return this.trigger(0);
    }

    String getCommand();

    int getMode();

    int getSuccessCount();

    boolean isPowered();

    boolean isAuto();

    boolean isConditional();

    boolean isConditionMet();

    int getTickDelay();

    long getLastExecution();

    boolean isTrackingOutput();

    Level getLevel();

    final class CommandBlockTrigger implements Runnable {
        private final ICommandBlock commandBlock;
        private final int chain;

        public CommandBlockTrigger(ICommandBlock commandBlock, int chain) {
            this.commandBlock = commandBlock;
            this.chain = chain;
        }

        @Override
        public void run() {
            this.commandBlock.execute(this.chain);
        }
    }
}
