FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S stoxsim && adduser -S stoxsim -G stoxsim
COPY --from=build /app/target/*.jar app.jar
USER stoxsim
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
