# First stage: compiles order-service and generates its .jar using the parent pom.xml.
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./pom.xml
COPY order-service/pom.xml ./order-service/pom.xml
COPY order-service/src ./order-service/src

RUN mvn -B -f order-service/pom.xml clean package -DskipTests

# Second stage: creates an image to run the previously generated .jar.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build --chown=app:app /app/order-service/target/*.jar app.jar

USER app

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]