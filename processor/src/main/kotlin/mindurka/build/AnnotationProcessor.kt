package mindurka.build

import mindurka.annotations.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.KspExperimental
import kotlin.reflect.KClass
import kotlin.math.max
import kotlin.math.min
import java.io.PrintWriter
import java.util.UUID
import java.util.Scanner
import java.io.File

interface Note

data class AddAnnotationNote(val annotation: String)

class Indented(val stream: PrintWriter) {
    private var level = 0
    private var newLine = true

    fun enter() { level++ }
    fun leave() { if (--level < 0) throw IllegalStateException("Invalid indentation") }
    fun indent() = "    ".repeat(level)

    fun print(s: String) {
        stream.print("${if (newLine) indent() else ""}${s.replace(Regex("\n"), "\n${indent()}")}")
        newLine = false
    }
    fun println(s: String) {
        stream.println("${if (newLine) indent() else ""}${s.replace(Regex("\n"), "\n${indent()}")}")
        newLine = true
    }
    fun println() {
        stream.println()
        newLine = true
    }

    fun flush() = stream.flush()
    fun close() = stream.close()
}

fun goodError(sym: KSNode, error: String, notes: Array<String>): String {
    val builder = StringBuilder()
    builder.appendLine(error)
    val location = sym.location
    if (location is FileLocation) {
        builder.appendLine()
        val scanner = Scanner(File(location.filePath))
        var linesRemaining = max(location.lineNumber - 3, 0)
        builder.appendLine("${location.filePath}:${location.lineNumber}:")
        while (scanner.hasNextLine() && --linesRemaining > 0) {
            scanner.nextLine()
        }
        linesRemaining = max(min(location.lineNumber, 3), 0)
        while (scanner.hasNextLine() && --linesRemaining > 0) {
            builder.appendLine("    ${scanner.nextLine()}")
        }
        val funny = scanner.nextLine()
        builder.appendLine("    $funny")
        builder.appendLine("^^^ somewhere here idk kotlin doesn't tell me ^^^")
        linesRemaining = 3
        while (scanner.hasNextLine() && --linesRemaining > 0) {
            builder.appendLine("    ${scanner.nextLine()}")
        }
    }
    return builder.toString()
}

private val ANNOTATION_TYPES: MutableSet<String> = mutableSetOf(DatabaseEntry::class.java.canonicalName, Maybe::class.java.canonicalName) 
private val PARAM_TYPES: Map<String, Int> = mapOf(
    // TODO: Special handling for primitive types.
    "int" to 2, "long" to 2, "short" to 2, "boolean" to 2, "float" to 2, "double" to 2,

    // TODO: Java wrapper types.
    "kotlin.Int" to 2, "kotlin.Long" to 2, "kotlin.Short" to 2, "kotlin.Boolean" to 2, "kotlin.Byte" to 2,
    "kotlin.UInt" to 3, "kotlin.ULong" to 3, "kotlin.UShort" to 3, "kotlin.UByte" to 3,
    "kotlin.Float" to 2, "kotlin.Double" to 2,

    "mindurka.api.MapHandle" to 1, "mindustry.game.Team" to 1, "mindustry.gen.Player" to 1, "mindustry.gen.Unit" to 1,
    "mindustry.type.UnitType" to 1, "mindurka.api.OfflinePlayer" to 1, "kotlin.time.Duration" to 1,

    "kotlin.String" to 0, "java.lang.String" to 0,
)
private val LIST_TYPES_PRIORITY: Int = 4
private val LIST_TYPES: Array<String> = arrayOf(
    "arc.struct.Seq",
)
private val REMAP_TYPES: Map<String, String> = mapOf(
    "kotlin.String" to "java.lang.String",
    "kotlin.UInt" to "int",
    "kotlin.UShort" to "short",
    "kotlin.ULong" to "long",
    "kotlin.UByte" to "byte",
    "kotlin.Int" to "int",
    "kotlin.Short" to "short",
    "kotlin.Long" to "long",
    "kotlin.Byte" to "byte",
    "kotlin.Boolean" to "boolean",
    "kotlin.Float" to "float",
    "kotlin.Double" to "double",
)
private val REMAP_TYPES_NULLABLE: Map<String, String> = mapOf(
    "kotlin.String" to "java.lang.String",
    "kotlin.Int" to "java.lang.Integer",
    "kotlin.Short" to "java.lang.Short",
    "kotlin.Long" to "java.lang.Long",
    "kotlin.Byte" to "java.lang.Byte",
    "kotlin.Boolean" to "java.lang.Boolean",
    "kotlin.Float" to "java.lang.Float",
    "kotlin.Double" to "java.lang.Double",
)

