# First stage: compiles config-server and generates its .jar using the parent pom.xml.
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./pom.xml
COPY config-server/pom.xml ./config-server/pom.xml
COPY config-server/src ./config-server/src

RUN mvn -B -f config-server/pom.xml clean package -DskipTests

# Second stage: creates an image to run the previously generated .jar.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build --chown=app:app /app/config-server/target/*.jar app.jar

USER app

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]
