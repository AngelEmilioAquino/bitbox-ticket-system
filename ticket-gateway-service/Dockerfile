# Build context: repository root (the module needs the parent pom and the Maven wrapper)
#   docker build -f ticket-gateway-service/Dockerfile .

# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY inventory-service/pom.xml inventory-service/
COPY ticket-gateway-service/pom.xml ticket-gateway-service/
RUN chmod +x mvnw && ./mvnw -B -q -pl ticket-gateway-service -am dependency:go-offline

COPY ticket-gateway-service/src ticket-gateway-service/src
RUN ./mvnw -B -q -pl ticket-gateway-service -am package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/ticket-gateway-service/target/ticket-gateway-service.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
