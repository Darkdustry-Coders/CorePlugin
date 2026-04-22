package mindurka.util;

import arc.func.Boolf;
import arc.func.Cons;
import arc.func.Func;
import arc.func.Intf;
import arc.func.Longf;
import arc.util.Log;

/**
 * An object protected with a {@code synchronized} statement.
 * <p>
 * Exists exclusively cuz {@code synchronized} is deprecated in Kotlin.
 */
public class Mutex<T> {
    private static int nextId = 0;
    private final int id = nextId++;

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
        Log.debug("Lock "+id+" acquired");
        synchronized (object) {
            proc.get(object);
        }
        Log.debug("Lock "+id+" released");
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
        Log.debug("Lock "+id+" acquired");
        synchronized (object) {
            boolean v = proc.get(object);
            Log.debug("Lock "+id+" released");
            return v;
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
        Log.debug("Lock "+id+" acquired");
        synchronized (object) {
            int v = proc.get(object);
            Log.debug("Lock "+id+" released");
            return v;
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
        Log.debug("Lock "+id+" acquired");
        synchronized (object) {
            long v = proc.get(object);
            Log.debug("Lock "+id+" released");
            return v;
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
        Log.debug("Lock "+id+" acquired");
        synchronized (object) {
            R v = proc.get(object);
            Log.debug("Lock "+id+" released");
            return v;
        }
    }
}
