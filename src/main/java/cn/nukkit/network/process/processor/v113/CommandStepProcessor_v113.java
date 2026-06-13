package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.command.Command;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.data.v113.CommandArgBlockVector_v113;
import cn.nukkit.command.data.v113.CommandArg_v113;
import cn.nukkit.event.player.PlayerCommandPreprocessEvent;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.v113.CommandStepPacket_v113;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
public class CommandStepProcessor_v113 extends DataPacketProcessor<CommandStepPacket_v113> {

    private static final Gson GSON = new Gson();

    public static final CommandStepProcessor_v113 INSTANCE = new CommandStepProcessor_v113();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull CommandStepPacket_v113 pk) {
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
                            try {
                                switch (par.type) {
                                    case TARGET:
                                        CommandArg_v113 rules = GSON.fromJson(arg, CommandArg_v113.class);
                                        if (rules != null && rules.getRules() != null && rules.getRules().length > 0) {
                                            commandText.append(" ").append(rules.getRules()[0].getValue());
                                        }
                                        break;
                                    case BLOCK_POSITION:
                                        CommandArgBlockVector_v113 bv = GSON.fromJson(arg, CommandArgBlockVector_v113.class);
                                        if (bv != null) {
                                            commandText.append(" ").append(bv.getX()).append(" ").append(bv.getY()).append(" ").append(bv.getZ());
                                        }
                                        break;
                                    case STRING:
                                    case RAWTEXT:
                                        String string = GSON.fromJson(arg, String.class);
                                        if (string != null) {
                                            commandText.append(" ").append(string);
                                        }
                                        break;
                                    default:
                                        commandText.append(" ").append(arg);
                                        break;
                                }
                            } catch (JsonSyntaxException e) {
                                // 忽略无效的JSON参数，使用原始JsonElement
                                commandText.append(" ").append(arg);
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
        return ProtocolInfo.toNewProtocolID(CommandStepPacket_v113.NETWORK_ID);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return CommandStepPacket_v113.class;
    }

    @Override
    public boolean isSupported(int protocol) {
        return protocol < ProtocolInfo.v1_2_0;
    }
}
