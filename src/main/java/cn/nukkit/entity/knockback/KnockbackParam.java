package cn.nukkit.entity.knockback;

import lombok.Getter;
import lombok.Setter;

public class KnockbackParam {
    // 水平击退
    @Setter
    @Getter
    private float horizontal = 0.52f;

    // 垂直击退
    @Setter
    @Getter
    private float vertical = 0.45f;

    // 击退持续时间
    @Setter
    @Getter
    private int interval = 10;

    // 每级击退附魔增加的水平击退
    @Setter
    @Getter
    private float horizontalIncrementPerEnchantLevel = 0.12f;

    // 有击退附魔时的增加的垂直击退
    @Getter
    @Setter
    private float verticalIncrementWhenHasEnchant = 0.05f;
}

