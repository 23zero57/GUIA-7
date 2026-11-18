FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY . .
RUN apt-get update && apt-get install -y maven
RUN mvn compile
EXPOSE 7000
CMD ["mvn", "exec:java", "-Dexec.mainClass=com.umb.taller.Main"]
