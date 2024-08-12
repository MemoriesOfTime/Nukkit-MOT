package cn.nukkit.command.selector.args.impl;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.exceptions.SelectorSyntaxException;
import cn.nukkit.command.selector.ParseUtils;
import cn.nukkit.command.selector.SelectorType;
import cn.nukkit.command.selector.args.CachedSimpleSelectorArgument;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Location;

import java.util.function.Predicate;


public class L extends CachedSimpleSelectorArgument {
    @Override
    protected Predicate<Entity> cache(SelectorType selectorType, CommandSender sender, Location basePos, String... arguments) throws SelectorSyntaxException {
        ParseUtils.singleArgument(arguments, getKeyName());
        ParseUtils.cannotReversed(arguments[0]);
        final var l = Integer.parseInt(arguments[0]);
        return entity -> entity instanceof Player player && player.getExperienceLevel() <= l;
    }

    @Override
    public String getKeyName() {
        return "l";
    }

    @Override
    public int getPriority() {
        return 3;
    }
}
