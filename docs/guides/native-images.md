# Native Images

migratory works in a GraalVM native image with no configuration from libraries or applications.

Spring's ahead-of-time step (`process-aot`) runs on the JVM with the application's full class path,
so it sees every declaring library the image will contain. There migratory:

- finds, validates and sorts the declarations, failing the build on a bad declaration or a cycle
  (even when `migratory.enabled=false` at build time, since it may be turned on when the image runs);
- writes the sorted list into the image (`META-INF/migratory/resolved-migrations.properties`), so
  the image never searches its class path for declarations;
- registers that file, and everything under each declared changelog's directory, as resources, so
  the changelogs a library's changelog includes, and the SQL files its changesets read, are in the
  image.

So a library keeps its included changelogs and SQL files under its declared changelog's directory.

migratory also puts Liquibase's XML schemas in the image, and builds it with
`--enable-url-protocols=http,https`: the JDK's XML parser turns the schema location a changelog
names (`https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd`, or the same over
http) into a `java.net.URL`, which a native image refuses for a protocol it was not built with. Liquibase still reads the schema from its jar, never from the network.

The image runs exactly the migrations that were on the class path when it was built.
`migratory.enabled` is read when the migrations would run, not decided at build time, so
`--migratory.enabled=false` works on a native binary as on the JVM.
