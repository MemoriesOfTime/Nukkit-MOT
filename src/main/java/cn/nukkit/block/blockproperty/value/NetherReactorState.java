package cn.nukkit.block.blockproperty.value;

public enum NetherReactorState {
    READY,

    INITIALIZED,

    FINISHED;
    
    private static final NetherReactorState[] values = values();
    
    public static NetherReactorState getFromData(int data) {
        return values[data];
    }
}
