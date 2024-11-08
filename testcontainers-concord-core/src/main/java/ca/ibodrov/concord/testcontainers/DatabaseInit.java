package ca.ibodrov.concord.testcontainers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.io.IOException;
import java.io.InputStream;

public final class DatabaseInit {

    public static void addInitScriptFromClassPath(GenericContainer<?> db, String dbInitScriptPath, String containerName) {
        try (InputStream in = DatabaseInit.class.getResourceAsStream(dbInitScriptPath)) {
            if (in == null) {
                throw new IllegalArgumentException("Can't find the DB init script: " + dbInitScriptPath);
            }

            byte[] ab = in.readAllBytes();
            db.withCopyToContainer(Transferable.of(ab), "/docker-entrypoint-initdb.d/" + containerName);
        } catch (IOException e) {
            throw new RuntimeException("Error while reading the DB init script: " + e.getMessage(), e);
        }
    }

    private DatabaseInit() {
    }
}
