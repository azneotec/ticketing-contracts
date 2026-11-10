// Project version from Git tags (set by hand, see README "Releasing").
//  - HEAD exactly on tag vX.Y.Z  -> X.Y.Z
//  - commits after vX.Y.Z        -> X.Y.(Z+1)-SNAPSHOT
//  - no tag yet                  -> 0.1.0-SNAPSHOT
fun git(vararg args: String): String? =
    try {
        providers.exec {
            commandLine("git", *args)
            workingDir = rootDir
            isIgnoreExitValue = true
        }.let { if (it.result.get().exitValue == 0) it.standardOutput.asText.get().trim() else null }
    } catch (e: Exception) {
        null
    }

val exact = git("describe", "--tags", "--exact-match", "--match", "v[0-9]*")
val latest = git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")

version = when {
    exact != null -> exact.removePrefix("v")
    latest != null -> {
        val (major, minor, patch) = latest.removePrefix("v").split(".").map { it.toInt() }
        "$major.$minor.${patch + 1}-SNAPSHOT"
    }
    else -> "0.1.0-SNAPSHOT"
}
group = "io.github.azneotec.ticketing"
