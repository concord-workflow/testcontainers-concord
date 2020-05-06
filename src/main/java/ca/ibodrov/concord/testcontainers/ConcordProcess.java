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

import com.google.gson.reflect.TypeToken;
import com.squareup.okhttp.Call;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ConcordProcess {

    private static final Logger log = LoggerFactory.getLogger(ConcordProcess.class);

    private final ApiClient client;
    private final UUID instanceId;

    public ConcordProcess(ApiClient client, UUID instanceId) {
        this.client = client;
        this.instanceId = instanceId;
    }

    /**
     * Returns the process' ID.
     */
    public UUID instanceId() {
        return instanceId;
    }

    public ProcessEntry getEntry(String ... includes) throws ApiException {
        ProcessV2Api api = new ProcessV2Api(client);
        return api.get(instanceId, Arrays.asList(includes));
    }

    /**
     * Waits for the process to reach the specified status. Throws an exception if
     * the process ends up in an unexpected status.
     *
     * @return the process queue entry for the process.
     */
    public ProcessEntry expectStatus(StatusEnum status, StatusEnum... more) throws ApiException {
        ProcessEntry pe = waitForStatus(status, more);

        if (!isSame(pe.getStatus(), status, more)) {
            throw new IllegalStateException("Unexpected status of the process: " + pe.getStatus());
        }

        return pe;
    }

    /**
     * Waits for the process to reach the specified or one of the final statuses.
     *
     * @return the process queue entry for the process.
     */
    public ProcessEntry waitForStatus(StatusEnum status, StatusEnum... more) throws ApiException {
        ProcessV2Api api = new ProcessV2Api(client);

        return waitForStatus(() -> Collections.singletonList(api.get(instanceId, Collections.emptyList())), status, more);
    }

    /**
     * Waits for the child process to reach the specified or one of the final statuses.
     *
     * @return the process queue entry for the child process.
     */
    public ProcessEntry waitForChildStatus(StatusEnum status, StatusEnum... more) throws ApiException {
        ProcessApi api = new ProcessApi(client);
        return waitForStatus(() -> api.listSubprocesses(instanceId, null), status, more);
    }

    /**
     * Check if the process' log matches the specified pattern (regex).
     */
    public void assertLog(@Language("RegExp") String pattern) throws ApiException {
        byte[] ab = getLog();
        String msg = "Expected: " + pattern + "\nGot: " + new String(ab);
        assertEquals(msg, 1, grep(pattern, ab).size());
    }

    /**
     * Check if the process' log doesn't match the specified pattern (regex).
     */
    public void assertNoLog(@Language("RegExp") String pattern) throws ApiException {
        byte[] ab = getLog();
        String msg = "Expected: " + pattern + "\nGot: " + new String(ab);
        assertEquals(msg, 0, grep(pattern, ab).size());
    }

    /**
     * Check if the process' log matches the specified pattern (regex) at least the specified number of times.
     */
    public void assertLogAtLeast(@Language("RegExp") String pattern, int times) throws ApiException {
        byte[] ab = getLog();
        assertTrue(times <= grep(pattern, ab).size());
    }

    /**
     * Returns a list of forms in the current process waiting for user input.
     */
    public List<FormListEntry> forms() throws ApiException {
        ProcessFormsApi formsApi = new ProcessFormsApi(client);
        return formsApi.list(instanceId);
    }

    /**
     * Submits a form.
     */
    public FormSubmitResponse submitForm(String formName, Map<String, Object> data) throws ApiException {
        ProcessFormsApi formsApi = new ProcessFormsApi(client);
        return formsApi.submit(instanceId, formName, data);
    }

    /**
     * Disable the process.
     */
    public ProcessEntry disable() throws ApiException {
        ProcessApi processApi = new ProcessApi(client);
        processApi.disable(instanceId, true);

        ProcessV2Api processV2Api = new ProcessV2Api(client);
        return processV2Api.get(instanceId, null);
    }

    /**
     * Kill (cancel) the process and all it's subprocesses.
     */
    public void killCascade() throws ApiException {
        ProcessApi processApi = new ProcessApi(client);
        processApi.killCascade(instanceId);
    }

    /**
     * Returns a list of subprocesses.
     */
    public List<ProcessEntry> subprocesses() throws ApiException {
        return subprocesses((String[]) null);
    }

    /**
     * Returns a list of subprocesses tagged with any of the specified values.
     */
    public List<ProcessEntry> subprocesses(String ... tags) throws ApiException {
        ProcessApi processApi = new ProcessApi(client);
        return processApi.listSubprocesses(instanceId, tags == null ? null : Arrays.asList(tags));
    }

    private byte[] getLog() throws ApiException {
        Set<String> auths = client.getAuthentications().keySet();
        String[] authNames = auths.toArray(new String[0]);

        Call c = client.buildCall("/api/v1/process/" + instanceId + "/log", "GET", new ArrayList<>(), new ArrayList<>(),
                null, new HashMap<>(), new HashMap<>(), authNames, null);

        Type t = new TypeToken<byte[]>() {
        }.getType();
        return client.<byte[]>execute(c, t).getData();
    }

    private static boolean isSame(StatusEnum status, StatusEnum first, StatusEnum... more) {
        if (status == first) {
            return true;
        }

        if (more != null) {
            for (StatusEnum s : more) {
                if (status == s) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<String> grep(String pattern, byte[] ab) {
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

    private static ProcessEntry waitForStatus(ProcessSupplier processSupplier, StatusEnum status, StatusEnum... more) throws ApiException {
        int retries = 10;

        while (true) {
            try {
                List<ProcessEntry> processes = processSupplier.get();
                for (ProcessEntry pe : processes) {
                    if (pe.getStatus() == StatusEnum.FINISHED || pe.getStatus() == StatusEnum.FAILED || pe.getStatus() == StatusEnum.CANCELLED) {
                        return pe;
                    }

                    if (isSame(pe.getStatus(), status, more)) {
                        return pe;
                    }
                }
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    log.warn("waitForStatus -> process not found, retrying... ({})", retries);
                    if (--retries < 0) {
                        throw e;
                    }
                }
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private interface ProcessSupplier {

        List<ProcessEntry> get() throws ApiException;
    }
}
