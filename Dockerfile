FROM anapsix/alpine-java:8
RUN mkdir /opt/app
ADD target/scala-2.12/akka-sharding-handover-assembly-1.0.jar /opt/app/app.jar
CMD ["java", "-jar", "/opt/app/app.jar"]