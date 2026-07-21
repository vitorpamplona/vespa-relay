# Build the runnable relay distribution, then run it on a slim JRE.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon :relay:installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/relay/build/install/vespa-relay /app
# The NIP-50 websocket + NIP-11 + web UI (RELAY_PORT, default 7777).
EXPOSE 7777
ENTRYPOINT ["/app/bin/vespa-relay"]
