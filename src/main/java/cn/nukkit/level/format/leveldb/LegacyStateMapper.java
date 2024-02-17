package cn.nukkit.level.format.leveldb;

public interface LegacyStateMapper {

    int getRuntimeId(int id, int meta);

    int getLegacyFullId(int runtimeId);

    int getBlockId(int runtimeId);

    int getBlockData(int runtimeId);

}
