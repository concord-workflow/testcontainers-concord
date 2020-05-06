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
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.ProcessV2Api;
import com.walmartlabs.concord.client.StartProcessResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Processes {

    private final ApiClient client;

    Processes(ApiClient client) {
        this.client = client;
    }

    /**
     * Starts a new Concord process using the provided data as the request parameters.
     *
     * @see <a href="https://concord.walmartlabs.com/docs/api/process.html#form-data">API docs</a>.
     */
    public ConcordProcess start(Map<String, Object> input) throws ApiException {
        ApiResponse<StartProcessResponse> resp = ClientUtils.postData(client, "/api/v1/process", input, StartProcessResponse.class);

        int code = resp.getStatusCode();
        if (code < 200 || code >= 300) {
            if (code == 403) {
                throw new ApiException("Forbidden: " + resp.getData());
            }

            throw new ApiException("Request error: " + code);
        }

        return new ConcordProcess(client, resp.getData().getInstanceId());
    }

    /**
     * Fetches an existing Concord process using the provided ID.
     */
    public ConcordProcess get(UUID instanceId) throws ApiException {
        ProcessV2Api processV2Api = new ProcessV2Api(client);

        ProcessEntry entry = processV2Api.get(instanceId, Collections.emptyList());
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
    public List<ProcessEntry> list(ProcessListQuery query) throws ApiException {
        ProcessV2Api processApi = new ProcessV2Api(client);
        return processApi.list(null, null, query.projectId(), null, null,
                null, null, null, query.tags(), null,
                null, null, null, query.limit(), query.offset());
    }
}
