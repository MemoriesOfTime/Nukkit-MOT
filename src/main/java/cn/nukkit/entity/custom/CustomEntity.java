package cn.nukkit.entity.custom;

public interface CustomEntity {

    EntityDefinition getEntityDefinition();

    default int getNetworkId() {
        return this.getEntityDefinition().getRuntimeId();
    }
}

