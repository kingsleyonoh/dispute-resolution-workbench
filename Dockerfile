FROM node:22-alpine AS assets

WORKDIR /app
COPY package.json package-lock.json* tailwind.config.js ./
COPY resources/assets/styles ./resources/assets/styles
COPY src ./src
RUN npm install
RUN npm run build:css

FROM clojure:tools-deps-1.12.0.1479 AS deps

WORKDIR /app
COPY deps.edn ./
RUN clojure -P

FROM deps AS dev

COPY . .
CMD ["clojure", "-M:dev"]

FROM deps AS build

COPY . .
COPY --from=assets /app/resources/public/assets/app.css ./resources/public/assets/app.css
RUN clojure -T:build uber

FROM eclipse-temurin:22-jre-alpine

WORKDIR /app
COPY --from=build /app/target/dispute-resolution-workbench.jar /app/app.jar

ENV APP_ENV=production
ENV PORT=3049
EXPOSE 3049

CMD ["java", "-jar", "/app/app.jar"]
