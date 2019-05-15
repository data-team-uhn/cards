# LFS Data Core based on Apache Sling

## Prerequisits:
* Java 1.8+
* Maven 3.3+

## Build:
`mvn clean install`

## Run:
`java -jar distribution/target/lfs-*.jar` => the app will run at `http://localhost:8080` (default port)

`java -jar distribution/target/lfs-*.jar -p PORT` to run at a different port

`java -jar distribution/target/lfs-*.jar -Dsling.run.modes=dev` to include the content browser (Composum), accessible at `http://localhost:8080/bin/browser.html`
