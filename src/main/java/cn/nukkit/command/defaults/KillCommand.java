package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.tree.ParamList;
import cn.nukkit.command.utils.CommandLogger;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.utils.TextFormat;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author Pub4Game
 * @since 2015/12/08
 */
public class KillCommand extends VanillaCommand {

    public KillCommand(String name) {
        super(name, "commands.kill.description");
        this.setPermission("nukkit.command.kill.self;"
                + "nukkit.command.kill.other");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                CommandParameter.newType("player", true, CommandParamType.TARGET)
        });
        this.enableParamTree();
    }

    @Override
    public int execute(CommandSender sender, String commandLabel, @NotNull Map.Entry<String, ParamList> result, CommandLogger log) {
        if (result.getValue().hasResult(0)) {
            if (!sender.hasPermission("nukkit.command.kill.other")) {
                log.addError("nukkit.command.generic.permission").output();
                return 0;
            }
            List<Entity> entities = result.getValue().getResult(0);
            entities.removeIf(entity -> !entity.isAlive());
            if (entities.isEmpty()) {
                log.addNoTargetMatch().output();
                return 0;
            }
            AtomicBoolean creativePlayer = new AtomicBoolean(false);
            entities = entities.stream().filter(entity -> {
                if (entity instanceof Player player)
                    if (player.isCreative()) {
                        creativePlayer.set(true);
                        return false;
                    } else return true;
                else
                    return true;
            }).toList();

            if (entities.isEmpty()) {
                if (creativePlayer.get())
                    log.addError(TextFormat.WHITE + "%commands.kill.attemptKillPlayerCreative");
                else log.addNoTargetMatch();
                log.output();
                return 0;
            }

            for (Entity entity : entities) {
                if (entity.getName().equals(sender.getName())) {
                    if (!sender.hasPermission("nukkit.command.kill.self")) {
                        continue;
                    }
                }
                if (entity instanceof Player player) {
                    EntityDamageEvent ev = new EntityDamageEvent(player, DamageCause.SUICIDE, 1000000);
                    player.attack(ev);
                } else {
                    entity.kill();
                }
            }
            String message = entities.stream().map(Entity::getName).collect(Collectors.joining(", "));
            log.addSuccess("commands.kill.successful", message).successCount(entities.size()).output(true);
            return entities.size();
        } else {
            if (sender.isPlayer()) {
                if (!sender.hasPermission("nukkit.command.kill.self")) {
                    log.addError("nukkit.command.generic.permission").output();
                    return 0;
                }
                if (sender.asPlayer().isCreative()) {
                    log.addError("commands.kill.attemptKillPlayerCreative").output();
                    return 0;
                }
                EntityDamageEvent ev = new EntityDamageEvent(sender.asPlayer(), DamageCause.SUICIDE, 1000000);
                sender.asPlayer().attack(ev);
            } else {
                log.addError("commands.generic.usage", "\n" + this.getCommandFormatTips()).output();
                return 0;
            }
            return 1;
        }
    }
}