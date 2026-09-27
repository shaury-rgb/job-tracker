FROM eclipse-temurin:17-jdk

WORKDIR /app
COPY src ./src
COPY web ./web
COPY data ./data

RUN mkdir out && javac -d out src/*.java

ENV PORT=8080
EXPOSE 8080
CMD ["sh", "-c", "java -cp out Main ${PORT}"]
