package cn.nukkit.command.data;

import cn.nukkit.network.protocol.types.PermissionLevel;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ToString
public class CommandData implements Cloneable {

    public CommandEnum aliases = null;
    public String description = "description";
    public Map<String, CommandOverload> overloads = new HashMap<>();

    public int flags;
    public PermissionLevel permission = PermissionLevel.ANY;

    public List<ChainedSubCommandData> subcommands = new ArrayList<>();

    @Override
    public CommandData clone() {
        try {
            return (CommandData) super.clone();
        } catch (Exception e) {
            return new CommandData();
        }
    }
}
