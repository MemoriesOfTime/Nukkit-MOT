package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.command.Command;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.event.player.PlayerCommandPreprocessEvent;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.v113.CommandStepPacketV113;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
public class CommandStepProcessor_v113 extends DataPacketProcessor<CommandStepPacketV113> {

    public static final CommandStepProcessor_v113 INSTANCE = new CommandStepProcessor_v113();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull CommandStepPacketV113 pk) {
        Player player = playerHandle.player;
        if (!player.spawned || !player.isAlive()) {
            return;
        }
        player.craftingType = Player.CRAFTING_SMALL;


        StringBuilder commandText = new StringBuilder(pk.command);
        Command command = player.getServer().getCommandMap().getCommand(commandText.toString());
        if (command != null) {
            if (pk.args != null && !pk.args.isEmpty()) {
                CommandParameter[] pars = command.getCommandParameters(pk.overload);
                if (pars != null) {
                    for (CommandParameter par : pars) {
                        JsonElement arg = pk.args.get(par.name);
                        if (arg != null) {
                            switch (par.type) {
                                case TARGET:
                                    //TODO
                                    /*CommandArg rules = new Gson().fromJson(arg, CommandArg.class);
                                    commandText += " " + rules.getRules()[0].getValue();*/
                                    break;
                                case BLOCK_POSITION:
                                    //TODO
                                    /*CommandArgBlockVector bv = new Gson().fromJson(arg, CommandArgBlockVector.class);
                                    commandText += " " + bv.getX() + " " + bv.getY() + " " + bv.getZ();*/
                                    break;
                                case STRING:
                                case RAWTEXT:
                                    String string = new Gson().fromJson(arg, String.class);
                                    commandText.append(" ").append(string);
                                    break;
                                default:
                                    commandText.append(" ").append(arg);
                                    break;
                            }
                        }
                    }
                } else {
                    player.sendMessage(player.getServer().getLanguage().translateString(command.getUsage()));
                }
            }
        }

        PlayerCommandPreprocessEvent playerCommandPreprocessEvent = new PlayerCommandPreprocessEvent(player, "/" + commandText);
        player.getServer().getPluginManager().callEvent(playerCommandPreprocessEvent);
        if (playerCommandPreprocessEvent.isCancelled()) {
            return;
        }

        player.getServer().dispatchCommand(playerCommandPreprocessEvent.getPlayer(), playerCommandPreprocessEvent.getMessage().substring(1));
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(CommandStepPacketV113.NETWORK_ID);
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol < ProtocolInfo.v1_2_0;
    }
}
