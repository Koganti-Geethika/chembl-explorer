FROM eclipse-temurin:17

WORKDIR /app

COPY . .

RUN chmod +x mvnw || true

RUN ./mvnw clean package -DskipTests || mvn clean package -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/chembl-explorer-0.0.1-SNAPSHOT.jar"]