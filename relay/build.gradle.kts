plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.mavenPublish)
}

dependencies {
    // The relay is Quartz's protocol engine (RelayServerBase) over a
    // vespa-eventstore store; both appear in the public wiring API.
    api(libs.quartz)
    api(libs.vespa.eventstore.store)
    implementation(libs.kotlinx.coroutines)
    // The websocket mount for the composition root's Ktor app.
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.websockets)
    testImplementation(kotlin("test"))
    // RelayProtocolTest drives the real protocol over InMemoryEventIndex, which is
    // production code in the store's transitively-exposed :vespa engine jar — no
    // test-fixtures dependency needed. RelayInfoTest parses the NIP-11 doc.
    testImplementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    coordinates(
        groupId = "com.vitorpamplona.quartz.eventstore",
        artifactId = "relay",
        version = libs.versions.app.get(),
    )
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    pom {
        name = "Vespa Event Store: relay"
        description = "A ready-to-serve Nostr relay over a vespa-eventstore store: Quartz's protocol engine (NIP-01/42/45/50/77) wired to the trust-ranked store, with a Ktor websocket mount and the NIP-11 doc."
        inceptionYear = "2026"
        url = "https://github.com/vitorpamplona/vespa-relay/"
        licenses {
            license {
                name = "MIT License"
                url = "https://github.com/vitorpamplona/vespa-relay/blob/main/LICENSE"
            }
        }
        developers {
            developer {
                id = "vitorpamplona"
                name = "Vitor Pamplona"
                url = "http://vitorpamplona.com"
                email = "vitor@vitorpamplona.com"
            }
        }
        scm {
            url = "https://github.com/vitorpamplona/vespa-relay/"
            connection = "https://github.com/vitorpamplona/vespa-relay/.git"
        }
    }
}
