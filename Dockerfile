# Fase 1: Build dell'applicazione
FROM  maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src /src
# Compila il progetto e crea il file JAR (escludendo i test per rapidità)
RUN mvn clean package -DskipTests

# Fase 2: Creazione dell'immagine finale
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Copia il file JAR generato dalla fase di build
COPY --from=builder /app/target/*.jar app.jar
# Espone la porta usata di default da Spring Boot
EXPOSE 8080
# Avvia l'applicazione
ENTRYPOINT ["java", "-jar", "app.jar"]