private data class ArgMeta (
    val type: String,
    val name: String,
    val subtype: String?,

    val originalType: String,

    val spread: Boolean,
    val nullable: Boolean,
    val consoleCommand: Boolean,
) {
    val list get() = subtype != null
    val resolvedType get() = subtype ?: type

    fun datatype(replace: String? = null): String = "${replace ?: originalType}${if (list) "<$subtype>" else ""}${if (nullable) "?" else ""}"
    fun nooptdt(replace: String? = null): String = "${replace ?: originalType}${if (list) "<$subtype>" else ""}"
}

/**
 * Parsers for mapped arg types.
 *
 * Pre-set variables:
 * - args (StringPtr): Arguments string.
 * - result (CommandResult): Parsing result.
 *
 * Upon failure, the parser should exit with `return null`.
 *
 * By the end, a correctly typed variable must exist.
 */
private val ARG_PARSERS = HashMap<String, (Indented, String, ArgMeta) -> Unit>()

private fun missing(name: String) = "CommandResult.Missing(\"${escapeString(name)}\")"
private fun invalid(name: String, message: String) = "CommandResult.Invalid(\"${escapeString(name)}\", \"${
    escapeString(message)}\")"

private fun createParser(stringKind: Boolean,
                         transform: (Indented, String, ArgMeta) -> Unit):
        (Indented, String, ArgMeta) -> Unit {
    return { write, varr, meta ->
        write.println("val $varr: ${meta.datatype()} = run parser@{")
        write.enter()

        if (meta.list) {
            write.println("val list = ${meta.nooptdt()}()")

            write.println("when (mindurka.build.list${if (stringKind) "" else "No"}Strings(args, ${!meta.spread}) { source ->")
            write.enter()

            write.println("list.add(")
            write.enter()

            transform(write, varr, meta)

            write.leave() // list.add
            write.println(")")

            write.leave() // listStrings
            write.println("}) {")
            write.enter()

            if (meta.nullable) {
                write.println("ParserError.String -> return ${invalid(meta.name, "{generic.command.string-termination}")}")
                write.println("ParserError.Eof -> return ${invalid(meta.name, "{generic.command.end-of-input}")}")
                write.println("ParserError.Empty -> return@parser null")
            } else {
                write.println("ParserError.String -> return ${invalid(meta.name, "{generic.command.string-termination}")}")
                write.println("ParserError.Eof -> return ${invalid(meta.name, "{generic.command.end-of-input}")}")
                write.println("ParserError.Empty -> return ${missing(meta.name)}")
            }

            write.leave() // when (..)
            write.println("}")

            if (meta.nullable) write.println("if (list.isEmpty()) null else list")
            else {
                write.println("if (list.isEmpty()) return ${missing(meta.name)}")
                write.println("list")
            }
        } else if (meta.spread) {
            write.println("val source = args.rest() ${
                if (meta.nullable) "?: return@parser null" else "?: return ${missing(meta.name)}"}")
            transform(write, varr, meta)
        } else if (stringKind) {
            write.println("mindurka.build.nextString(args).select({ source ->")
            write.enter()

            transform(write, varr, meta)

            write.leave() // select ..
            write.println("}, { error -> when (error) {")
            write.enter()

            write.println("ParserError.Ok -> unreachable()")

            if (meta.nullable) {
                write.println("else -> return@parser null")
            } else {
                write.println("ParserError.String -> return ${invalid(meta.name, "{generic.command.string-termination}")}")
                write.println("ParserError.Eof -> return ${invalid(meta.name, "{generic.command.end-of-input}")}")
                write.println("ParserError.Empty -> return ${missing(meta.name)}")
            }

            write.leave() // when (it)
            write.println("}})")
        } else {
            write.println("val source = args.takeUntil { it.isWhitespace() } ${
                if (meta.nullable) "?: return@parser null" else "?: return ${missing(meta.name)}"}")
            transform(write, varr, meta)
        }

        write.leave() // run parser@
        write.println("}")
    }
}

