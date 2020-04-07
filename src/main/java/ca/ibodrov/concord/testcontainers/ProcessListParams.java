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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ProcessListParams {

    private UUID projectId;

    private List<String> tags = new ArrayList<>();

    private int limit = 1;

    private int offset = 0;

    public ProcessListParams projectId(UUID projectId) {
        this.projectId = projectId;
        return this;
    }

    public UUID projectId() {
        return projectId;
    }

    public ProcessListParams tags(String ... tags) {
        this.tags.addAll(Arrays.asList(tags));
        return this;
    }

    public List<String> tags() {
        return tags;
    }

    public int limit() {
        return limit;
    }

    public int offset() {
        return offset;
    }
}
