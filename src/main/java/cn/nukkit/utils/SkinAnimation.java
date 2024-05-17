package cn.nukkit.utils;

import lombok.ToString;

/**
 * Skin animation
 */
@ToString
public class SkinAnimation {

    public static final int TYPE_NONE = 0;
    public static final int TYPE_FACE = 1;
    public static final int TYPE_BODY_32x32 = 2;
    public static final int TYPE_BODY_128x128 = 3;

    public static final int EXPRESSION_LINER = 0;
    public static final int EXPRESSION_BLINKING = 1;

    public final SerializedImage image;
    public final int type;
    public final float frames;
    public final int expression;

    public SkinAnimation(SerializedImage image, int type, float frames, int expression) {
        this.image = image;
        this.type = type;
        this.frames = frames;
        this.expression = expression;
    }
}