private var INIT = false
private fun init() {
    if (INIT) return
    INIT = true

    ARG_PARSERS["java.lang.String"] = createParser(true) { write, _, _ -> write.println("source") }

    for (baseType in arrayOf("int", "long", "short", "float", "double", "byte")) {
        val upper = baseType.replaceFirstChar(Char::uppercase)
        ARG_PARSERS[baseType] = createParser(false) { write, _, meta ->
            when (meta.originalType) {
                baseType,
                "java.lang.$upper${if (baseType == "int") "eger" else ""}",
                "kotlin.$upper" -> write.println("source.to${upper}OrNull() ?: " +
                    if (meta.nullable) "return@parser null" else "return ${invalid(meta.name, "{generic.command.not-a-number}")}")
                "kotlin.U$upper" -> write.println("source.toU${upper}OrNull() ?: " +
                    if (meta.nullable) "return@parser null" else "return ${invalid(meta.name, "{generic.command.not-a-number}")}")
                else -> throw IllegalStateException("Unsupported type.")
            }
        }
        ARG_PARSERS["kotlin.$upper"] = createParser(false) { write, _, meta ->
            write.println("source.to${upper}OrNull() ?: " +
                if (meta.nullable) "return@parser null" else "return ${invalid(meta.name, "{generic.command.not-a-number}")}")
        }
        ARG_PARSERS["java.lang.$upper${if (baseType == "int") "eger" else ""}"] = ARG_PARSERS["kotlin.$upper"]!!
        ARG_PARSERS["kotlin.U$upper"] = createParser(false) { write, _, meta ->
            write.println("source.toU${upper}OrNull() ?: " +
                if (meta.nullable) "return@parser null" else "return ${invalid(meta.name, "{generic.command.not-a-number}")}")
        }
    }

    ARG_PARSERS["mindurka.api.MapHandle"] = createParser(true) { write, _, meta ->
        write.println("source.toUIntOrNull()?.let {")
        write.enter()

        write.println("it.ifCheckedSub(1U) {")
        write.enter()

        write.print("mindurka.api.Gamemode.maps.maps().nth(it) ?: ")
        if (meta.nullable) write.println("return@parser null")
        else write.println("return ${invalid(meta.name, "{generic.command.invalid-map-id}")}")

        write.leave() // .ifCheckedSub
        write.println("} ?: return ${invalid(meta.name, "{generic.command.zero-map-id}")}")

        write.leave() // toUIntOrNull()?.let
        write.print("} ?: mindurka.api.Gamemode.maps.maps().findOrNull { it.name().startsWith(source) } ?: ")
        if (meta.nullable) write.println("return@parser null")
        else write.println("return ${invalid(meta.name, "{genetic.command.unknown-map}")}")
    }

    ARG_PARSERS["mindustry.gen.Player"] = createParser(true) { write, _, meta ->
        write.println("findPlayer(source, ${meta.consoleCommand}) ?: " +
            if (meta.nullable) "return@parser null" else "return ${invalid(meta.name, "{generic.command.unknown-player}")}")
    }

    ARG_PARSERS["mindurka.api.OfflinePlayer"] = createParser(true) { write, _, meta ->
        write.println("mindurka.api.OfflinePlayer.resolve(source, ${meta.consoleCommand}) ?: " +
            if (meta.nullable) "return@parser null" else "return ${invalid(meta.name, "{generic.command.unknown-player}")}")
    }

    ARG_PARSERS["kotlin.time.Duration"] = createParser(true) { write, _, meta ->
        write.println("kotlin.time.Duration.parseOrNull(source) ?: " +
            if (meta.nullable) "return@parser null" else "return ${invalid(meta.name, "{generic.command.invalid-duration}")}")
    }
}

