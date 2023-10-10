package ca.ibodrov.concord.testcontainers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2020 Ivan Bodrov
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.IOUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.*;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class Utils {

    private static final char[] RANDOM_CHARS = "abcdef0123456789".toCharArray();

    public static byte[] archive(URI uri) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
            IOUtils.zip(zip, Paths.get(uri));
        }
        return out.toByteArray();
    }

    public static int reservePort(int start) {
        for (int i = start; i < 65536; i++) {
            try (ServerSocket socket = new ServerSocket(i)) {
                return socket.getLocalPort();
            } catch (IOException e) {
                // continue
            }
        }

        throw new RuntimeException("Can't reserve a port (starting from " + start + ")");
    }

    public static boolean isSame(ProcessEntry.StatusEnum status, ProcessEntry.StatusEnum first, ProcessEntry.StatusEnum... more) {
        if (status == first) {
            return true;
        }

        if (more != null) {
            for (ProcessEntry.StatusEnum s : more) {
                if (status == s) {
                    return true;
                }
            }
        }

        return false;
    }

    public static List<String> grep(String pattern, byte[] ab) {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(ab)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.matches(pattern)) {
                    result.add(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static String randomString() {
        StringBuilder b = new StringBuilder();
        b.append(System.currentTimeMillis()).append("_");

        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            int n = rng.nextInt(RANDOM_CHARS.length);
            b.append(RANDOM_CHARS[n]);
        }

        return b.toString();
    }

    public static String randomToken() {
        byte[] ab = new byte[16];
        ThreadLocalRandom.current().nextBytes(ab);

        Base64.Encoder e = Base64.getEncoder().withoutPadding();
        return e.encodeToString(ab);
    }

    public static String randomPwd() {
        return "pwd_" + randomString() + "A!";
    }

    public static Path getLocalMavenRepositoryPath() {
        String localPath = System.getProperty("maven.repo.local");
        if (localPath == null) {
            // fallback
            return Paths.get(System.getProperty("user.home"), ".m2", "repository");
        }

        return Paths.get(localPath);
    }

    private Utils() {
    }
}
