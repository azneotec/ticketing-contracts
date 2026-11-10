plugins {
    `kotlin-dsl`
}

dependencies {
    // Avro's own compiler (code generation) and SchemaValidator (compatibility checks).
    implementation("org.apache.avro:avro-compiler:${libs.versions.avro.get()}")
}
