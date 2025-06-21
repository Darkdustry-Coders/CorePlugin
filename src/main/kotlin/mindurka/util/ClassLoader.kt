package mindurka.util

import mindurka.util.empty
import arc.files.Fi
import mindurka.util.map
import java.util.jar.JarFile
import java.net.URLDecoder
import arc.util.Log

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
