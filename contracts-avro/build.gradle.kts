// Java classes for the Kafka event schemas in ../avro: Avro SpecificRecord classes.
plugins {
    `java-library`
    `maven-publish`
    id("ticketing.java-conventions")
}

dependencies {
    // api: generated classes extend Avro runtime types (SpecificRecordBase, ...).
    api(libs.avro)
}

val generateAvroJava = tasks.register<GenerateAvroJava>("generateAvroJava") {
    schemaDir = rootProject.layout.projectDirectory.dir("avro")
    outputDir = layout.buildDirectory.dir("generated/sources/avro/java/main")
}
// Passing the task (not just the folder) makes compileJava depend on it.
sourceSets.main { java.srcDir(generateAvroJava) }

val checkAvroCompatibility = tasks.register<CheckAvroCompatibility>("checkAvroCompatibility") {
    repoDir = rootProject.layout.projectDirectory
    schemaDir = rootProject.layout.projectDirectory.dir("avro")
    baselineTagOverride = providers.gradleProperty("ticketing.compatibilityBaselineTag")
}
tasks.check { dependsOn(checkAvroCompatibility) }

// Avro (1.11.4+) only instantiates classes from trusted packages, a defence against
// deserialization attacks. Every service that reads these events must set the same property.
tasks.test {
    systemProperty("org.apache.avro.SERIALIZABLE_PACKAGES", "io.github.azneotec.ticketing")
}

java { withSourcesJar() }

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "ticketing-contracts-avro"
            from(components["java"])
        }
    }
}
