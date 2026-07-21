pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "vespa-relay"

// A ready-to-serve Nostr relay over a vespa-eventstore VespaEventStore, with
// trust-ranked NIP-50 search. Quartz's protocol engine (RelayServerBase +
// LiveEventStore) wired to the store, plus a Ktor websocket mount and the NIP-11 doc.
include(":relay")
