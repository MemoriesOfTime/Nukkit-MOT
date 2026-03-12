package cn.nukkit.level.util;

public class SingletonBitArray implements BitArray {

    public static final SingletonBitArray INSTANCE = new SingletonBitArray();
    private static final int[] EMPTY_WORDS = new int[0];

    public SingletonBitArray() {
    }

    @Override
    public SingletonBitArray copy() {
        return this;
    }

    @Override
    public int get(int index) {
        return 0;
    }

    @Override
    public int[] getWords() {
        return EMPTY_WORDS;
    }

    @Override
    public BitArrayVersion getVersion() {
        return BitArrayVersion.V0;
    }

    @Override
    public void set(int index, int value) {
    }

    @Override
    public int size() {
        return 1;
    }
}
