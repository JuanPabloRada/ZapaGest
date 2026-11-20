# Imagen para Render: compila con Maven y corre solo el .jar con un JRE liviano.
# Las claves (DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET, DUENO_PASSWORD, FRONTEND_ORIGIN)
# se configuran como variables de entorno en Render, NUNCA dentro de esta imagen.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Las pruebas se corren en local y en el PR; aqui solo se empaqueta
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# Usuario sin privilegios para correr la aplicacion
RUN useradd --system --uid 1001 zapagest
COPY --from=build /app/target/proyectoZapGest-0.0.1-SNAPSHOT.jar app.jar
USER zapagest
# Limita la memoria a lo que da el plan gratis de Render (512 MB)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
