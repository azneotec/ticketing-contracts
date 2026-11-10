import java.io.File
import javax.inject.Inject
import org.apache.avro.Schema
import org.apache.avro.SchemaValidationException
import org.apache.avro.SchemaValidatorBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * The Avro equivalent of `buf breaking`, plus a light lint.
 *
 * Compatibility: each `.avsc` must be BACKWARD compatible with the same file in the last release
 * tag, i.e. a consumer using the new schema (reader) can read events written with the old one
 * (writer). Published schemas must not be deleted.
 *
 * Lint: namespace `io.github.azneotec.ticketing.<context>.v<N>` matching the path
 * `avro/<context>/v<N>/<Name>.avsc`; a `doc` on every record, enum and field; a default on every
 * enum and on every field added since the last release.
 */
abstract class CheckAvroCompatibility @Inject constructor(private val exec: ExecOperations) :
    DefaultTask() {

    /** Repo root (holds .git); schema paths are reported relative to it. */
    @get:Internal abstract val repoDir: DirectoryProperty

    @get:Internal abstract val schemaDir: DirectoryProperty

    @get:Input @get:Optional abstract val baselineTagOverride: Property<String>

    init {
        group = "verification"
        description = "Lints Avro schemas and checks BACKWARD compatibility with the last release."
    }

    @TaskAction
    fun check() {
        val repo = repoDir.get().asFile
        val dir = schemaDir.get().asFile
        val problems = mutableListOf<String>()

        val current: Map<String, File> =
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "avsc" }
                .associateBy { it.relativeTo(repo).invariantSeparatorsPath }
                .toSortedMap()

        val schemas = current.mapValues { (path, file) -> parse(path, file.readText(), problems) }
        schemas.forEach { (path, schema) -> if (schema != null) lint(path, schema, problems) }

        val tag = exec.baselineTag(repo, baselineTagOverride.orNull)
        if (tag == null) {
            logger.lifecycle("checkAvroCompatibility: no release tag yet, nothing to compare against (first release).")
        } else {
            logger.lifecycle("checkAvroCompatibility: comparing ${dir.relativeTo(repo)}/ with tag $tag")
            val dirInRepo = dir.relativeTo(repo).invariantSeparatorsPath
            val released =
                exec.git(repo, "ls-tree", "-r", "--name-only", tag, "--", dirInRepo)
                    .orEmpty().lines().filter { it.endsWith(".avsc") }
            released.filterNot { it in current }.forEach {
                problems += "$it: published schema was deleted. Consumers may still read these events; deprecate it instead."
            }
            schemas.forEach { (path, schema) ->
                if (schema == null) return@forEach
                val oldText = exec.git(repo, "show", "$tag:$path")
                if (oldText == null) {
                    logger.lifecycle("  $path: new schema (not in $tag)")
                    return@forEach
                }
                val old = parse("$path@$tag", oldText, problems) ?: return@forEach
                compare(path, old, schema, problems)
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Avro schema check failed:\n" + problems.joinToString("\n") { "  - $it" }
            )
        }
        logger.lifecycle("checkAvroCompatibility: ${current.size} schema(s) OK")
    }

    private fun parse(path: String, text: String, problems: MutableList<String>): Schema? =
        try {
            Schema.Parser().parse(text)
        } catch (e: Exception) {
            problems += "$path: cannot parse: ${e.message}"
            null
        }

    private fun compare(path: String, old: Schema, new: Schema, problems: MutableList<String>) {
        // canReadStrategy: the NEW schema must be able to read data written with the OLD one,
        // which is Avro's (and the schema registry's) definition of BACKWARD compatibility.
        try {
            SchemaValidatorBuilder().canReadStrategy().validateLatest().validate(new, listOf(old))
        } catch (e: SchemaValidationException) {
            problems += "$path: not BACKWARD compatible with the last release: ${e.message}"
        }
        // Clearer message for the most common mistake: a new field without a default.
        val oldFields = namedTypes(old).filter { it.type == Schema.Type.RECORD }
            .associate { it.fullName to it.fields.map { f -> f.name() }.toSet() }
        namedTypes(new).filter { it.type == Schema.Type.RECORD }.forEach { record ->
            val known = oldFields[record.fullName] ?: return@forEach
            record.fields.filter { it.name() !in known && !it.hasDefaultValue() }.forEach {
                problems += "$path: new field ${record.name}.${it.name()} needs a default " +
                    "(old events do not have it, so new consumers need a value to use)"
            }
        }
    }

    private fun lint(path: String, schema: Schema, problems: MutableList<String>) {
        val match = PATH.matchEntire(path)
        if (match == null) {
            problems += "$path: expected path avro/<context>/v<N>/<Name>.avsc"
        } else {
            val (context, version, name) = match.destructured
            val expectedNamespace = "io.github.azneotec.ticketing.$context.v$version"
            if (schema.namespace != expectedNamespace) {
                problems += "$path: namespace must be $expectedNamespace, was ${schema.namespace}"
            }
            if (schema.name != name) {
                problems += "$path: top-level type must be named $name (the file name), was ${schema.name}"
            }
        }
        namedTypes(schema).forEach { type ->
            if (type.doc.isNullOrBlank()) problems += "$path: ${type.name} has no doc"
            when (type.type) {
                Schema.Type.RECORD -> type.fields.filter { it.doc().isNullOrBlank() }.forEach {
                    problems += "$path: field ${type.name}.${it.name()} has no doc"
                }
                Schema.Type.ENUM -> if (type.enumDefault == null) {
                    problems += "$path: enum ${type.name} needs a default symbol " +
                        "(readers map symbols they do not know to it)"
                }
                else -> {}
            }
        }
    }

    /** All records, enums and fixed types in [schema], each once. */
    private fun namedTypes(schema: Schema): List<Schema> {
        val seen = linkedMapOf<String, Schema>()
        fun visit(s: Schema) {
            when (s.type) {
                Schema.Type.RECORD -> if (seen.putIfAbsent(s.fullName, s) == null) {
                    s.fields.forEach { visit(it.schema()) }
                }
                Schema.Type.ENUM, Schema.Type.FIXED -> seen.putIfAbsent(s.fullName, s)
                Schema.Type.UNION -> s.types.forEach(::visit)
                Schema.Type.ARRAY -> visit(s.elementType)
                Schema.Type.MAP -> visit(s.valueType)
                else -> {}
            }
        }
        visit(schema)
        return seen.values.toList()
    }

    private companion object {
        val PATH = Regex("""avro/([a-z]+)/v(\d+)/(\w+)\.avsc""")
    }
}
