FROM eclipse-temurin:21-jdk-jammy AS compilacao
WORKDIR /app
COPY lib/ lib/
COPY src/ src/
COPY manifesto.txt compilar.sh ./
RUN sh compilar.sh

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=compilacao /app/fl-barbearia.jar ./
COPY --from=compilacao /app/lib/ lib/
COPY site/ site/
RUN mkdir -p dados && chown 10001:10001 dados
ENV PORT=10000 TZ=America/Sao_Paulo
USER 10001:10001
EXPOSE 10000
CMD ["java", "-Djava.awt.headless=true", "-XX:MaxRAMPercentage=65.0", "-jar", "fl-barbearia.jar"]
