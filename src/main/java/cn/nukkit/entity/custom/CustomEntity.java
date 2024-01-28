package cn.nukkit.entity.custom;

import cn.nukkit.utils.Identifier;

public interface CustomEntity {

    EntityDefinition getEntityDefinition();

    default Identifier getIdentifier() {
        return new Identifier(this.getEntityDefinition().getIdentifier());
    }

    default int getNetworkId() {
        return this.getEntityDefinition().getRuntimeId();
    }
}

