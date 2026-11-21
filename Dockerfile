# Una sola imagen para los tres roles (ADR-01: un artefacto).
# El rol lo decide la variable SPRING_PROFILES_ACTIVE en docker-compose.yml.

# --- Etapa 1: compilar ---
FROM maven:3.9-eclipse-temurin-21 AS compilacion
WORKDIR /fuente
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Etapa 2: ejecutar ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 biohub
COPY --from=compilacion /fuente/target/biohub.jar /app/biohub.jar
USER biohub
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/biohub.jar"]
