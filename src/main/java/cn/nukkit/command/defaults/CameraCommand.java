package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.CameraInstructionPacket;
import cn.nukkit.network.protocol.types.camera.CameraEase;
import cn.nukkit.network.protocol.types.camera.CameraFadeInstruction;
import cn.nukkit.network.protocol.types.camera.CameraSetInstruction;
import cn.nukkit.utils.CameraPresetManager;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;

import java.awt.*;
import java.util.Arrays;

/**
 * @author daoge_cmd <br>
 * Date: 2023/6/11 <br>
 * PowerNukkitX Project <br>
 * TODO: 此命令的多语言文本似乎不能正常工作
 */
public class CameraCommand extends VanillaCommand {

    public static final String[] EASE_TYPES = Arrays.stream(CameraEase.values()).map(CameraEase::getSerializeName).toArray(String[]::new);

    public CameraCommand(String name) {
        super(name, "%nukkit.command.camera.description");
        this.setPermission("nukkit.command.camera");
        this.commandParameters.clear();
        this.commandParameters.put("clear", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("clear", false, new String[]{"clear"})
        });
        this.commandParameters.put("fade", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("fade", false, new String[]{"fade"}),
                CommandParameter.newEnum("facing", true, new String[]{"facing"}),
                CommandParameter.newType("players", true, CommandParamType.TARGET)
        });
        this.commandParameters.put("fade-color", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("fade", false, new String[]{"fade"}),
                CommandParameter.newEnum("color", false, new String[]{"color"}),
                CommandParameter.newType("red", false, CommandParamType.FLOAT),
                CommandParameter.newType("green", false, CommandParamType.FLOAT),
                CommandParameter.newType("blue", false, CommandParamType.FLOAT),
                CommandParameter.newEnum("facing", true, new String[]{"facing"}),
                CommandParameter.newType("players", true, CommandParamType.TARGET)
        });
        this.commandParameters.put("fade-time-color", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("fade", false, new String[]{"fade"}),
                CommandParameter.newEnum("time", false, new String[]{"time"}),
                CommandParameter.newType("fadeInSeconds", false, CommandParamType.FLOAT),
                CommandParameter.newType("holdSeconds", false, CommandParamType.FLOAT),
                CommandParameter.newType("fadeOutSeconds", false, CommandParamType.FLOAT),
                CommandParameter.newEnum("color", false, new String[]{"color"}),
                CommandParameter.newType("red", false, CommandParamType.FLOAT),
                CommandParameter.newType("green", false, CommandParamType.FLOAT),
                CommandParameter.newType("blue", false, CommandParamType.FLOAT),
                CommandParameter.newEnum("facing", true, new String[]{"facing"}),
                CommandParameter.newType("players", true, CommandParamType.TARGET)
        });
        this.commandParameters.put("set-default", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("default", true, new String[]{"default"}),
                CommandParameter.newEnum("facing", true, new String[]{"facing"}),
                CommandParameter.newType("players", true, CommandParamType.TARGET)
        });
        this.commandParameters.put("set-rot", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("rot", false, new String[]{"rot"}),
                CommandParameter.newType("xRot", false, CommandParamType.VALUE),
                CommandParameter.newType("yRot", false, CommandParamType.VALUE),
                CommandParameter.newEnum("facing", true, new String[]{"facing"}),
                CommandParameter.newType("players", true, CommandParamType.TARGET)
        });
        this.commandParameters.put("set-pos", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("pos", false, new String[]{"pos"}),
                CommandParameter.newType("position", false, CommandParamType.POSITION),
                CommandParameter.newEnum("facing", true, new String[]{"facing"}),
                CommandParameter.newType("players", true, CommandParamType.TARGET)
        });
        this.commandParameters.put("set-pos-rot", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("pos", false, new String[]{"pos"}),
                CommandParameter.newType("position", false, CommandParamType.POSITION),
                CommandParameter.newEnum("rot", false, new String[]{"rot"}),
                CommandParameter.newType("xRot", false, CommandParamType.VALUE),
                CommandParameter.newType("yRot", false, CommandParamType.VALUE),
                CommandParameter.newEnum("facing", true, new String[]{"facing"}),
                CommandParameter.newType("players", true, CommandParamType.TARGET)
        });
        this.commandParameters.put("set-ease-default", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("ease", false, new String[]{"ease"}),
                CommandParameter.newType("easeTime", false, CommandParamType.FLOAT),
                CommandParameter.newEnum("easeType", false, EASE_TYPES),
                CommandParameter.newEnum("default", true, new String[]{"default"}),
                CommandParameter.newEnum("facing", true, new String[]{"facing"}),
                CommandParameter.newType("players", true, CommandParamType.TARGET)
        });
        this.commandParameters.put("set-ease-rot", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("ease", false, new String[]{"ease"}),
                CommandParameter.newType("easeTime", false, CommandParamType.FLOAT),
                CommandParameter.newEnum("easeType", false, EASE_TYPES),
                CommandParameter.newEnum("rot", false, new String[]{"rot"}),
                CommandParameter.newType("xRot", false, CommandParamType.VALUE),
                CommandParameter.newType("yRot", false, CommandParamType.VALUE),
                CommandParameter.newEnum("facing", true, new String[]{"facing"}),
                CommandParameter.newType("players", true, CommandParamType.TARGET)
        });
        this.commandParameters.put("set-ease-pos", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("ease", false, new String[]{"ease"}),
                CommandParameter.newType("easeTime", false, CommandParamType.FLOAT),
                CommandParameter.newEnum("easeType", false, EASE_TYPES),
                CommandParameter.newEnum("pos", false, new String[]{"pos"}),
                CommandParameter.newType("position", false, CommandParamType.POSITION),
                CommandParameter.newEnum("facing", true, new String[]{"facing"}),
                CommandParameter.newType("players", true, CommandParamType.TARGET)
        });
        this.commandParameters.put("set-ease-pos-rot", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("ease", false, new String[]{"ease"}),
                CommandParameter.newType("easeTime", false, CommandParamType.FLOAT),
                CommandParameter.newEnum("easeType", false, EASE_TYPES),
                CommandParameter.newEnum("pos", false, new String[]{"pos"}),
                CommandParameter.newType("position", false, CommandParamType.POSITION),
                CommandParameter.newEnum("rot", false, new String[]{"rot"}),
                CommandParameter.newType("xRot", false, CommandParamType.VALUE),
                CommandParameter.newType("yRot", false, CommandParamType.VALUE),
                CommandParameter.newEnum("facing", true, new String[]{"facing"}),
                CommandParameter.newType("players", true, CommandParamType.TARGET)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        Player player = Server.getInstance().getPlayer(args[0].replace("@s", sender.getName()));
        if (player == null) {
            sender.sendMessage(new TranslationContainer("nukkit.camera.unknownPlayer"));
            return false;
        }
        CameraInstructionPacket pk = processPacket(player, args);
        System.out.println(pk.toString());
        player.dataPacket(pk);
        player.sendMessage(new TranslationContainer("nukkit.camera.success", commandLabel));
        return true;
    }

    public CameraInstructionPacket processPacket(Player player, String[] args) {
        CameraInstructionPacket pk = new CameraInstructionPacket();
        for (int i = 1; i < args.length; i++) {
            int subStartIndex = i + 1;
            switch (args[i]) {
                case "clear" -> {
                    pk.setClear(OptionalBoolean.of(true));
                    return pk;
                }
                case "ease" -> {
                    pk.getSetInstruction().setEase(new CameraSetInstruction.EaseData(CameraEase.fromName(args[subStartIndex + 1]), Float.parseFloat(args[subStartIndex])));
                    i += 2;
                }
                case "fade" -> {
                    pk.setFadeInstruction(new CameraFadeInstruction());
                    if (subStartIndex < args.length) {
                        switch (args[subStartIndex]) {
                            case "time" -> {
                                pk.getFadeInstruction().setTimeData(new CameraFadeInstruction.TimeData(Float.parseFloat(args[subStartIndex + 1]), Float.parseFloat(args[subStartIndex + 2]), Float.parseFloat(args[subStartIndex + 3])));
                                if (subStartIndex + 7 < args.length) {
                                    pk.getFadeInstruction().setColor(new Color(Float.parseFloat(args[subStartIndex + 5]), Float.parseFloat(args[subStartIndex + 6]), Float.parseFloat(args[subStartIndex + 7])));
                                }
                                i += 8;
                            }
                            case "color" -> {
                                pk.getFadeInstruction().setColor(new Color(Float.parseFloat(args[subStartIndex + 1]), Float.parseFloat(args[subStartIndex + 2]), Float.parseFloat(args[subStartIndex + 3])));
                                i += 4;
                            }
                        }
                    }
                }
                case "set" -> {
                    pk.setSetInstruction(new CameraSetInstruction());
                    pk.getSetInstruction().setPos(player.asVector3f());
                    pk.getSetInstruction().setPreset(CameraPresetManager.getPreset(args[subStartIndex]));
                    i += 1;
                }
                case "pos" -> {
                    pk.getSetInstruction().setPos(new Vector3f(Float.parseFloat(args[subStartIndex]), Float.parseFloat(args[subStartIndex + 1]), Float.parseFloat(args[subStartIndex + 2])));
                    i += 3;
                }
                case "rot" -> {
                    pk.getSetInstruction().setRot(new Vector2f(Float.parseFloat(args[subStartIndex]), Float.parseFloat(args[subStartIndex + 1])));
                    i += 2;
                }
                case "facing" -> {
                    if (pk.getSetInstruction() == null) {
                        pk.setSetInstruction(new CameraSetInstruction());
                    }
                    Player facing = Server.getInstance().getPlayer(args[subStartIndex]);
                    if (facing != null) {
                        pk.getSetInstruction().setFacing(facing.asVector3f());
                    }
                    return pk;
                }
            }
            if (i >= args.length) {
                return pk;
            }
        }
        return pk;
    }
}
