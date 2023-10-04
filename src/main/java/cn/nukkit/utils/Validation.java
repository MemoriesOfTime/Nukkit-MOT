package cn.nukkit.utils;

import lombok.experimental.UtilityClass;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author joserobjr
 * @since 2020-10-11
 */
@UtilityClass
public class Validation {
    /**
     * Throws an exception if the value is negative.
     *
     * @param arg   The name of the argument, will be placed in front of the exception message if the value is is not null.
     * @param value The argument value to be validated.
     * @throws IllegalArgumentException If the value is negative.
     */
    public static void checkPositive(@Nullable String arg, byte value) {
        if (value < 0) {
            throw new IllegalArgumentException((arg != null ? arg + ": " : "") + "Negative value is not allowed: " + value);
        }
    }

    /**
     * Throws an exception if the value is negative.
     *
     * @param arg   The name of the argument, will be placed in front of the exception message if the value is is not null.
     * @param value The argument value to be validated.
     * @throws IllegalArgumentException If the value is negative.
     */
    public static void checkPositive(@Nullable String arg, short value) {
        if (value < 0) {
            throw new IllegalArgumentException((arg != null ? arg + ": " : "") + "Negative value is not allowed: " + value);
        }
    }

    /**
     * Throws an exception if the value is negative.
     *
     * @param arg   The name of the argument, will be placed in front of the exception message if the value is is not null.
     * @param value The argument value to be validated.
     * @throws IllegalArgumentException If the value is negative.
     */
    public static void checkPositive(@Nullable String arg, int value) {
        if (value < 0) {
            throw new IllegalArgumentException((arg != null ? arg + ": " : "") + "Negative value is not allowed: " + value);
        }
    }

    /**
     * Throws an exception if the value is negative.
     *
     * @param arg   The name of the argument, will be placed in front of the exception message if the value is is not null.
     * @param value The argument value to be validated.
     * @throws IllegalArgumentException If the value is negative.
     */
    public static void checkPositive(@Nullable String arg, long value) {
        if (value < 0) {
            throw new IllegalArgumentException((arg != null ? arg + ": " : "") + "Negative value is not allowed: " + value);
        }
    }

    /**
     * Throws an exception if the value is negative.
     *
     * @param arg   The name of the argument, will be placed in front of the exception message if the value is is not null.
     * @param value The argument value to be validated.
     * @throws IllegalArgumentException If the value is negative.
     */
    public static void checkPositive(@Nullable String arg, float value) {
        if (value < 0) {
            throw new IllegalArgumentException((arg != null ? arg + ": " : "") + "Negative value is not allowed: " + value);
        }
    }

    /**
     * Throws an exception if the value is negative.
     *
     * @param arg   The name of the argument, will be placed in front of the exception message if the value is is not null.
     * @param value The argument value to be validated.
     * @throws IllegalArgumentException If the value is negative.
     */
    public static void checkPositive(@Nullable String arg, double value) {
        if (value < 0) {
            throw new IllegalArgumentException((arg != null ? arg + ": " : "") + "Negative value is not allowed: " + value);
        }
    }

    /**
     * Throws an exception if the value is negative.
     *
     * @param arg   The name of the argument, will be placed in front of the exception message if the value is is not null.
     * @param value The argument value to be validated.
     * @throws IllegalArgumentException If the value is negative.
     */
    public static void checkPositive(@Nullable String arg, BigInteger value) {
        if (value.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException((arg != null ? arg + ": " : "") + "Negative value is not allowed: " + value);
        }
    }

    /**
     * Throws an exception if the value is negative.
     *
     * @param arg   The name of the argument, will be placed in front of the exception message if the value is is not null.
     * @param value The argument value to be validated.
     * @throws IllegalArgumentException If the value is negative.
     */
    public static void checkPositive(@Nullable String arg, BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException((arg != null ? arg + ": " : "") + "Negative value is not allowed: " + value);
        }
    }
}
