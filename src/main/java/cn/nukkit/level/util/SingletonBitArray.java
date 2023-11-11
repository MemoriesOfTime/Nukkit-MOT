package cn.nukkit.level.util;

import com.google.common.base.Preconditions;

public class SingletonBitArray implements BitArray {

    private static final int[] EMPTY_WORDS = new int[0];

    private int value;
    private final int size;

    SingletonBitArray(int size) {
        this.size = size;
    }

    SingletonBitArray(int size, int value) {
        this.size = size;
        this.value = value;
    }

    @Override
    public void set(int index, int value) {
        Preconditions.checkElementIndex(index, this.size);
        this.value = value;
    }

    @Override
    public int get(int index) {
        Preconditions.checkElementIndex(index, this.size);
        return this.value;
    }

    @Override
    public int size() {
        return this.size;
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
    public BitArray copy() {
        return new SingletonBitArray(this.size, this.value);
    }
}
