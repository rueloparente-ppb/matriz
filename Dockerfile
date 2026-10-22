FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/jira-clone-*.jar extract --layers --destination target/extracted

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 app
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "jira-clone-0.0.1-SNAPSHOT.jar"]
