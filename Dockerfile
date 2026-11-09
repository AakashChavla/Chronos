FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -pl api -am package -DskipTests
FROM eclipse-temurin:21-jre
COPY --from=build /src/api/target/api-0.1.0.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
