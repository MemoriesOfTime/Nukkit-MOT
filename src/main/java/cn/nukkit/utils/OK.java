package cn.nukkit.utils;

import lombok.Data;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 可携带异常信息的的结果
 *
 * @param <E> the error parameter
 */
@Data
public class OK<E> {
    public static final OK<Void> TRUE = new OK<>(true);

    private boolean ok;
    private E error;

    public OK(boolean ok) {
        this(ok, null);
    }

    public OK(boolean ok, @Nullable E error) {
        this.ok = ok;
        this.error = error;
    }

    public Throwable getError() {
        if (error instanceof Throwable) {
            Throwable throwable = (Throwable) error;
            return new AssertionError(throwable);
        } else {
            return new AssertionError(Objects.toString(error, "Unknown error"));
        }
    }

    /**
     * 断言该结果是否为真，如果不为真则会抛出AssertionError
     * <p>
     * Asserts whether the result is true or not, and throws an AssertionError if it is not true
     *
     * @throws AssertionError the assertion error
     */
    public void assertOK() throws AssertionError {
        if (!ok) {
            if (error instanceof Throwable) {
                Throwable throwable = (Throwable) error;
                throw new AssertionError(throwable);
            } else {
                throw new AssertionError(Objects.toString(error, "Unknown error"));
            }
        }
    }
}
