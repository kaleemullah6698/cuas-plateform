# syntax=docker/dockerfile:1
# sensor-simulator — ARCHITECTURE.md section 8.2
#
# Built from the repository root: the Maven reactor is backend/pom.xml, and the contracts module
# needs the generated Java that lives beside it under contracts/generated/java.
#
# Two things this build does that are easy to get wrong:
#   -f backend/pom.xml   there is no pom at the repository root, so -pl alone cannot resolve
#   -Dbuf.skip=true      buf is not in the maven base image; the bindings arrive pre-generated
#                        in the build context (see the guard below and .dockerignore)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .

# Fail here, with a readable message, rather than three minutes later with a compile error
# listing hundreds of missing symbols. contracts/generated is git-ignored build output, so a
# clean checkout has to run 'buf generate' before the image can be built.
RUN test -d contracts/generated/java/pk || ( \
      echo 'ERROR: contracts/generated/java is missing from the build context.' && \
      echo 'Run "buf generate" in contracts/ before building this image (see docs/DEVELOPMENT.md).' && \
      exit 1 )

# A BuildKit cache mount shared by all thirteen images. Without it each build downloads the whole
# dependency tree from Maven Central independently — thirteen times over, and the first attempt at
# this failed when one 6.7 MB artifact arrived truncated. sharing=locked serialises the first
# populate so parallel builds cannot corrupt each other's downloads; the retry handler absorbs the
# transient truncations that started this.
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -f backend/pom.xml -pl sensor-simulator -am package -DskipTests -Dbuf.skip=true -Dmaven.wagon.http.retryHandler.count=3

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S cuas && adduser -S cuas -G cuas
WORKDIR /app
COPY --from=build /build/backend/sensor-simulator/target/*.jar app.jar
USER cuas
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
