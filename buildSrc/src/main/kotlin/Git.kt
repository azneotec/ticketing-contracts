import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.process.ExecOperations

/** Runs git in [repoDir] and returns its trimmed stdout, or null when git exits non-zero. */
internal fun ExecOperations.git(repoDir: File, vararg args: String): String? {
    val out = ByteArrayOutputStream()
    val result = exec {
        commandLine("git", *args)
        workingDir = repoDir
        standardOutput = out
        errorOutput = ByteArrayOutputStream()
        isIgnoreExitValue = true
    }
    return if (result.exitValue == 0) out.toString(Charsets.UTF_8).trim() else null
}

/**
 * The release the contracts are compared against: an explicit tag (Gradle property
 * `ticketing.compatibilityBaselineTag`, used by samples/verify-failure-cases.sh), else the latest
 * `vX.Y.Z` tag reachable from HEAD, else null (nothing released yet).
 */
internal fun ExecOperations.baselineTag(repoDir: File, override: String?): String? =
    override?.takeIf { it.isNotBlank() }
        ?: git(repoDir, "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
