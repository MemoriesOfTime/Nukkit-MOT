package cn.nukkit.command.tree.node;

import cn.nukkit.IPlayer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.exceptions.SelectorSyntaxException;
import cn.nukkit.command.selector.EntitySelectorAPI;
import cn.nukkit.entity.Entity;

import java.util.List;
import java.util.Locale;

/** Targets for name-based administrative commands; general IPlayersNode still resolves full profiles. */
public class NameOnlyPlayerTargetsNode extends ParamNode<List<NameOnlyPlayerTargetsNode.Target>> {
    @Override
    public void fill(String arg) {
        if (arg.isBlank()) {
            error();
            return;
        }
        Server server = Server.getInstance();
        if (EntitySelectorAPI.getAPI().checkValid(arg)) {
            List<Entity> entities;
            try {
                entities = EntitySelectorAPI.getAPI().matchEntities(paramList.getParamTree().getSender(), arg);
            } catch (SelectorSyntaxException exception) {
                error(exception.getMessage());
                return;
            }
            List<Target> targets = entities.stream().filter(entity -> entity instanceof IPlayer)
                    .map(entity -> new Target(server, null, (IPlayer) entity)).toList();
            if (!targets.isEmpty()) value = targets;
            else error("commands.generic.noTargetMatch");
        } else {
            value = List.of(new Target(server, arg, server.getPlayerExact(arg.toLowerCase(Locale.ROOT))));
        }
    }

    /** Captures the selected path while retaining all effects of an already resolved online Player. */
    public record Target(Server server, String name, IPlayer resolved) {
        public String getName() {
            return resolved == null ? name : resolved.getName();
        }

        public boolean isOp() {
            return resolved == null ? server.isOp(name.toLowerCase(Locale.ROOT)) : resolved.isOp();
        }

        public void setOp(boolean value) {
            if (resolved != null) {
                resolved.setOp(value);
                return;
            }
            if (value != isOp()) {
                if (value) server.addOp(name.toLowerCase(Locale.ROOT));
                else server.removeOp(name.toLowerCase(Locale.ROOT));
            }
        }

        public boolean isOnline() {
            return resolved == null ? getPlayer() != null : resolved.isOnline();
        }

        public Player getPlayer() {
            return resolved == null ? server.getPlayerExact(name) : resolved.getPlayer();
        }
    }
}
