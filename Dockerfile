FROM gradle:jdk8 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle shadowJar --no-daemon

FROM openjdk:18

ENV TOKEN=$TOKEN
ENV DB_CONN_STRING=$DB_CONN_STRING

COPY --from=build /home/gradle/src/build/libs/*.jar /bot.jar

ENTRYPOINT ["java","-XX:ErrorFile=logs/hs_err_pid%p.log","-jar","bot.jar"]
