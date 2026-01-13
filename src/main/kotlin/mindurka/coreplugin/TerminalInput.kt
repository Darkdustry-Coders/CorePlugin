package mindurka.coreplugin

import arc.func.Cons
import mindurka.api.Consts
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.TerminalBuilder

fun setupTerminalInput() {
    // Stolen from https://github.com/Darkdustry-Coders/DarkdustryPlugin/blob/master/src/main/java/darkdustry/features/Console.java
    class BlockingPrintStream(val cons: Cons<String>): PrintStream(ByteArrayOutputStream()) {
        var last = -1;

        override fun write(sign: Int) {
            if (last == '\r'.code && sign == '\n'.code) {
                last = -1;
                return;
            }

            last = sign;

            if (sign == '\n'.code || sign == '\r'.code) flush();
            else super.write(sign);
        }

        override fun write(array: ByteArray, off: Int, len: Int) {
            for (i in 0..<len)
                write(array[off + i].toInt());
        }

        override fun flush() {
            cons.get(out.toString());
            (out as ByteArrayOutputStream).reset();
        }
    }

    val terminal = TerminalBuilder.builder().jna(true).system(true).dumb(true).build()
    val reader = LineReaderBuilder.builder().terminal(terminal).build()

    terminal.enterRawMode()

    System.setOut(BlockingPrintStream(reader::printAbove))

    Consts.serverControl.serverInput = Runnable {
        while (true) {
            val line = reader.readLine("> ").trim()
            if (!line.isEmpty() && !line.trimStart().startsWith('#')) Consts.serverControl.handleCommandString(line)
        }
    }
}
