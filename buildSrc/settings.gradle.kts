// buildSrc: build logic used only by this repo (Avro code generation and the Buf / Avro
// compatibility checks). It is not shared, so it does not belong in the convention plugins.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    // Reuse the main build's version catalog, so the Avro version is declared once.
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
