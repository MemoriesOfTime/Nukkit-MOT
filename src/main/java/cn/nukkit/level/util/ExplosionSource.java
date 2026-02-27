package cn.nukkit.level.util;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;

public sealed interface ExplosionSource permits ExplosionSource.EntitySource, ExplosionSource.BlockSource {
    record EntitySource(Entity entity) implements ExplosionSource {}
    record BlockSource(Block block) implements ExplosionSource {}
}
