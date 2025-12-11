package cn.nukkit.network.protocol.types;

public enum PermissionLevel {

    ANY,
    GAMEDIRECTORS,
    ADMIN,
    HOST,
    OWNER,
    INTERNAL;

    public String getName() {
        return this.name().toLowerCase();
    }
}