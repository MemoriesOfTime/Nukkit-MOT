package cn.nukkit.command.defaults;

import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.CameraInstructionPacket;
import cn.nukkit.network.protocol.types.camera.CameraEase;
import cn.nukkit.network.protocol.types.camera.CameraFadeInstruction;
import cn.nukkit.network.protocol.types.camera.CameraPreset;
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
        super(name, "commands.camera.description");
        this.setPermission("nukkit.command.camera");
        this.commandParameters.clear();
        this.commandParameters.put("clear", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("clear", false, new String[]{"clear"})
        });
        this.commandParameters.put("fade", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("fade", false, new String[]{"fade"})
        });
        this.commandParameters.put("fade-color", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("fade", false, new String[]{"fade"}),
                CommandParameter.newEnum("color", false, new String[]{"color"}),
                CommandParameter.newType("red", false, CommandParamType.FLOAT),
                CommandParameter.newType("green", false, CommandParamType.FLOAT),
                CommandParameter.newType("blue", false, CommandParamType.FLOAT)
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
                CommandParameter.newType("blue", false, CommandParamType.FLOAT)
        });
        this.commandParameters.put("set-default", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("default", true, new String[]{"default"})
        });
        this.commandParameters.put("set-rot", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("rot", false, new String[]{"rot"}),
                CommandParameter.newType("xRot", false, CommandParamType.VALUE),
                CommandParameter.newType("yRot", false, CommandParamType.VALUE)
        });
        this.commandParameters.put("set-pos", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("pos", false, new String[]{"pos"}),
                CommandParameter.newType("position", false, CommandParamType.POSITION),
        });
        this.commandParameters.put("set-pos-rot", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("pos", false, new String[]{"pos"}),
                CommandParameter.newType("position", false, CommandParamType.POSITION),
                CommandParameter.newEnum("rot", false, new String[]{"rot"}),
                CommandParameter.newType("xRot", false, CommandParamType.VALUE),
                CommandParameter.newType("yRot", false, CommandParamType.VALUE)
        });
        this.commandParameters.put("set-ease-default", new CommandParameter[]{
                CommandParameter.newType("players", false, CommandParamType.TARGET),
                CommandParameter.newEnum("set", false, new String[]{"set"}),
                CommandParameter.newEnum("preset", false, CommandEnum.CAMERA_PRESETS),
                CommandParameter.newEnum("ease", false, new String[]{"ease"}),
                CommandParameter.newType("easeTime", false, CommandParamType.FLOAT),
                CommandParameter.newEnum("easeType", false, EASE_TYPES),
                CommandParameter.newEnum("default", true, new String[]{"default"})
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
                CommandParameter.newType("yRot", false, CommandParamType.VALUE)
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
                CommandParameter.newType("yRot", false, CommandParamType.VALUE)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        CameraInstructionPacket pk = new CameraInstructionPacket();
        // todo: add messages to notice sender & add translations & add args checks
        switch (args[0]) {
            case "clear" -> {
                pk.setClear(OptionalBoolean.of(true));
            }
            case "fade" -> {
                pk.setFadeInstruction(new CameraFadeInstruction());
            }
            case "fade-color" -> {
                pk.setFadeInstruction(new CameraFadeInstruction());
                pk.getFadeInstruction().setColor(new Color(Float.parseFloat(args[3]), Float.parseFloat(args[4]), Float.parseFloat(args[5])));
            }
            case "fade-time-color" -> {
                pk.setFadeInstruction(new CameraFadeInstruction());
                pk.getFadeInstruction().setColor(new Color(Float.parseFloat(args[3]), Float.parseFloat(args[4]), Float.parseFloat(args[5])));
                pk.getFadeInstruction().setTimeData(new CameraFadeInstruction.TimeData(Float.parseFloat(args[7]), Float.parseFloat(args[8]), Float.parseFloat(args[9])));
            }
            case "set-default" -> {
                CameraPreset preset = CameraPresetManager.getPreset(args[2]);
                if (preset == null) {
                    return false;
                }
                pk.setSetInstruction(new CameraSetInstruction());
            }
            case "set-rot" -> {
                CameraPreset preset = CameraPresetManager.getPreset(args[2]);
                if (preset == null) {
                    return false;
                }
                pk.setSetInstruction(new CameraSetInstruction());
                pk.getSetInstruction().setPreset(preset);
                pk.getSetInstruction().setRot(new Vector2f(0, 0));
            }
            case "set-pos" -> {
                CameraPreset preset = CameraPresetManager.getPreset(args[2]);
                if (preset == null) {
                    return false;
                }
                Position position = new Position();
                pk.setSetInstruction(new CameraSetInstruction());
                pk.getSetInstruction().setPreset(preset);
                pk.getSetInstruction().setPos(new Vector3f((float) position.getX(), (float) position.getY(), (float) position.getZ()));
            }
            case "set-pos-rot" -> {
                CameraPreset preset = CameraPresetManager.getPreset(args[2]);
                if (preset == null) {
                    return false;
                }
                Position position = new Position(Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]));
                pk.setSetInstruction(new CameraSetInstruction());
                pk.getSetInstruction().setPreset(preset);
                pk.getSetInstruction().setPos(new Vector3f((float) position.getX(), (float) position.getY(), (float) position.getZ()));
                pk.getSetInstruction().setRot(new Vector2f(Float.parseFloat(args[6]), Float.parseFloat(args[7])));
            }
            case "set-ease-default" -> {
                CameraPreset preset = CameraPresetManager.getPreset(args[2]);
                if (preset == null) {
                    return false;
                }
                float easeTime = Float.parseFloat(args[4]);
                CameraEase easeType = CameraEase.valueOf(args[5].toUpperCase());
                pk.setSetInstruction(new CameraSetInstruction());
                pk.getSetInstruction().setEase(new CameraSetInstruction.EaseData(easeType, easeTime));
            }
            case "set-ease-rot" -> {
                CameraPreset preset = CameraPresetManager.getPreset(args[2]);
                if (preset == null) {
                    return false;
                }
                float easeTime = Float.parseFloat(args[4]);
                CameraEase easeType = CameraEase.valueOf(args[5].toUpperCase());
                pk.setSetInstruction(new CameraSetInstruction());
                pk.getSetInstruction().setPreset(preset);
                pk.getSetInstruction().setEase(new CameraSetInstruction.EaseData(easeType, easeTime));
                pk.getSetInstruction().setRot(new Vector2f(Float.parseFloat(args[7]), Float.parseFloat(args[8])));
            }
            case "set-ease-pos" -> {
                CameraPreset preset = CameraPresetManager.getPreset(args[2]);
                if (preset == null) {
                    return false;
                }
                float easeTime = Float.parseFloat(args[4]);
                CameraEase easeType = CameraEase.valueOf(args[5].toUpperCase());
                Vector3f position = new Vector3f(Float.parseFloat(args[6]), Float.parseFloat(args[7]), Float.parseFloat(args[8]));
                pk.setSetInstruction(new CameraSetInstruction());
                pk.getSetInstruction().setPreset(preset);
                pk.getSetInstruction().setEase(new CameraSetInstruction.EaseData(easeType, easeTime));
                pk.getSetInstruction().setPos(position);
            }
            case "set-ease-pos-rot" -> {
                CameraPreset preset = CameraPresetManager.getPreset(args[2]);
                if (preset == null) {
                    return false;
                }
                float easeTime = Float.parseFloat(args[4]);
                CameraEase easeType = CameraEase.valueOf(args[5].toUpperCase());
                Vector3f position = new Vector3f(Float.parseFloat(args[6]), Float.parseFloat(args[7]), Float.parseFloat(args[8]));
                pk.setSetInstruction(new CameraSetInstruction());
                pk.getSetInstruction().setPreset(preset);
                pk.getSetInstruction().setEase(new CameraSetInstruction.EaseData(easeType, easeTime));
                pk.getSetInstruction().setPos(position);
                pk.getSetInstruction().setRot(new Vector2f(Float.parseFloat(args[9]), Float.parseFloat(args[10])));
            }
        }
        return true;
    }
}