private fun obtainClass(classname: String): String =
    if (classname in arrayOf("int", "long", "short", "boolean", "byte", "float", "double"))
        "${classname.take(1).uppercase() + classname.substring(1)}::class.javaPrimitiveType"
    else "Class.forName(\"${escapeString(classname)}\")"
private fun escapeString(str: String): String = str.replace(Regex("[\"$\n\t\r]")) { "\\$it" }

private fun isNullableAnnotation(qualifiedName: String): Boolean =
    qualifiedName == Maybe::class.java.canonicalName!! ||
    qualifiedName.split(".").last() == "Nullable"

@KspExperimental
class AnnotationProcessor(private val environment: SymbolProcessorEnvironment): SymbolProcessor {
    private val commandsClassList = ArrayList<String>()
    private val commandsDeps = HashSet<KSFile>()

    // TODO: Make this shit work in Java
    override fun process(resolver: Resolver): List<KSAnnotated> {
        for (sym in resolver.getSymbolsWithAnnotation(DatabaseEntry::class.java.canonicalName)) {
            if (!sym.validate()) {
                environment.logger.error("Invalid symbol", sym)
                continue
            }
            if (sym !is KSClassDeclaration) {
                environment.logger.error("'@DatabaseEntry' can only be applied to classes", sym)
                continue
            }
        }

        for (sym in resolver.getSymbolsWithAnnotation(NetworkEvent::class.java.canonicalName))
            if (!sym.annotations.any { it.annotationType.resolve().declaration.qualifiedName!!.asString() == "kotlinx.serialization.Serializable" })
                environment.logger.error(goodError(
                    sym,
                    "'@NetworkEvent' annotation can only be applied to types annotated with '@Serializable'",
                    arrayOf()
                ), sym)

        for (sym in resolver.getSymbolsWithAnnotation(Maybe::class.java.canonicalName))
            if (sym.containingFile!!.filePath.endsWith(".kt"))
                environment.logger.error(goodError(
                    sym,
                    "'@Maybe' annotation cannot be used in Kotlin. Use nullable types (`Type?`) instead",
                    arrayOf(),
                ), sym)
        for (sym in resolver.getSymbolsWithAnnotation(ListOf::class.java.canonicalName))
            if (sym.containingFile!!.filePath.endsWith(".kt"))
                environment.logger.error(goodError(
                    sym,
                    "'@ListOf' annotation cannot be used in Kotlin. Use type parameters (`Type<Params>`) instead",
                    arrayOf(),
                ), sym)
        for (sym in resolver.getSymbolsWithAnnotation(Command::class.java.canonicalName)) {
            if (sym.annotations.any { it.annotationType.resolve().declaration.qualifiedName!!.asString() == ConsoleCommand::class.java.canonicalName }) {
                environment.logger.error(goodError(
                    sym,
                    "Command cannot be both tagged @Command and @CommandName at the same time!",
                    arrayOf(),
                ), sym)
            }
        }

        data class FunctionAnnotationDecl(
            val annotation: KClass<*>,
            val type: CommandType,
            val firstParam: String?,
            val tag: Char,
            val consoleCommand: Boolean,
        )
        for (f in arrayOf(
            FunctionAnnotationDecl(annotation = Command::class, type = CommandType.Player, firstParam = "mindustry.gen.Player", tag = 'P', false),
            FunctionAnnotationDecl(annotation = ConsoleCommand::class, type = CommandType.Console, firstParam = null, tag = 'C', true),
        )) {
            syms@for (sym in resolver.getSymbolsWithAnnotation(f.annotation.java.canonicalName)) {
                val isJava = sym.containingFile?.filePath?.endsWith(".java") == true;

                if (!sym.validate()) {
                    environment.logger.error(goodError(sym, "Invalid symbol", arrayOf()), sym)
                    continue
                }

                if (sym !is KSFunctionDeclaration) {
                    environment.logger.error(goodError(
                        sym,
                        "'@${f.annotation.simpleName}' can only be applied to static functions",
                        arrayOf(),
                    ), sym)
                    continue
                }
                if (sym.functionKind != FunctionKind.TOP_LEVEL && sym.functionKind != FunctionKind.STATIC
                    && sym.functionKind != FunctionKind.MEMBER) {
                    environment.logger.error(goodError(
                        sym,
                        "'@${f.annotation.simpleName}' can only be applied to static functions",
                        arrayOf(),
                    ), sym)
                    continue
                }

                val parent = sym.parent
                if (!(
                    parent is KSFile ||
                    parent is KSClassDeclaration && parent.classKind == ClassKind.OBJECT && (
                        parent.parent is KSFile ||
                        parent.isCompanionObject ||
                        parent.parent is KSPropertyDeclaration && parent.parent!!.parent is KSFile
                    ) || isJava
                )) {
                    environment.logger.error(goodError(
                        sym,
                        "'@${f.annotation.simpleName}' can only be applied to static functions",
                        arrayOf(),
                    ), sym)
                    continue
                }
                val implClassName =
                    if (parent is KSFile) "${parent.packageName.asString()}.${parent.fileName.replace(Regex("\\.kt$"), "Kt")}"
                    else if (parent is KSClassDeclaration) parent.qualifiedName!!.asString()
                    else TODO()
                val implMethodName = resolver.getJvmName(sym)!!

                val commandClassName = "CommandClass_${UUID.randomUUID().toString().replace("-", "")}"
                val commandName: Array<String> = run {
                    val annotation = sym.annotations.first { it.annotationType.resolve().declaration.qualifiedName!!.asString() == f.annotation.java.canonicalName }

                    val name = annotation.arguments.find { it.name!!.asString() == "value" }!!.value as String
                    if (name == "<infer>") arrayOf(sym.simpleName.getShortName())
                    else name.split(" ").toTypedArray()
                }

                val commandSetUsage: String? = sym.annotations
                    .first { it.annotationType.resolve().declaration.qualifiedName!!.asString() == f.annotation.java.canonicalName }
                    .arguments
                    .find { it.name!!.asString() == "usage" }?.value as String?

                val classFile = Indented(PrintWriter(environment.codeGenerator.createNewFile(
                    dependencies = Dependencies(aggregating = true, sources = arrayOf(sym.containingFile!!)),
                    packageName  = "_gen.mindurka.commands",
                    fileName     = commandClassName,
                )))

                val cooldown = sym.annotations
                    .find { it.annotationType.resolve().declaration.qualifiedName!!.asString() == Cooldown::class.java.canonicalName }
                    ?.let { it.arguments[0].value as Float } ?: 0f
                val permissionLevel = sym.annotations
                    .find { it.annotationType.resolve().declaration.qualifiedName!!.asString() == RequiresPermission::class.java.canonicalName }
                    ?.let { it.arguments[0].value as Int } ?: 0

                val usage = if (f.consoleCommand && commandSetUsage == null) StringBuilder() else null

                classFile.println("package _gen.mindurka.commands")
                classFile.println("import mindurka.build.StringPtr")
                classFile.println("import mindurka.build.CommandResult")
                classFile.println("import mindurka.build.ParserError")
                classFile.println("import kotlin.reflect.*")
                classFile.println("import kotlin.reflect.jvm.*")
                classFile.println("import kotlin.reflect.full.*")
                classFile.println("import mindurka.util.*")
                classFile.println("import arc.struct.*")

                classFile.println("class $commandClassName: mindurka.build.CommandImpl() {")
                classFile.enter()

                classFile.println("override val doc: String = ${if (sym.docString == null) "\"I'm a goofy goober error\"" else "\"${escapeString(sym.docString!!)}\""}")

                classFile.print("override val command: Array<String> = arrayOf(")
                for (part in commandName) {
                    classFile.print("\"${escapeString(part)}\",")
                }
                classFile.println(")")

                classFile.println("override val type: mindurka.build.CommandType = mindurka.build.CommandType.${f.type.name}")
                classFile.println("override val hidden: Boolean = ${sym.annotations.any { it.annotationType.resolve().declaration.qualifiedName!!.asString() == Hidden::class.java.canonicalName }}")
                classFile.println("override val cooldown = ${cooldown}f")
                classFile.println("override val minPermissionLevel = $permissionLevel")
                classFile.print("override val priority: Array<Int> = arrayOf(")
                val paramTypes = ArrayList<ArgMeta>()
                var paramIdx = -1
                for (param in sym.parameters) {
                    paramIdx++
                    val rest = param.annotations.any { it.annotationType.resolve().declaration.qualifiedName!!.asString() == Rest::class.java.canonicalName }
                    if (rest
                        && (paramIdx + 1 != sym.parameters.size
                        || (paramIdx == 0 && f.firstParam != null))) {
                            environment.logger.error(goodError(
                                sym,
                                "Using '@Rest' on a non-last argument.",
                                arrayOf(),
                            ), sym)
                            continue
                        }

                    val ty = param.type.resolve()
                    val nullable = if (isJava) ty.annotations.any { isNullableAnnotation(it.annotationType.resolve().declaration.qualifiedName!!.asString()) } else ty.nullability == Nullability.NULLABLE

                    usage?.let { usage ->
                        if (!usage.isEmpty()) usage.append(" ")
                        usage.append(if (nullable) "[" else "<")
                            .append(param.name?.asString() ?: "_")
                            .append(if (nullable) "]" else ">")
                    }

                    val strParam = ty.declaration.qualifiedName!!.asString()
                    val remapped = (if (nullable) REMAP_TYPES_NULLABLE[strParam] else null) ?: REMAP_TYPES[strParam] ?: strParam
                    val prio = PARAM_TYPES[strParam]
                    if (prio != null) {
                        classFile.print("$prio,")
                        paramTypes.add(ArgMeta(remapped, param.name?.asString() ?: "_", null, strParam, rest, nullable, f.type == CommandType.Console))
                        continue
                    }
                    if (strParam in LIST_TYPES) {
                        val strParam2 = ty.arguments.first().type!!.resolve().declaration.qualifiedName!!.asString()
                        val remappedArg = REMAP_TYPES_NULLABLE[strParam2] ?: strParam2
                        val prio = PARAM_TYPES[strParam2]
                        if (prio != null) {
                            paramTypes.add(ArgMeta(remapped, param.name?.asString() ?: "_", remappedArg, strParam, rest, nullable, f.type == CommandType.Console))
                            classFile.print("${prio + LIST_TYPES_PRIORITY},")
                            continue
                        }
                        environment.logger.error(goodError(
                            sym,
                            "Type '${strParam2}' (list param) is not a valid command parameter.",
                            arrayOf(),
                        ), sym)
                        continue
                    }
                    environment.logger.error(goodError(
                        sym,
                        "Type '${strParam}' is not a valid command parameter.",
                        arrayOf(),
                    ), sym)
                    continue
                }
                classFile.println(")")

                classFile.println("override val usage: String = \"${escapeString(commandSetUsage ?: usage?.toString() ?: "[args...]")}\"")

                classFile.println("private val method: KFunction<*>")
                classFile.println("init {")
                classFile.enter()

                classFile.println("val klass = Class.forName(\"${escapeString(implClassName)}\")")
                classFile.println("method = klass.getDeclaredMethod(\"${escapeString(implMethodName)}\",")
                classFile.enter()

                for (param in paramTypes) classFile.println("${obtainClass(param.type)},")

                classFile.leave()
                classFile.println(").kotlinFunction!!")
                classFile.println("method.isAccessible = true")

                classFile.leave()
                classFile.println("}") // init

                classFile.println("override suspend fun parse(caller: Any?, raw: String): CommandResult {")
                classFile.enter()

                classFile.println("val args = StringPtr(raw)")
                if (paramTypes.any { it.nullable }) classFile.println("var tempArgsPtr = 0")

                for (i in (if (f.firstParam == null) 0 else 1)..<paramTypes.size) {
                    val name = "arg$i"
                    val meta = paramTypes[i]
                    val parser = ARG_PARSERS[meta.resolvedType]
                    if (parser == null) {
                        environment.logger.error(goodError(
                            sym,
                            "TODO! Parsing for ${meta.resolvedType} is not yet supported!",
                            arrayOf(),
                        ), sym)
                        continue@syms
                    }
                    classFile.println()
                    if (meta.nullable) classFile.println("tempArgsPtr = args.index\nargs.takeUntil { !it.isWhitespace() }")
                    parser(classFile, name, meta)
                    if (meta.nullable) classFile.println("if ($name == null) args.index = tempArgsPtr\nelse args.takeUntil { !it.isWhitespace() }")
                    classFile.println("args.trimStart()")
                }
                classFile.println()
                classFile.println("if (!args.isEmpty()) return CommandResult.TooMuchData")
                // val awaits = sym.annotations.any { it.annotationType.resolve().declaration.qualifiedName!!.asString() == Awaits::class.java.canonicalName }
                // if (awaits) classFile.println("Async.run {")

                if (f.firstParam != null) classFile.println("val firstParam = caller as ${f.firstParam}")

                if (f.type == CommandType.Player) {
                    if (permissionLevel != 0) {
                        classFile.println("if (firstParam.permissionLevel < $permissionLevel) {")
                        classFile.enter()

                        classFile.println("buj.tl.Tl.send(firstParam).done(\"{generic.checks.permission}\")")
                        classFile.println("return CommandResult.Complete")

                        classFile.leave()
                        classFile.println("}")
                    }
                    if (cooldown != 0f) {
                        classFile.println("if (firstParam.checkOnCooldown(\"$commandClassName\")) {")
                        classFile.enter()

                        classFile.println("buj.tl.Tl.send(firstParam).done(\"{generic.checks.cooldown}\")")
                        classFile.println("return CommandResult.Complete")

                        classFile.leave()
                        classFile.println("}")
                        classFile.println("firstParam.setCooldown(\"$commandClassName\", ${cooldown}f)")
                    }
                }

                classFile.print("method.call(")
                if (f.firstParam != null) classFile.print("firstParam,")
                for (i in (if (f.firstParam == null) 0 else 1)..<paramTypes.size) {
                    // val meta = paramTypes[i]
                    classFile.print("arg$i,")
                }
                classFile.println(")")
                // if (awaits) classFile.print("}")

                classFile.println("return CommandResult.Complete")

                classFile.leave()
                classFile.println("}") // suspend fun parse(caller: Any?, raw: String): CommandResult

                classFile.leave()
                classFile.println("}") // class commandName

                classFile.flush()
                classFile.close()

                commandsClassList.add("_gen.mindurka.commands.$commandClassName")
                commandsDeps.add(sym.containingFile!!)
            }
        }

        return emptyList()
    }

    override fun finish() {
        val serviceFile = PrintWriter(environment.codeGenerator.createNewFile(
            dependencies  = Dependencies(true, sources = commandsDeps.toTypedArray()),
            packageName   = "",
            fileName      = "META-INF/mindurka.coreplugin.commands",
            extensionName = ""
        ))
        for (service in commandsClassList) serviceFile.println(service)
        serviceFile.flush()
        serviceFile.close()
    }
}

@KspExperimental
class AnnotationProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        init()
        return AnnotationProcessor(environment)
    }
}
