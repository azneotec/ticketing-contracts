// Java classes for the gRPC contracts in ../proto: Protobuf messages + gRPC client/server stubs.
plugins {
    `java-library`
    `maven-publish`
    id("ticketing.java-conventions")
    alias(libs.plugins.protobuf)
}

sourceSets {
    main {
        proto { srcDir(rootProject.file("proto")) }
    }
}

dependencies {
    // api: services that use these classes also need the runtime libraries they extend.
    api(libs.protobuf.java)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    testImplementation(libs.grpc.inprocess)
}

// Artifact coordinates for the protobuf { } block below.
val protocArtifact = libs.protobuf.protoc.get().toString()
val grpcGeneratorArtifact = libs.grpc.generator.get().toString()

protobuf {
    // protoc (the Protobuf compiler) and the gRPC code generator, downloaded as Maven artifacts.
    protoc { artifact = protocArtifact }
    plugins {
        create("grpc") { artifact = grpcGeneratorArtifact }
    }
    generateProtoTasks {
        all().configureEach {
            plugins { create("grpc") }
        }
    }
}

val bufLint = tasks.register<BufLint>("bufLint") {
    repoDir = rootProject.layout.projectDirectory
}
val bufBreaking = tasks.register<BufBreaking>("bufBreaking") {
    repoDir = rootProject.layout.projectDirectory
    baselineTagOverride = providers.gradleProperty("ticketing.compatibilityBaselineTag")
}
tasks.check { dependsOn(bufLint, bufBreaking) }

java { withSourcesJar() }

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "ticketing-contracts-grpc"
            from(components["java"])
        }
    }
}
