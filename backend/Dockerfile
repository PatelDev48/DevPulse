FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/devpulse-backend-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]