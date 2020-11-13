A really strange bug including OKHttpClient and Dropwizard

### Instructions

```bash
# build jar
mvn clean package
# start server
java -jar target/bug-0.1.jar server config.yml
# (in a second terminal) run client
java -cp target/bug-0.1.jar com.graphhopper.bug.Client
```