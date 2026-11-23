FROM eclipse-temurin:17-jre-noble

WORKDIR /app
COPY app.jar app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]