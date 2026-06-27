# Use Maven build stage with Java 21
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy Maven wrapper and pom first for dependency download caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Copy source and build
COPY src ./src
RUN chmod +x mvnw && ./mvnw -B package -DskipTests

# Runtime image
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

COPY --from=build /workspace/target/JobScheduler-0.0.1-SNAPSHOT.jar ./app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
