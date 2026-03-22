package mindurka.util;

import arc.func.Cons;

/**
 * Basic operations cuz in Kotlin for some reason they are
 * experimental.
 */
public class Ops {
    private Ops() {}

    /**
     * Get a bitwise or of several values.
     *
     * @param first First value
     * @param others All the other values
     *
     * @return All values or-d
     */
    public static byte bitOr(byte first, byte... others) {
        for (byte other: others) first |= other;
        return first;
    }
    /**
     * Get a bitwise or of several values.
     *
     * @param first First value
     * @param others All the other values
     *
     * @return All values or-d
     */
    public static short bitOr(short first, short... others) {
        for (short other: others) first |= other;
        return first;
    }
    /**
     * Get a bitwise or of several values.
     *
     * @param first First value
     * @param others All the other values
     *
     * @return All values or-d
     */
    public static int bitOr(int first, int... others) {
        for (int other: others) first |= other;
        return first;
    }
    /**
     * Get a bitwise or of several values.
     *
     * @param first First value
     * @param others All the other values
     *
     * @return All values or-d
     */
    public static long bitOr(long first, long... others) {
        for (long other: others) first |= other;
        return first;
    }

    /**
     * Get a bitwise and of several values.
     *
     * @param first First value
     * @param others All the other values
     *
     * @return All values and-d
     */
    public static byte bitAnd(byte first, byte... others) {
        for (byte other: others) first &= other;
        return first;
    }
    /**
     * Get a bitwise and of several values.
     *
     * @param first First value
     * @param others All the other values
     *
     * @return All values and-d
     */
    public static short bitAnd(short first, short... others) {
        for (short other: others) first &= other;
        return first;
    }
    /**
     * Get a bitwise and of several values.
     *
     * @param first First value
     * @param others All the other values
     *
     * @return All values and-d
     */
    public static int bitAnd(int first, int... others) {
        for (int other: others) first &= other;
        return first;
    }
    /**
     * Get a bitwise and of several values.
     *
     * @param first First value
     * @param others All the other values
     *
     * @return All values and-d
     */
    public static long bitAnd(long first, long... others) {
        for (long other: others) first &= other;
        return first;
    }
}
