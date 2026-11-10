import org.apache.avro.Schema
import org.apache.avro.compiler.specific.SpecificCompiler
import org.apache.avro.generic.GenericData
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Generates Java `SpecificRecord` classes from `.avsc` files with Avro's own compiler.
 * (We call avro-compiler directly because the popular Avro Gradle plugin is archived.)
 */
@CacheableTask
abstract class GenerateAvroJava : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaDir: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    init {
        group = "build"
        description = "Generates Java classes from Avro schemas (.avsc)."
    }

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        // One parser for all files, in path order, so a schema can refer to a type defined in
        // an earlier file.
        val parser = Schema.Parser()
        schemaDir.asFileTree.matching { include("**/*.avsc") }.files.sorted().forEach { file ->
            val schema = parser.parse(file)
            SpecificCompiler(schema).apply {
                // Java String instead of Avro's Utf8 for string fields.
                setStringType(GenericData.StringType.String)
                setFieldVisibility(SpecificCompiler.FieldVisibility.PRIVATE)
                setOutputCharacterEncoding("UTF-8")
            }.compileToDestination(file, out)
        }
    }
}
