package ca.ibodrov.concord.testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.client2.ProcessEntry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PersistentWorkdirTest {

    private static Path persistentWorkDir;
    private static Concord<?> concord;

    @BeforeAll
    public static void setUp() throws Exception {
        persistentWorkDir = Files.createTempDirectory("test");

        concord = new Concord<>()
                .mode(Concord.Mode.DOCKER)
                .persistentWorkDir(persistentWorkDir)
                .streamAgentLogs(true)
                .streamServerLogs(true);

        concord.start();
    }

    @AfterAll
    public static void tearDown() {
        if (concord != null) {
            concord.close();
        }

        concord = null;
    }

    @Test
    public void test() throws Exception {
        Payload payload = new Payload()
                .archive(PersistentWorkdirTest.class.getResource("persistentWorkDir").toURI())
                .out("filePath");

        ConcordProcess proc = concord.processes().start(payload);
        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        Map<String, Object> out = proc.getOutVariables();

        String filePath = (String) out.get("filePath");
        assertNotNull(filePath);

        Path p = persistentWorkDir.resolve(proc.instanceId().toString()).resolve(filePath);
        assertTrue(Files.exists(p));

        Map<String, Object> map = parseJsonFile(p);
        assertEquals(2, map.size());
        assertEquals(123, map.get("x"));
        assertEquals(234, map.get("y"));
    }

    private static Map<String, Object> parseJsonFile(Path p) throws IOException {
        ObjectMapper om = new ObjectMapper();
        try (InputStream in = Files.newInputStream(p)) {
            return om.readValue(in, Map.class);
        }
    }
}
