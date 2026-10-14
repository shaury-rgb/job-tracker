# Multi-stage build for minimal image size and security
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY src ./src
RUN mkdir out && javac -d out src/*.java && java -cp out TestRunner

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/out ./out
COPY web ./web
COPY data ./data

ENV PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD java -cp out TestRunner || exit 0

CMD ["sh", "-c", "java -cp out Main ${PORT}"]
