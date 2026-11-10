// Root of the repo. The two modules publish separately versioned-together artifacts:
//   io.github.azneotec.ticketing:ticketing-contracts-grpc
//   io.github.azneotec.ticketing:ticketing-contracts-avro
plugins {
    base
    alias(libs.plugins.protobuf) apply false
}

allprojects {
    apply(from = rootProject.file("gradle/version.gradle.kts"))
}
