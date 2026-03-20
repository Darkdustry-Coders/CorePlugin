package mindurka.util;

import arc.func.Boolf;
import arc.func.Cons;
import arc.func.Func;
import arc.func.Intf;
import arc.func.Longf;

/**
 * An object protected with a {@code synchronized} statement.
 * <p>
 * Exists exclusively cuz {@code synchronized} is deprecated in Kotlin.
 */
public class Mutex<T> {
    private final T object;

    /**
     * Create a new {@link Mutex}.
     *
     * @param object The object to enclose.
     */
    public Mutex(T object) {
        this.object = object;
    }

    /**
     * Mutate the inner object.
     * <p>
     * While it is technically possible to set an external field to an object using this method,
     * doing so is extremely unsound, and will warrant a shovel hammering your spine cord.
     *
     * @param proc The mutator procedure.
     */
    public void mutv(Cons<T> proc) {
        synchronized (object) {
            proc.get(object);
        }
    }

    /**
     * Mutate the inner object and return a boolean.
     * <p>
     * While it is technically possible to set an external field to an object using this method,
     * doing so is extremely unsound, and will warrant a shovel hammering your spine cord.
     *
     * @param proc The mutator procedure.
     * @return The value returned by the mutator.
     */
    public boolean mutb(Boolf<T> proc) {
        synchronized (object) {
            return proc.get(object);
        }
    }

    /**
     * Mutate the inner object and return an int.
     * <p>
     * While it is technically possible to set an external field to an object using this method,
     * doing so is extremely unsound, and will warrant a shovel hammering your spine cord.
     *
     * @param proc The mutator procedure.
     * @return The value returned by the mutator.
     */
    public int muti(Intf<T> proc) {
        synchronized (object) {
            return proc.get(object);
        }
    }

    /**
     * Mutate the inner object and return a long.
     * <p>
     * While it is technically possible to set an external field to an object using this method,
     * doing so is extremely unsound, and will warrant a shovel hammering your spine cord.
     *
     * @param proc The mutator procedure.
     * @return The value returned by the mutator.
     */
    public long mutl(Longf<T> proc) {
        synchronized (object) {
            return proc.get(object);
        }
    }

    /**
     * Mutate the inner object.
     * <p>
     * While it is technically possible to return the inner object with this method,
     * doing so is extremely unsound, and will warrant a shovel hammering your spine
     * cord.
     *
     * @param <R> The type of the value to return after mutation.
     * @param proc The mutator procedure.
     * @return The value returned by the mutator.
     */
    public <R> R mut(Func<T, R> proc) {
        synchronized (object) {
            return proc.get(object);
        }
    }
}
