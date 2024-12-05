package cn.nukkit.utils;

public interface ThrowableRunnable extends Runnable {

    @Override
    default void run() {
        try {
            run0();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    void run0() throws Exception;
}