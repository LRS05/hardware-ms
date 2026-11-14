# First stage: compiles payment-service and generates its .jar using the parent pom.xml.
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./pom.xml
COPY payment-service/pom.xml ./payment-service/pom.xml
COPY payment-service/src ./payment-service/src

RUN mvn -B -f payment-service/pom.xml clean package -DskipTests

# Second stage: creates an image to run the previously generated .jar.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build --chown=app:app /app/payment-service/target/*.jar app.jar

USER app

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
