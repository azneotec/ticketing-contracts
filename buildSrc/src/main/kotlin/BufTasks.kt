import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/** Base for tasks that run the `buf` CLI in the repo root (where buf.yaml lives). */
abstract class BufTask @Inject constructor(private val exec: ExecOperations) : DefaultTask() {

    /** Repo root: holds buf.yaml and .git. */
    @get:Internal abstract val repoDir: DirectoryProperty

    init {
        group = "verification"
        // These tasks declare no outputs, so Gradle runs them on every build (they are fast).
    }

    protected fun buf(vararg args: String) {
        val result =
            try {
                exec.exec {
                    commandLine("buf", *args)
                    workingDir = repoDir.get().asFile
                    isIgnoreExitValue = true
                }
            } catch (e: Exception) {
                throw GradleException(
                    "Could not run 'buf'. Install it with: brew install bufbuild/buf/buf",
                    e,
                )
            }
        if (result.exitValue != 0) {
            throw GradleException("buf ${args.first()} failed (see the messages above).")
        }
    }

    protected fun execOps() = exec
}

/** `buf lint`: naming and style rules (STANDARD set in buf.yaml). */
abstract class BufLint @Inject constructor(exec: ExecOperations) : BufTask(exec) {
    init {
        description = "Checks .proto files against Buf's STANDARD lint rules."
    }

    @TaskAction fun lint() = buf("lint")
}

/** `buf breaking`: fails if proto/ changed incompatibly since the last release tag. */
abstract class BufBreaking @Inject constructor(exec: ExecOperations) : BufTask(exec) {

    @get:Input @get:Optional abstract val baselineTagOverride: Property<String>

    init {
        description = "Checks .proto files for breaking changes against the last release tag."
    }

    @TaskAction
    fun breaking() {
        val repo = repoDir.get().asFile
        val tag = execOps().baselineTag(repo, baselineTagOverride.orNull)
        if (tag == null) {
            logger.lifecycle("bufBreaking: no release tag yet, nothing to compare against (first release).")
            return
        }
        logger.lifecycle("bufBreaking: comparing proto/ with tag $tag")
        buf("breaking", "--against", ".git#tag=$tag")
    }
}
