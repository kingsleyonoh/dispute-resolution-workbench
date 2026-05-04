FROM clojure:tools-deps-1.12.0.1479 AS build

WORKDIR /app
COPY deps.edn ./
RUN clojure -P
COPY . .
RUN clojure -T:build uber

FROM eclipse-temurin:22-jre-alpine

WORKDIR /app
RUN apk add --no-cache wkhtmltopdf
COPY --from=build /app/target/dispute-resolution-workbench.jar /app/app.jar

ENV APP_ENV=production
ENV PORT=3049
EXPOSE 3049

CMD ["java", "-jar", "/app/app.jar"]
