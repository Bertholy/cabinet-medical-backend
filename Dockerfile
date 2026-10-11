# Image du serveur CASAFAMI (utilisée par Render, Railway, Fly.io, un VPS avec Docker…).
# Construction : Maven compile le jar ; l'image finale ne contient que Java + le jar.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/cabinet-0.0.1-SNAPSHOT.jar app.jar
# Petite mémoire (offres gratuites : 512 Mo) : ramasse-miettes simple et plafond à 70 % de la RAM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
# Le port est donné par l'hébergeur dans la variable PORT (lue par application.properties).
CMD ["java", "-jar", "app.jar"]
