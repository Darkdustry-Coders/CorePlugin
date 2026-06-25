package mindurka.coreplugin.recording

import java.io.RandomAccessFile

private val header = byteArrayOf('M'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(), 0)

/**
 * A new recording of the game.
 *
 * Target file is automatically erased.
 **/
class ActiveRecording(val dest: RandomAccessFile) {
    private val buffer = ByteArray(1024 * 1024 * 8)

    init {
        dest.setLength(0)
        dest.seek(0)
        dest.write(header)
    }
}