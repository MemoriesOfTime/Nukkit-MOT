package cn.nukkit.utils;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 角色（皮肤创建器）部件类型枚举，序数与 Bedrock v2168 协议一致。
 * <p>
 * Persona (character creator) piece type enum; ordinals align with the Bedrock v2168 protocol.
 * <p>
 * Adapted from NukkitPetteriM1Edition and CloudburstMC Protocol
 * (<a href="https://github.com/PetteriM1/NukkitPetteriM1Edition">pm1e</a>,
 * <a href="https://github.com/CloudburstMC/Protocol">CloudburstMC Protocol</a>).
 */
public enum PersonaPieceType {

    UNKNOWN("unknown", "persona_unknown"),
    SKELETON("skeleton", "persona_skeleton"),
    BODY("body", "persona_body"),
    SKIN("skin", "persona_skin"),
    BOTTOM("bottom", "persona_bottom"),
    FEET("feet", "persona_feet"),
    DRESS("dress", "persona_dress"),
    TOP("top", "persona_top"),
    HIGH_PANTS("high_pants", "persona_high_pants"),
    HANDS("hands", "persona_hand"),
    OUTERWEAR("outerwear", "persona_outerwear"),
    FACIAL_HAIR("facialhair", "persona_facial_hair"),
    MOUTH("mouth", "persona_mouth"),
    EYES("eyes", "persona_eyes"),
    HAIR("hair", "persona_hair"),
    HOOD("hood", "persona_hood"),
    BACK("back", "persona_back"),
    FACE_ACCESSORY("faceaccessory", "persona_face_accessory"),
    HEAD("head", "persona_head"),
    LEGS("legs", "persona_legs"),
    LEFT_LEG("leftleg", "persona_left_leg"),
    RIGHT_LEG("rightleg", "persona_right_leg"),
    ARMS("arms", "persona_arms"),
    LEFT_ARM("leftarm", "persona_left_arm"),
    RIGHT_ARM("rightarm", "persona_right_arm"),
    CAPES("capes", "persona_capes"),
    CLASSIC_SKIN("classicskin", "persona_classic_skin"),
    EMOTE("emote", "persona_emote"),
    UNSUPPORTED("unsupported", "unsupported");

    /**
     * 协议序列化使用的短名（如 "body"）。
     * <p>
     * Short name used for protocol serialization (e.g. "body").
     */
    @Getter
    private final String serializeName;

    /**
     * 带前缀的类型名（如 "persona_body"），客户端 JWT 可能发送此格式。
     * <p>
     * Prefixed type name (e.g. "persona_body"); the client JWT may send this form.
     */
    @Getter
    private final String type;

    private static final Map<String, PersonaPieceType> BY_NAME = new HashMap<>(values().length * 2, 1);

    static {
        for (PersonaPieceType value : values()) {
            BY_NAME.put(value.serializeName, value);
            BY_NAME.put(value.type, value);
        }
    }

    PersonaPieceType(String serializeName, String type) {
        this.serializeName = serializeName;
        this.type = type;
    }

    /**
     * 按名称查找，同时接受 serializeName 与 type 两种格式。
     * <p>
     * Lookup accepting both serializeName and type forms.
     *
     * @param name serializeName 或 type 字符串 / a serializeName or type string
     * @return 匹配的枚举常量，未匹配时返回 {@link #UNKNOWN}
     */
    public static PersonaPieceType fromName(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        PersonaPieceType value = BY_NAME.get(name);
        return value != null ? value : UNKNOWN;
    }

    /**
     * 安全地按序数查找，越界时回退 {@link #UNKNOWN}，避免恶意/损坏数据导致数组越界。
     * <p>
     * Safe ordinal lookup; falls back to {@link #UNKNOWN} on out-of-bounds to avoid
     * crashes from malicious or corrupt data.
     */
    public static PersonaPieceType fromOrdinal(int ordinal) {
        PersonaPieceType[] all = values();
        if (ordinal < 0 || ordinal >= all.length) {
            return UNKNOWN;
        }
        return all[ordinal];
    }
}
