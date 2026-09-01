package cn.nukkit.utils;

import com.google.common.collect.ImmutableList;
import lombok.ToString;

import java.util.List;

/**
 * 角色（皮肤创建器）部件染色。
 * <p>
 * Persona (character creator) skin piece tint.
 */
@ToString
public class PersonaPieceTint {

    public final PersonaPieceType pieceType;
    public final ImmutableList<String> colors;

    /**
     * 按字符串构造，pieceType 在内部转换为枚举。
     * <p>
     * String-based constructor; pieceType is converted to enum internally.
     */
    public PersonaPieceTint(String pieceType, List<String> colors) {
        this(PersonaPieceType.fromName(pieceType), colors);
    }

    public PersonaPieceTint(PersonaPieceType pieceType, List<String> colors) {
        this.pieceType = pieceType;
        this.colors = ImmutableList.copyOf(colors);
    }
}
