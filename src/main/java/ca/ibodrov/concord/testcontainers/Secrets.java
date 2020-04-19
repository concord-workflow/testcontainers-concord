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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.SecretEntry;
import com.walmartlabs.concord.client.SecretOperationResponse;

import java.util.HashMap;
import java.util.Map;

public class Secrets {

    private final ApiClient apiClient;

    Secrets(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Creates a new single-value secret.
     */
    public SecretOperationResponse createSecret(NewSecretQuery query, byte[] value) throws ApiException {
        Map<String, Object> m = serialize(query);
        m.put("type", SecretEntry.TypeEnum.DATA.toString());
        m.put("data", value);
        return Utils.postSecret(apiClient, query.org(), m);
    }

    /**
     * Creates a new credentials (username/password pair) secret.
     */
    public SecretOperationResponse createSecret(NewSecretQuery query, String username, String password) throws ApiException {
        Map<String, Object> m = serialize(query);
        m.put("type", SecretEntry.TypeEnum.USERNAME_PASSWORD.toString());
        m.put("username", username);
        m.put("password", password);
        return Utils.postSecret(apiClient, query.org(), m);
    }

    public SecretOperationResponse generateKeyPair(NewSecretQuery query) throws ApiException {
        Map<String, Object> m = serialize(query);
        m.put("type", SecretEntry.TypeEnum.KEY_PAIR.toString());
        return Utils.postSecret(apiClient, query.org(), m);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serialize(NewSecretQuery query) {
        ObjectMapper om = new ObjectMapper();
        Map<String, Object> m = om.convertValue(query, Map.class);
        // make it mutable
        return new HashMap<>(m);
    }
}
