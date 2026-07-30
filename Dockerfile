# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# --- Optional corporate proxy CA (local builds behind a TLS-inspecting proxy, e.g. Zscaler) ---
# Grigory / CI build normally → Maven Central is reached directly, works out of the box.
# Behind a proxy, place a `*.crt` (the proxy root CA) in the project root (gitignored) and:
#     podman build --build-arg TRUST_LOCAL_CA=1 -t nevis-api .
# The CA is imported into the build JDK truststore so Maven trusts the proxy.
COPY . /src-context/
ARG TRUST_LOCAL_CA=0
RUN if [ "$TRUST_LOCAL_CA" = "1" ] && ls /src-context/*.crt >/dev/null 2>&1; then \
        for c in /src-context/*.crt; do \
            keytool -importcert -noprompt -trustcacerts -alias "ca-$(basename "$c")" \
                -file "$c" -cacerts -storepass changeit ; \
        done ; \
    fi

RUN cp /src-context/pom.xml . && mvn -q dependency:go-offline
RUN cp -r /src-context/src ./src && mvn -q clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/search-api-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
