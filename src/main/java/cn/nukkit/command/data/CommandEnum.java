package cn.nukkit.command.data;

import cn.nukkit.Server;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.network.protocol.UpdateSoftEnumPacket;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.CameraPresetManager;
import cn.nukkit.utils.Identifier;
import com.google.common.collect.ImmutableList;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author CreeperFace
 */
@EqualsAndHashCode
public class CommandEnum {

    public static final CommandEnum ENUM_BOOLEAN = new CommandEnum("Boolean", ImmutableList.of("true", "false"));
    public static final CommandEnum ENUM_GAMEMODE = new CommandEnum("GameMode",
            ImmutableList.of("survival", "creative", "s", "c", "adventure", "a", "spectator", "view", "v", "spc"));
    /**
     * @since 589
     */
    public static final CommandEnum CAMERA_PRESETS = new CommandEnum("preset", () -> CameraPresetManager.getPresets().keySet());
    /**
     * @since 618
     */
    public static final CommandEnum SCOREBOARD_OBJECTIVES = new CommandEnum("ScoreboardObjectives", () -> Server.getInstance().getScoreboardManager().getScoreboards().keySet());
    public static final CommandEnum CHAINED_COMMAND_ENUM = new CommandEnum("ExecuteChainedOption_0", "run", "as", "at", "positioned", "if", "unless", "in", "align", "anchored", "rotated", "facing");
    public static final CommandEnum ENUM_BLOCK = new CommandEnum("Block", Collections.emptyList());
    public static final CommandEnum ENUM_ITEM = new CommandEnum("Item", Collections.emptyList());
    public static final CommandEnum ENUM_ENTITY = new CommandEnum("Entity", Collections.emptyList());
    public static final CommandEnum ENUM_EFFECT = new CommandEnum("Effect", Arrays.stream(Effect.class.getDeclaredFields())
            .filter(field -> field.getType() == int.class)
            .filter(field -> (field.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL)) == (Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL))
            .map(field -> field.getName().toLowerCase(Locale.ROOT))
            .toList());
    public static final CommandEnum ENUM_ENCHANTMENT = new CommandEnum("enchantmentName", () -> Enchantment.getEnchantmentName2IDMap().keySet().stream()
            .map(name -> name.startsWith(Identifier.DEFAULT_NAMESPACE) ? name.substring(10) : name)
            .toList());

    private final String name;
    private final List<String> values;

    private final boolean isSoft;
    private final Supplier<Collection<String>> strListSupplier;

    public CommandEnum(String name, String... values) {
        this(name, Arrays.asList(values));
    }

    public CommandEnum(String name, List<String> values) {
        this(name, values, false);
    }

    /**
     * 构建一个枚举参数
     *
     * @param name   该枚举的名称，会显示到命令中
     * @param values 该枚举的可选值，不能为空，但是可以为空列表
     * @param isSoft 当为False  时，客户端显示枚举参数会带上枚举名称{@link CommandEnum#getName()},当为true时 则判定为String
     */
    public CommandEnum(String name, List<String> values, boolean isSoft) {
        this.name = name;
        this.values = values;
        this.isSoft = isSoft;
        this.strListSupplier = null;
    }

    /**
     * Instantiates a new Soft Command enum.
     *
     * @param name            the name
     * @param strListSupplier the str list supplier
     */
    public CommandEnum(String name, Supplier<Collection<String>> strListSupplier) {
        this.name = name;
        this.values = null;
        this.isSoft = true;
        this.strListSupplier = strListSupplier;
    }

    public String getName() {
        return name;
    }

    public List<String> getValues() {
        if (this.strListSupplier != null) {
            return strListSupplier.get().stream().toList();
        }
        return values;
    }

    public boolean isSoft() {
        return isSoft;
    }

    public void updateSoftEnum(UpdateSoftEnumPacket.Type mode, String... value) {
        if (!this.isSoft) {
            return;
        }
        UpdateSoftEnumPacket pk = new UpdateSoftEnumPacket();
        pk.name = this.getName();
        pk.values = value;
        pk.type = mode;
        Server.broadcastPacket(Server.getInstance().getOnlinePlayers().values(), pk);
    }

    public void updateSoftEnum() {
        if (this.strListSupplier == null) {
            return;
        }
        this.updateSoftEnum(UpdateSoftEnumPacket.Type.SET, this.getValues().toArray(new String[0]));
    }
}
