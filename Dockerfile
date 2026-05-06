# syntax=docker/dockerfile:1
#############################################################
# 🚀 ETAPA ÚNICA: Runtime (minimal, seguro, para Google Cloud)
#############################################################

# PASO 1: Usar una imagen base de Java 17 estándar y segura
FROM eclipse-temurin:17-jre-jammy

# PASO 2: Establecer el directorio de trabajo
WORKDIR /app

# PASO 3: Copiar el archivo .jar que YA compilaste en tu máquina local.
COPY target/*.jar app.jar

# PASO 4: Correr como un usuario no-root por seguridad.
RUN addgroup --system appgroup && adduser --system --group appuser
USER appuser

# PASO 5: Opciones de memoria (ajusta si es necesario).
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# PASO 6: Expone el puerto que tu aplicación necesita. ¡Verifica esto!
# Si tu support-app corre en el 8081, cambia el número aquí.
EXPOSE 8081

# PASO 7: El comando para iniciar la aplicación.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]