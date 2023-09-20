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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client2.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Processes {

    private final ApiClient client;

    public Processes(ApiClient client) {
        this.client = client;
    }

    /**
     * Build a new process.
     */
    public ProcessBuilder create() {
        return new ProcessBuilder(client, this);
    }

    /**
     * Starts a new Concord process using the provided data as the request parameters.
     *
     * @see <a href="https://concord.walmartlabs.com/docs/api/process.html#form-data">API docs</a>.
     */
    public ConcordProcess start(Map<String, Object> input) throws ApiException {
        ProcessApi processApi = new ProcessApi(client);
        StartProcessResponse spr = processApi.startProcess(input);
        return new ConcordProcess(client, spr.getInstanceId());
    }

    /**
     * Fetches an existing Concord process using the provided ID.
     */
    public ConcordProcess get(UUID instanceId) throws ApiException {
        ProcessV2Api processV2Api = new ProcessV2Api(client);

        ProcessEntry entry = processV2Api.getProcess(instanceId, Collections.emptySet());
        if (entry == null) {
            throw new IllegalArgumentException("Process not found: " + instanceId);
        }

        return new ConcordProcess(client, instanceId);
    }

    /**
     * Starts a new Concord process using the provided payload.
     *
     * @see #start(Map)
     */
    public ConcordProcess start(Payload builder) throws ApiException {
        return start(builder.build());
    }

    /**
     * Creates a new process list query.
     */
    public List<ProcessEntry> list(ProcessListFilter filter) throws ApiException {
        ProcessV2Api processApi = new ProcessV2Api(client);
        return processApi.listProcesses(filter);
    }
}
