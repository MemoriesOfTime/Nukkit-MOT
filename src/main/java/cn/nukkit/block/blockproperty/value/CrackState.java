package cn.nukkit.block.blockproperty.value;

import javax.annotation.Nullable;

public enum CrackState {
    NO_CRACKS,
    
    CRACKED,
    
    MAX_CRACKED {
        @Nullable
        @Override
        public CrackState getNext() {
            return null;
        }
    };
    private static final CrackState[] VALUES = values();

    @Nullable
    public CrackState getNext() {
        return VALUES[ordinal() + 1];
    }
}
