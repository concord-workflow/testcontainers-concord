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

import ca.ibodrov.concord.testcontainers.Concord.Mode;
import com.walmartlabs.concord.common.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.*;

public class Payload {

    private final Map<String, Object> input = new LinkedHashMap<>();
    private final List<String> tags = new ArrayList<>();

    /**
     * Sets the content of the main concord.yml file.
     */
    public Payload concordYml(String content) {
        input.put("concord.yml", content.getBytes());
        return this;
    }

    public Payload parameter(String key, Object value) {
        input.put(key, value);
        return this;
    }

    public Payload archive(byte[] archive) {
        input.put("archive", archive);
        return this;
    }

    public Payload archive(URI uri) {
        try {
            return archive(Utils.archive(uri));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Payload entryPoint(String entryPoint) {
        input.put("entryPoint", entryPoint);
        return this;
    }

    public Payload activeProfiles(String profiles) {
        input.put("activeProfiles", profiles);
        return this;
    }

    public Payload arg(String name, Object value) {
        input.put("arguments." + name, value);
        return this;
    }

    public Payload out(String... variables) {
        input.put("out", variables);
        return this;
    }

    public Payload file(String name, byte[] content) {
        input.put(name, content);
        return this;
    }

    public Payload resource(String name, URL url) throws IOException {
        byte[] ab;
        try (InputStream in = url.openStream()) {
            ab = IOUtils.toByteArray(in);
        }
        return file(name, ab);
    }

    public Payload parent(UUID parentInstanceId) {
        input.put("parentInstanceId", parentInstanceId.toString());
        return this;
    }

    public Payload org(String orgName) {
        input.put("org", orgName);
        return this;
    }

    public Payload project(String projectName) {
        input.put("project", projectName);
        return this;
    }

    public Payload tag(String tag) {
        tags.add(tag);
        return this;
    }

    /**
     * Enable remote debugging for the process.
     * The process will listen on the specified {@code port}, optionally
     * waiting for the debugger to connect.
     * <p/>
     * The remote debug mode only works with {@link Mode#LOCAL}
     * and {@link Mode#REMOTE} as it requires being able to connect to
     * the remote agent host.
     *
     * @param port    local port to listen for debugger connections.
     * @param suspend if {@code true} the process waits for the debugger
     *                to connect before it starts running.
     */
    public Payload remoteDebug(int port, boolean suspend) {
        String template = "{ \"jvmArgs\": [\"-Xdebug\", \"-Xrunjdwp:transport=dt_socket,address=%d,server=y,suspend=%s\"] }";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(String.format(template, port, suspend ? 'y' : 'n').getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        input.put("_agent.json", baos.toByteArray());
        return this;
    }

    Map<String, Object> build() {
        Map<String, Object> m = new HashMap<>(input);

        if (!tags.isEmpty()) {
            String s = String.join(",", tags);
            m.put("tags", s);
        }

        return m;
    }
}
