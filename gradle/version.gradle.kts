// Project version from Git tags (set by hand, see README "Releasing").
//  - HEAD exactly on tag vX.Y.Z, clean tree       -> X.Y.Z
//  - HEAD exactly on tag vX.Y.Z, uncommitted work -> X.Y.(Z+1)-SNAPSHOT  (never overwrite a release)
//  - commits after vX.Y.Z                         -> X.Y.(Z+1)-SNAPSHOT
//  - no tag yet                                   -> 0.1.0-SNAPSHOT
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

// Any output from `git status --porcelain` (modified, staged or untracked files) means "dirty".
val dirty = git("status", "--porcelain")?.isNotEmpty() ?: true
val exact = git("describe", "--tags", "--exact-match", "--match", "v[0-9]*")
val latest = git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")

fun nextSnapshot(tag: String): String {
    val (major, minor, patch) = tag.removePrefix("v").split(".").map { it.toInt() }
    return "$major.$minor.${patch + 1}-SNAPSHOT"
}

version = when {
    exact != null && !dirty -> exact.removePrefix("v")
    latest != null -> nextSnapshot(latest)
    else -> "0.1.0-SNAPSHOT"
}
group = "io.github.azneotec.ticketing"
