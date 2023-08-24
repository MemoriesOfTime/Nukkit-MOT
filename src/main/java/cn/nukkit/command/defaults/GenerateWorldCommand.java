package cn.nukkit.command.defaults;

import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.generator.Generator;

public class GenerateWorldCommand extends Command {

    public GenerateWorldCommand(String name) {
        super(name, "%nukkit.command.generateworld.description", "%nukkit.command.generateworld.usage");
        this.setPermission("nukkit.command.generateworld");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                new CommandParameter("name", CommandParamType.STRING, false),
                new CommandParameter("type", CommandParamType.STRING, false),
                new CommandParameter("seed", CommandParamType.INT, false)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
            return false;
        }

        if (Server.getInstance().isLevelGenerated(args[0])) {
            sender.sendMessage(new TranslationContainer("nukkit.command.generateworld.exists", args[0]));
            return true;
        }

        long seed;

        try {
            seed = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(new TranslationContainer("nukkit.command.generateworld.numericSeed"));
            return true;
        }

        Server.getInstance().generateLevel(args[0], seed, Generator.getGenerator(args[1]));

        sender.sendMessage(new TranslationContainer("nukkit.command.generateworld.generating", args[0]));
        return true;
    }
}
