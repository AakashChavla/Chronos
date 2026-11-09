FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE=api
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE=api
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-0.1.0.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
