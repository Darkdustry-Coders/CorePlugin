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
import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.KspExperimental
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.math.max
import kotlin.math.min
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import java.io.PrintWriter
import java.util.UUID
import java.util.Scanner
import java.io.File
import java.util.Locale
import java.util.Locale.getDefault

interface Note

data class AddAnnotationNote(val annotation: String)

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
    "mindustry.type.UnitType" to 1,

    "kotlin.String" to 0, "java.lang.String" to 0,
)
private val LIST_TYPES_PRIORITY: Int = 4
private val LIST_TYPES: Array<String> = arrayOf(
    "arc.struct.Seq",
)
private val REMAP_TYPES: Map<String, String> = mapOf(
    "kotlin.String" to "java.lang.String",
)
private val REMAP_TYPES_NULLABLE: Map<String, String> = mapOf(
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

private data class ArgMeta (
    val type: String,
    val subtype: String?,

    val originalType: String,

    val spread: Boolean,
    val nullable: Boolean,
) {
    val list get() = subtype != null
    val resolvedType get() = subtype ?: type

    fun datatype(replace: String? = null): String = "${replace ?: originalType}${if (list) "<$subtype>" else ""}${if (nullable) "?" else ""}"
}

/**
 * Parsers for mapped arg types.
 *
 * Pre-set variables:
 * - args (StringPtr): Arguments string.
 *
 * Upon failure, the parser should exit with `return null`.
 *
 * By the end, a correctly typed variable must exist.
 */
private val ARG_PARSERS = HashMap<String, (PrintWriter, String, ArgMeta) -> Unit>()

private var INIT = false
private fun init() {
    if (INIT) return
    INIT = true

    ARG_PARSERS["java.lang.String"] = { write, varr, meta ->
        write.print("val $varr: ${meta.datatype()} = ")
        if (meta.list) write.print("(")
        if (meta.spread) {
            write.print("args.rest()")
        } else {
            write.print("(if (args.peek() == '\"') args.takeUntil { it == '\"' } else if (args.peek() == '\\'') args.takeUntil { it == '\\'' } else args.takeUntil { it == ' ' })")
        }
        if (!meta.nullable) write.print("?:return null")
        if (meta.list) {
            write.print(")")
            if (meta.nullable) write.print("?")
            write.print(".apply{Seq.with(this.split('${if (meta.spread) ' ' else ','}'))}")
        }
        write.println()
    }

    ARG_PARSERS["int"] = { write, varr, meta ->
        when (meta.originalType) {
            "kotlin.UInt", "kotlin.UShort", "kotlin.UByte", "kotlin.ULong",
            "kotlin.Int", "kotlin.Short", "kotlin.Byte", "kotlin.Long",
            "kotlin.Float", "kotlin.Double" -> {
                val convert = meta.originalType.substring("kotlin.".length)
                if (meta.list) write.println("val $varr: ${meta.datatype()} = run {")
                else write.print("val $varr: ${meta.datatype()} = ")
                if (meta.list) write.print("val s = ")
                if (meta.spread) {
                    write.print("args.rest()")
                } else {
                    write.print("args.takeUntil { it == ' ' }")
                }
                if (!meta.list) write.print("?.to${convert}OrNull()")
                if (!meta.nullable) write.print("?:return null")
                if (meta.list) {
                    write.println()
                    if (meta.nullable) write.print("?")
                    write.println("val col = ${meta.datatype()}()")
                    write.println("for (x in s.split(${if (meta.spread) "Regex(\" *\")" else "Regex(\", *\")"}))")
                    write.println("if (!x.isEmpty())")
                    write.println("col.add(x.to${convert}OrNull()?:return@run null)")
                    write.println("col")
                    write.print("}")
                    if (!meta.nullable) write.print("?:return null")
                }
                write.println()
            }
            else -> write.println("val $varr: ${meta.datatype()} = TODO()")
        }
    }

    for (intType in arrayOf(
        "kotlin.UInt", "kotlin.UShort", "kotlin.UByte", "kotlin.ULong",
        "kotlin.Int", "kotlin.Short", "kotlin.Byte", "kotlin.Long",
        "kotlin.Float", "kotlin.Double",
    )) {
        val convert = intType.substring("kotlin.".length)
        ARG_PARSERS[intType] = { write, varr, meta ->
            if (meta.list) write.println("val $varr: ${meta.datatype()} = run {")
            else write.print("val $varr: ${meta.datatype()} = ")
            if (meta.list) write.print("val s = ")
            if (meta.spread) {
                write.print("args.rest()")
            } else {
                write.print("args.takeUntil { it == ' ' }")
            }
            if (!meta.list) write.print("?.to${convert}OrNull()")
            if (!meta.nullable) write.print("?:return null")
            if (meta.list) {
                write.println()
                if (meta.nullable) write.print("?")
                write.println("val col = ${meta.datatype()}()")
                write.println("for (x in s.split(${if (meta.spread) "Regex(\" *\")" else "Regex(\", *\")"}))")
                write.println("if (!x.isEmpty())")
                write.println("col.add(x.to${convert}OrNull()?:return@run null)")
                write.println("col")
                write.print("}")
                if (!meta.nullable) write.print("?:return null")
            }
            write.println()
        }
    }

    for (intType in arrayOf(
        "int", "long", "short", "float", "double", "byte",
    )) {
        val convert = intType.replaceFirstChar { it.uppercase() }
        ARG_PARSERS[intType] = { write, varr, meta ->
            if (meta.list) write.println("val $varr: ${meta.datatype()} = run {")
            else write.print("val $varr: ${meta.datatype()} = ")
            if (meta.list) write.print("val s = ")
            if (meta.spread) {
                write.print("args.rest()")
            } else {
                write.print("args.takeUntil { it == ' ' }")
            }
            if (!meta.list) write.print("?.to${convert}OrNull()")
            if (!meta.nullable) write.print("?:return null")
            if (meta.list) {
                write.println()
                if (meta.nullable) write.print("?")
                write.println("val col = ${meta.datatype()}()")
                write.println("for (x in s.split(${if (meta.spread) "Regex(\" *\")" else "Regex(\", *\")"}))")
                write.println("if (!x.isEmpty())")
                write.println("col.add(x.to${convert}OrNull()?:return@run null)")
                write.println("col")
                write.print("}")
                if (!meta.nullable) write.print("?:return null")
            }
            write.println()
        }
    }

    for (boolType in arrayOf("kotlin.Boolean", "boolean", "java.lang.Boolean")) {
        ARG_PARSERS[boolType] =  { write, varr, meta ->
            write.println("val $varr: ${meta.datatype()} = run {\nval possibleBool = ")
            if (meta.spread) {
                write.println("args.rest()")
            } else {
                write.println("args.takeUntil { it == ' ' }")
            }
            write.println("when (possibleBool) {")
            write.println("\"true\",\"ye\",\"yes\",\"y\",\"1\",\"on\"->true")
            write.println("\"false\",\"nah\",\"na\",\"n\",\"0\",\"no\",\"off\"->false")
            write.println("else->null")
            write.print("}}")
            if (!meta.nullable) write.print("?:return null")
            write.println("")
        }
    }

    ARG_PARSERS["mindurka.api.MapHandle"] = { write, varr, meta ->
        write.println("val $varr: ${meta.datatype()} = run {\nval s = ")
        if (meta.spread) {
            write.print("args.rest()")
        } else {
            write.print("(if (args.peek() == '\"') args.takeUntil { it == '\"' } else if (args.peek() == '\\'') args.takeUntil { it == '\\'' } else args.takeUntil { it == ' ' })")
        }
        write.println("?:return@run null")
        if (meta.list) {
            write.println("val col = ${meta.datatype()}()")
            write.println("for (x in s.split(Regex(\", +\"))) {")
            write.println("col.add(mindurka.api.Gamemode.maps.maps().findOrNull { it.startsWith(s) } ?: return@run null)")
            write.println("}")
            write.println("col")
        }
        else write.println("mindurka.api.Gamemode.maps.maps().findOrNull { it.startsWith(s) }")
        if (meta.nullable) write.println("}?:return null")
        else write.println("}")
    }
}

