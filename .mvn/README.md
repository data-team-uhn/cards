# `.mvn`

This directory exists to mark the root of the Maven reactor.

Maven's launcher walks up from the working directory looking for a `.mvn` directory, and sets
`${maven.multiModuleProjectDirectory}` to the directory that contains it. Without this marker the
property falls back to whichever directory `mvn` happened to be invoked from, which breaks any
configuration that needs to point at a file kept at the top of the repository.

The root `pom.xml` relies on it to locate `spotbugs-exclude.xml`, so that SpotBugs resolves the same
exclusion file whether the build is started from the repository root or from a single module
(`mvn -pl modules/... install`).
