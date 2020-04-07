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

import java.util.LinkedHashMap;
import java.util.Map;

public final class Payload {

    private final Map<String, Object> input = new LinkedHashMap<>();

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

    public Payload arg(String name, Object value) {
        input.put("arguments." + name, value);
        return this;
    }

    Map<String, Object> getInput() {
        return input;
    }
}
