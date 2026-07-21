plugins {
    alias(libs.plugins.kotlin.jvm)
    // This module IS the app: RelayMain is the entrypoint (`./gradlew :relay:run`,
    // or the `installDist` scripts the Docker image runs).
    application
}

dependencies {
    // The relay is Quartz's protocol engine (RelayServerBase) over a vespa-eventstore store.
    api(libs.quartz)
    api(libs.vespa.eventstore.store)
    implementation(libs.kotlinx.coroutines)
    // The Ktor server: serveRelay binds a port over the Netty engine.
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.netty)
    testImplementation(kotlin("test"))
    // RelayProtocolTest drives the real protocol over InMemoryEventIndex, which is
    // production code in the store's transitively-exposed :vespa engine jar — no
    // test-fixtures dependency needed. RelayInfoTest parses the NIP-11 doc.
    testImplementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.vitorpamplona.quartz.eventstore.relay.RelayMainKt"
    applicationName = "vespa-relay"
}

tasks.test {
    useJUnitPlatform()
}
