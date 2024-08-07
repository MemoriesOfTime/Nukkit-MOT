package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.potion.Effect;
import cn.nukkit.potion.InstantEffect;
import cn.nukkit.utils.ServerException;
import cn.nukkit.utils.TextFormat;

/**
 * Created by Snake1999 and Pub4Game on 2016/1/23.
 * Package cn.nukkit.command.defaults in project nukkit.
 */
public class EffectCommand extends Command {

    public EffectCommand(String name) {
        super(name, "%nukkit.command.effect.description", "%commands.effect.usage");
        this.setPermission("nukkit.command.effect");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                new CommandParameter("player", CommandParamType.TARGET, false),
                new CommandParameter("effect", CommandParamType.STRING, false), //Do not use Enum here because of buggy behavior
                new CommandParameter("seconds", CommandParamType.INT, true),
                new CommandParameter("amplifier", true),
                new CommandParameter("hideParticle", true, new String[]{"true", "false"})
        });
        this.commandParameters.put("clear", new CommandParameter[]{
                new CommandParameter("player", CommandParamType.TARGET, false),
                new CommandParameter("clear", new String[]{"clear"})
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
            return true;
        }
        Player player = sender.getServer().getPlayer(args[0].replace("@s", sender.getName()));
        if (player == null) {
            sender.sendMessage(new TranslationContainer(TextFormat.RED + "%commands.generic.player.notFound"));
            return true;
        }
        if (args[1].equalsIgnoreCase("clear")) {
            player.removeAllEffects(EntityPotionEffectEvent.Cause.COMMAND);
            sender.sendMessage(new TranslationContainer("commands.effect.success.removed.all", player.getDisplayName()));
            return true;
        }
        Effect effect;
        try {
            effect = Effect.getEffect(Integer.parseInt(args[1]));
        } catch (NumberFormatException | ServerException a) {
            try {
                effect = Effect.getEffectByName(args[1]);
            } catch (Exception e) {
                sender.sendMessage(new TranslationContainer("commands.effect.notFound", args[1]));
                return true;
            }
        }
        int duration = 300;
        int amplification = 1;
        if (args.length >= 3) {
            try {
                duration = Integer.parseInt(args[2]);
            } catch (NumberFormatException a) {
                sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
                return true;
            }
            if (!(effect instanceof InstantEffect)) {
                duration *= 20;
            }
        } else if (effect instanceof InstantEffect) {
            duration = 1;
        }
        if (args.length >= 4) {
            try {
                amplification = Integer.parseInt(args[3]);
            } catch (NumberFormatException a) {
                sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
                return true;
            }
        }
        if (args.length >= 5) {
            String v = args[4].toLowerCase();
            if (v.matches("(?i)|on|true|t|1")) {
                effect.setVisible(false);
            }
        }
        if (duration == 0) {
            if (!player.hasEffect(effect.getId())) {
                if (player.getEffects().isEmpty()) {
                    sender.sendMessage(new TranslationContainer("commands.effect.failure.notActive.all", player.getDisplayName()));
                } else {
                    sender.sendMessage(new TranslationContainer("commands.effect.failure.notActive", effect.getName(), player.getDisplayName()));
                }
                return true;
            }
            player.removeEffect(effect.getId(), EntityPotionEffectEvent.Cause.COMMAND);
            sender.sendMessage(new TranslationContainer("commands.effect.success.removed", effect.getName(), player.getDisplayName()));
        } else {
            effect.setDuration(duration).setAmplifier(amplification);
            player.addEffect(effect, EntityPotionEffectEvent.Cause.COMMAND);
            broadcastCommandMessage(sender, new TranslationContainer("%commands.effect.success", effect.getName(), String.valueOf(effect.getAmplifier()), player.getDisplayName(), String.valueOf(effect.getDuration() / 20)));
        }
        return true;
    }
}
