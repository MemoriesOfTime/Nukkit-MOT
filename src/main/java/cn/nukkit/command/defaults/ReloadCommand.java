package cn.nukkit.command.defaults;

import cn.nukkit.command.CommandSender;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.utils.TextFormat;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ReloadCommand extends VanillaCommand {

    private static int lastTick = -200;

    public ReloadCommand(String name) {
        super(name, "%nukkit.command.reload.description", "%commands.reload.usage");
        this.setPermission("nukkit.command.reload");
        this.commandParameters.clear();
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        if (lastTick + 100 > sender.getServer().getTick()) {
            broadcastCommandMessage(sender, new TranslationContainer(TextFormat.YELLOW + "%nukkit.command.reload.reloading" + TextFormat.WHITE));

            sender.getServer().reload();

            broadcastCommandMessage(sender, new TranslationContainer(TextFormat.YELLOW + "%nukkit.command.reload.reloaded" + TextFormat.WHITE));
        } else {
            sender.sendMessage(new TranslationContainer("nukkit.command.reload.confirm"));
            lastTick = sender.getServer().getTick();
        }

        return true;
    }
}
