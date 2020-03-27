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
import com.walmartlabs.concord.client.StartProcessResponse;

import java.util.Map;

public final class Processes {

    private final ApiClient client;

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
     * Starts a new Concord process using the provided payload.
     *
     * @see #start(Map)
     */
    public ConcordProcess start(Payload builder) throws ApiException {
        return start(builder.getInput());
    }

    Processes(ApiClient client) {
        this.client = client;
    }
}