private fun obtainClass(classname: String): String =
    if (classname in arrayOf("int", "long", "short", "boolean", "byte", "float", "double"))
        "${classname.take(1).uppercase() + classname.substring(1)}::class.javaPrimitiveType"
    else "Class.forName(\"${escapeString(classname)}\")"
private fun escapeString(str: String): String = str.replace(Regex("[\\\"$\n\t\r]")) { "\\$it" }

private fun isNullableAnnotation(qualifiedName: String): Boolean =
    qualifiedName == mindurka.annotations.Maybe::class.java.canonicalName!! ||
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
        )
        for (f in arrayOf(
            FunctionAnnotationDecl(annotation = Command::class, type = CommandType.Player, firstParam = "mindustry.gen.Player", tag = 'P'),
            FunctionAnnotationDecl(annotation = ConsoleCommand::class, type = CommandType.Console, firstParam = null, tag = 'C'),
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
                val implDoc = sym.docString
                
                val commandClassName = "CommandClass_${UUID.randomUUID().toString().replace("-", "")}"
                val commandName: Array<String> = run {
                    val annotation = sym.annotations.first { it.annotationType.resolve().declaration.qualifiedName!!.asString() == f.annotation.java.canonicalName }
                    val name = annotation.arguments.first().value as String
                    if (name == "<infer>") arrayOf(sym.simpleName.getShortName())
                    else name.split(" ").toTypedArray()
                }

                val classFile = PrintWriter(environment.codeGenerator.createNewFile(
                    dependencies = Dependencies(aggregating = true, sources = arrayOf(sym.containingFile!!)),
                    packageName  = "_gen.mindurka.commands",
                    fileName     = commandClassName,
                ))

                classFile.println("package _gen.mindurka.commands")
                classFile.println("import mindurka.build.StringPtr")
                classFile.println("import kotlin.reflect.*")
                classFile.println("import kotlin.reflect.jvm.*")
                classFile.println("import kotlin.reflect.full.*")
                classFile.println("import mindurka.util.*")
                classFile.println("import arc.struct.*")
                classFile.println("class $commandClassName: mindurka.build.CommandImpl() {")
                classFile.println("override val doc: String = ${if (sym.docString == null) "\"I'm a goofy goober error\"" else "\"${escapeString(sym.docString!!)}\""}")
                classFile.print("override val command: Array<String> = arrayOf(")
                for (part in commandName) {
                    classFile.print("\"${escapeString(part)}\",")
                }
                classFile.println(")")
                classFile.println("override val type: mindurka.build.CommandType = mindurka.build.CommandType.${f.type.name}")
                classFile.println("override val hidden: Boolean = ${sym.annotations.any { it.annotationType.resolve().declaration.qualifiedName!!.asString() == Hidden::class.java.canonicalName }}")
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
                    val strParam = ty.declaration.qualifiedName!!.asString()
                    val remapped = (if (ty.nullability == Nullability.NULLABLE) null else REMAP_TYPES_NULLABLE[strParam]) ?: REMAP_TYPES[strParam] ?: strParam
                    val prio = PARAM_TYPES[strParam]
                    if (prio != null) {
                        classFile.print("$prio,")
                        paramTypes.add(ArgMeta(remapped, null, strParam, rest, ty.nullability == Nullability.NULLABLE))
                        continue
                    }
                    if (strParam in LIST_TYPES) {
                        val strParam2 = ty.arguments.first().type!!.resolve().declaration.qualifiedName!!.asString()
                        val remappedArg = REMAP_TYPES[strParam2] ?: strParam2
                        val prio = PARAM_TYPES[strParam2]
                        if (prio != null) {
                            paramTypes.add(ArgMeta(remapped, remappedArg, strParam, rest, ty.nullability == Nullability.NULLABLE))
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

                classFile.println("private val method: KFunction<*>")
                classFile.println("init {")
                classFile.println("val klass = Class.forName(\"${escapeString(implClassName)}\")")
                classFile.println("method = klass.getDeclaredMethod(\"${escapeString(implMethodName)}\"")
                for (param in paramTypes) classFile.print(",${obtainClass(param.type)}")
                classFile.println(").kotlinFunction!!")
                classFile.println("method.isAccessible = true")
                classFile.println("}")

                classFile.println("override fun parse(caller: Any?, raw: String): (() -> Unit)? {")
                classFile.println("val args = StringPtr(raw)")
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
                    if (meta.nullable) classFile.println("val tempArgsPtr$i = args.index\nargs.takeUntil { it != ' ' }")
                    parser(classFile, name, meta)
                    if (meta.nullable) classFile.println("if ($name == null) args.index = tempArgsPtr$i\nelse args.takeUntil { it != ' ' }")
                    classFile.println("args.trimStart()")
                }
                classFile.println("if (!args.isEmpty()) return null")
                classFile.println("return {")
                val awaits = sym.annotations.any { it.annotationType.resolve().declaration.qualifiedName!!.asString() == Awaits::class.java.canonicalName }
                if (awaits) classFile.println("Async.run {")
                classFile.print("method.call(")
                if (f.firstParam != null) classFile.print("caller as ${f.firstParam},")
                for (i in (if (f.firstParam == null) 0 else 1)..<paramTypes.size) {
                    val meta = paramTypes[i]
                    classFile.print("arg$i,")
                }
                classFile.print(")")
                if (awaits) classFile.print("}")
                classFile.println("}}}")

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
