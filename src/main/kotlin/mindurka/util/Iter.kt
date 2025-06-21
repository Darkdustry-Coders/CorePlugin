package mindurka.util

import arc.func.Func
import arc.math.Mathf
import mindurka.annotations.PublicAPI
import arc.func.Boolf
import arc.func.Boolf2
import arc.func.Func2
import arc.struct.Seq

@PublicAPI
fun <T> Iterator<T>.collect(collection: Seq<T>): Seq<T> {
    for (it in this) collection.add(it)
    return collection 
}
@PublicAPI
fun <T, C: MutableCollection<T>> Iterator<T>.collect(collection: C): C {
    for (it in this) collection.add(it)
    return collection 
}

@PublicAPI
fun <T> Iterator<T>.findOrNull(with: Boolf<T>): T? {
    for (x in this) if (with[x]) return x
    return null
}

@PublicAPI
fun <T, I: Iterator<T>> I.skip(amount: UInt): I {
    for (i in 0U..<amount) if (hasNext()) next() else break
    return this
}

@PublicAPI
fun <T> Iterator<T>.any(with: Boolf<T>): Boolean {
    for (x in this) if (with[x]) return true
    return false 
}

@PublicAPI
fun <T> Iterator<T>.all(with: Boolf<T>): Boolean {
    for (x in this) if (!with[x]) return false
    return true
}

@PublicAPI
fun <T> Iterator<T>.fold(start: T, with: Func2<T, T, T>): T {
    var v = start
    for (it in this) v = with[v, it]
    return v
}

@PublicAPI
fun <T> Iterator<T>.take(amount: UInt): Taken<T> = Taken(this, amount)
@PublicAPI
class Taken<T>(private val iter: Iterator<T>, private var remaining: UInt): Iterator<T> {
    override fun hasNext(): Boolean = iter.hasNext() && remaining > 0U
    override fun next(): T =
        if (remaining > 0U) {
            remaining--
            iter.next()
        }
        else throw IndexOutOfBoundsException("Calling '.next()' on an empty iterator")
}

@PublicAPI
fun <T, Y> Iterator<T>.map(with: Func<T, Y>): Mapped<T, Y> = Mapped(this, with)
@PublicAPI
class Mapped<T, Y>(private val iter: Iterator<T>, private val f: Func<T, Y>): Iterator<Y> {
    override fun hasNext(): Boolean = iter.hasNext()
    override fun next(): Y = f[iter.next()]
}

@PublicAPI
fun <T> Iterator<T>.enumerate(): Enumerated<T> = Enumerated<T>(this)
@PublicAPI
class Enumerated<T>(private val iter: Iterator<T>): Iterator<Pair<Int, T>> {
    var i = 0

    override fun hasNext(): Boolean = iter.hasNext()
    override fun next(): Pair<Int, T> {
        if (!hasNext()) throw IndexOutOfBoundsException("Calling '.next()' on an empty iterator")

        return (i++).to(iter.next())
    }

    fun all2(with: Boolf2<Int, T>): Boolean {
        while (hasNext()) if (!with[i++, iter.next()]) return false
        return true
    }

    fun any2(with: Boolf2<Int, T>): Boolean {
        while (hasNext()) if (with[i++, iter.next()]) return true
        return false
    }
}

@PublicAPI
fun <T, Y> Iterator<T>.zip(other: Iterator<Y>): Zipped<T, Y> = Zipped<T, Y>(this, other)
@PublicAPI
class Zipped<T, Y>(private val first: Iterator<T>, private val second: Iterator<Y>): Iterator<Pair<T, Y>> {
    override fun hasNext(): Boolean = first.hasNext() && second.hasNext()
    override fun next(): Pair<T, Y> {
        if (!hasNext()) throw IndexOutOfBoundsException("Calling '.next()' on an empty iterator") 

        return first.next() to second.next()
    }

    fun all2(with: Boolf2<T, Y>): Boolean {
        while (hasNext()) if (!with[first.next(), second.next()]) return false
        return true
    }

    fun any2(with: Boolf2<T, Y>): Boolean {
        while (hasNext()) if (with[first.next(), second.next()]) return true
        return false
    }
}

@PublicAPI
fun <T> Iterator<T>.filter(with: Boolf<T>): Filtered<T> = Filtered(this, with)
@PublicAPI
class Filtered<T>(private val iter: Iterator<T>, private val f: Boolf<T>): Iterator<T> {
    private var next: T? = null
    private var hasNext = false

    init {
        while (iter.hasNext()) {
            next = iter.next()
            if (f[next]) {
                hasNext = true
                break
            }
        }
    }

    override fun hasNext(): Boolean = hasNext
    override fun next(): T {
        if (!hasNext) throw IndexOutOfBoundsException("Calling '.next()' on an empty iterator")
        val n = next
        hasNext = false
        while (iter.hasNext()) {
            next = iter.next()
            if (f[next]) {
                hasNext = true
                break
            }
        }
        return n!!
    }
}

@PublicAPI
fun <T> Iterator<T>.join(sep: String = "", start: String = "", end: String = ""): String {
    val builder = StringBuilder(start)
    var i = 0
    for (it in this) {
        if (i++ > 0) builder.append(sep)
        builder.append(it)
    }
    return builder.append(end).toString()
}

@PublicAPI
fun <T> Iterator<T>.random(): T? {
    if (!hasNext()) return null
    
    var item = next()
    var i = 1

    while (hasNext()) {
        val o = next()
        if (Mathf.random(0, i) == 0) item = o
    }

    return item
}

@Suppress("UNCHECKED_CAST")
@PublicAPI
fun <T> empty(): Empty<T> = Empty.any as Empty<T> // Since 'Empty' never creates an instance of an object,
@PublicAPI
class Empty<T>: Iterator<T> {
    companion object {
        val any = Empty<Any>()
    }

    override fun hasNext(): Boolean = false
    override fun next() = throw IndexOutOfBoundsException("Calling '.next()' on an empty iterator")
}

@PublicAPI
object Iterators {
    @PublicAPI
    @JvmStatic
    fun <T, Y> map(iter: Iterator<T>, with: Func<T, Y>): Mapped<T, Y> = Mapped(iter, with)
    @PublicAPI
    @JvmStatic
    fun <T> filter(iter: Iterator<T>, with: Boolf<T>): Filtered<T> = Filtered(iter, with)
    @PublicAPI
    @JvmStatic
    fun <T> random(iter: Iterator<T>): T? = iter.random()
}
