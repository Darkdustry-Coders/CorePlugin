package mindurka.util

import arc.files.Fi
import arc.struct.ObjectMap
import mindurka.coreplugin.Build
import java.io.InputStream
import java.net.URL
import java.util.jar.JarFile
import java.net.URLDecoder
import java.util.Enumeration

fun ClassLoader.listResources(path: String): Iterator<String> {
    val url = getResource(path) ?: return empty()

    return when (url.getProtocol()) {
        "file" -> Fi.get(url.path).list().iterator().map { it.absolutePath() }
        "jar" -> {
            val i = url.path.indexOf('!')
            val archive = URLDecoder.decode(url.path.substring(0, i), charset("UTF-8"))
            val path = URLDecoder.decode(url.path.substring(i + 1), charset("UTF-8"))
            val archiveFile = JarFile(if (archive.startsWith("file:")) archive.substring(5) else archive)

            archiveFile.entries().iterator()
                .filter { val p = "/${it.name}"; p.startsWith(path) && !(p.length - path.length == 1 && p.endsWith('/')) }
                .map { it.name }
        }
        else -> empty()
    }
}

private val prefixedCache = ObjectMap<String, ClassLoader>()
/**
 * Get prefixed ClassLoader.
 *
 * On native builds, all resources prefixed classloader fetches will be prefixed with ["$prefix/"][prefix], otherwise
 * the passed [ClassLoader] is returned.
 *
 * Classloaders are cached.
 */
fun ClassLoader.prefixed(prefix: String): ClassLoader {
    return if (Build.nativeImage) prefixedCache.getOrPut(prefix) { object : ClassLoader(this) {
        override fun getResource(name: String): URL? {
            return super.getResource("$prefix/$name")
        }

        override fun getResourceAsStream(name: String): InputStream? {
            return super.getResourceAsStream("$prefix/$name")
        }

        override fun getResources(name: String): Enumeration<URL?>? {
            return super.getResources("$prefix/$name")
        }
    } } else this
}

object ClassLoaders {
    /**
     * Get prefixed ClassLoader.
     *
     * On native builds, all resources prefixed classloader fetches will be prefixed with ["$prefix/"][prefix], otherwise
     * the passed [ClassLoader] is returned.
     *
     * Classloaders are cached.
     */
    @JvmStatic
    fun prefixed(classLoader: ClassLoader, prefix: String) = classLoader.prefixed(prefix)
}
