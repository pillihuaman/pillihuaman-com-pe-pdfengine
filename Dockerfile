# 1. Imagen Base (Soporte para Java 25 según tu pom.xml)
FROM eclipse-temurin:25-jre-jammy

# 2. Directorio de trabajo
WORKDIR /app

# 3. Copia del Artefacto (Asegúrate de haber ejecutado mvn clean package)
COPY target/*.jar app.jar

# 4. Seguridad: Usuario no-root (Zero Trust)
RUN addgroup --system appgroup && adduser --system --group appuser
USER appuser

# 5. Optimización de Memoria (Crítico para procesamiento PDF en Cloud Run)
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
ENTRYPOINT ["sh","-c","java --enable-preview $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]

