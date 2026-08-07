# Etapa 1: Compilar el proyecto con Maven
# Usa una imagen que ya tiene Java 21 + Maven preinstalado.
# "AS build" le pone nombre a esta etapa para referenciarla despues.
FROM maven:3.9-eclipse-temurin-21 AS build

# Carpeta de trabajo dentro del contenedor
WORKDIR /app

# Copiar primero solo el pom.xml para cachear las dependencias.
# Docker cachea cada capa: si el pom.xml no cambio, no vuelve
# a descargar las dependencias (ahorra minutos en cada build).
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Ahora copiar el codigo fuente y compilar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Etapa 2: Imagen final liviana (solo Java, sin Maven)
# eclipse-temurin:21-jre pesa ~200MB vs ~800MB de la imagen con Maven.
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copiar SOLO el .jar compilado de la etapa anterior
COPY --from=build /app/target/*.jar app.jar

# Puerto que expone el contenedor
EXPOSE 8443

# Comando para ejecutar la aplicacion
ENTRYPOINT ["java", "-jar", "app.jar"]