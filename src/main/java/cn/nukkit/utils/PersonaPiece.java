package cn.nukkit.utils;

import lombok.ToString;

import java.util.UUID;

/**
 * 角色（皮肤创建器）部件。
 * <p>
 * Persona (character creator) skin piece.
 */
@ToString
public class PersonaPiece {

    public final String id;
    public final PersonaPieceType type;
    public final UUID packId;
    public final boolean isDefault;
    public final String productId;

    /**
     * 按字符串构造，pieceType 与 packId 在内部转换为枚举/UUID。
     * 保留此构造器以兼容现有调用方与 NBT/JWT 解析。
     * <p>
     * String-based constructor; pieceType and packId are converted to enum/UUID internally.
     * Retained for compatibility with existing callers and NBT/JWT parsing.
     */
    public PersonaPiece(String id, String type, String packId, boolean isDefault, String productId) {
        this(id, PersonaPieceType.fromName(type), parsePackId(packId), isDefault, productId);
    }

    public PersonaPiece(String id, PersonaPieceType type, UUID packId, boolean isDefault, String productId) {
        this.id = id;
        this.type = type;
        this.packId = packId;
        this.isDefault = isDefault;
        this.productId = productId;
    }

    private static UUID parsePackId(String packId) {
        if (packId == null || packId.isEmpty()) {
            return new UUID(0, 0);
        }
        try {
            return UUID.fromString(packId);
        } catch (IllegalArgumentException ignored) {
            return new UUID(0, 0);
        }
    }
}
