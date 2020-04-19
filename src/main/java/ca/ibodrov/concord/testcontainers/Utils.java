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
import com.walmartlabs.concord.client.SecretOperationResponse;
import com.walmartlabs.concord.common.IOUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;

public final class Utils {

    public static byte[] archive(URI uri) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
            IOUtils.zip(zip, Paths.get(uri));
        }
        return out.toByteArray();
    }

    public static <T> T request(ApiClient client, String uri, Map<String, Object> input, Class<T> entityType) throws ApiException {
        ApiResponse<T> resp = ClientUtils.postData(client, uri, input, entityType);

        int code = resp.getStatusCode();
        if (code < 200 || code >= 300) {
            throw new ApiException("Request error: " + code);
        }

        return resp.getData();
    }

    public static SecretOperationResponse postSecret(ApiClient client, String orgName, Map<String, Object> input) throws ApiException {
        return Utils.request(client, "/api/v1/org/" + orgName + "/secret", input, SecretOperationResponse.class);
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

    private Utils() {
    }
}
