# config-service — ARCHITECTURE.md section 8.2
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

RUN mvn -B -f backend/pom.xml -pl config-service -am package -DskipTests -Dbuf.skip=true

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S cuas && adduser -S cuas -G cuas
WORKDIR /app
COPY --from=build /build/backend/config-service/target/*.jar app.jar
USER cuas
EXPOSE 8091 9091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
