pluginManagement {
    val ticketingConventionsVersion = providers.gradleProperty("ticketingConventionsVersion").get()
    repositories {
        mavenLocal() // ticketing.* convention plugins
        gradlePluginPortal()
    }
    plugins {
        id("ticketing.java-conventions") version ticketingConventionsVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ticketing-contracts"

// contracts-grpc: Protobuf messages + gRPC stubs, generated from proto/.
// contracts-avro: Avro SpecificRecord classes for Kafka events, generated from avro/.
include("contracts-grpc", "contracts-avro")
