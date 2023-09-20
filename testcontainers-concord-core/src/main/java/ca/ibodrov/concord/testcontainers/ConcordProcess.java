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
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.client2.ProcessEntry.StatusEnum;
import org.apache.commons.io.IOUtils;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ConcordProcess {

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

    public ProcessEntry getEntry(String... includes) throws ApiException {
        ProcessV2Api api = new ProcessV2Api(client);
        return api.getProcess(instanceId, new HashSet<>(Arrays.asList(includes)));
    }

    /**
     * Waits for the process to reach the specified status. Throws an exception if
     * the process ends up in an unexpected status.
     *
     * @return the process queue entry for the process.
     */
    public ProcessEntry expectStatus(StatusEnum status, StatusEnum... more) throws ApiException {
        ProcessEntry pe = waitForStatus(status, more);

        if (!Utils.isSame(pe.getStatus(), status, more)) {
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

        return waitForStatus(() -> Collections.singletonList(api.getProcess(instanceId, Collections.emptySet())), status, more);
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
        assertEquals(msg, 1, Utils.grep(pattern, ab).size());
    }

    /**
     * Check if the process' log doesn't match the specified pattern (regex).
     */
    public void assertNoLog(@Language("RegExp") String pattern) throws ApiException {
        byte[] ab = getLog();
        String msg = "Expected: " + pattern + "\nGot: " + new String(ab);
        assertEquals(msg, 0, Utils.grep(pattern, ab).size());
    }

    /**
     * Check if the process' log matches the specified pattern (regex) at least the specified number of times.
     */
    public void assertLogAtLeast(@Language("RegExp") String pattern, int times) throws ApiException {
        byte[] ab = getLog();
        String msg = "Expected " + pattern + " at least " + times + " time(s)\nGot: " + new String(ab);
        assertTrue(msg, times <= Utils.grep(pattern, ab).size());
    }

    /**
     * Returns a list of forms in the current process waiting for user input.
     */
    public List<FormListEntry> forms() throws ApiException {
        ProcessFormsApi formsApi = new ProcessFormsApi(client);
        return formsApi.listProcessForms(instanceId);
    }

    /**
     * Returns the list of current checkpoints.
     */
    public List<ProcessCheckpointEntry> checkpoints() throws ApiException {
        CheckpointApi checkpointApi = new CheckpointApi(client);
        return checkpointApi.listCheckpoints(instanceId);
    }

    /**
     * Restores the process from the specified checkpoint.
     */
    public void restoreCheckpoint(UUID checkpointId) throws ApiException {
        CheckpointApi checkpointApi = new CheckpointApi(client);
        checkpointApi.restore(instanceId, new RestoreCheckpointRequest().id(checkpointId));
    }

    /**
     * Submits a form.
     */
    public FormSubmitResponse submitForm(String formName, Map<String, Object> data) throws ApiException {
        ProcessFormsApi formsApi = new ProcessFormsApi(client);
        return formsApi.submitForm(instanceId, formName, data);
    }

    /**
     * Disable the process.
     */
    public ProcessEntry disable() throws ApiException {
        ProcessApi processApi = new ProcessApi(client);
        processApi.disable(instanceId, true);

        ProcessV2Api processV2Api = new ProcessV2Api(client);
        return processV2Api.getProcess(instanceId, null);
    }

    /**
     * Kill (cancel) the process.
     */
    public void kill() throws ApiException {
        ProcessApi processApi = new ProcessApi(client);
        processApi.kill(instanceId);
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
    public List<ProcessEntry> subprocesses(String... tags) throws ApiException {
        ProcessApi processApi = new ProcessApi(client);
        return processApi.listSubprocesses(instanceId, tags == null ? null : new HashSet<>(Arrays.asList(tags)));
    }

    /**
     * Returns process out variables.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOutVariables() {
        ProcessApi processApi = new ProcessApi(client);
        try (InputStream is = processApi.downloadAttachment(instanceId, "out.json")) {
            return new ObjectMapper().readValue(is, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Error converting out variables: " + e.getMessage());
        }
    }

    public List<String> getLogLines() throws ApiException {
        return getLogLines(line -> true);
    }

    public List<String> getLogLines(Predicate<String> lineFilter) throws ApiException {
        try {
            return IOUtils.readLines(
                    new InputStreamReader(
                            new ByteArrayInputStream(getLog())))
                    .stream()
                    .filter(lineFilter)
                    .collect(Collectors.toList());
        } catch (IOException ioex) {
            throw new RuntimeException("Failed to read log lines", ioex);
        }
    }

    public byte[] getLog() throws ApiException {
        ProcessApi processApi = new ProcessApi(client);
        try (InputStream is = processApi.getProcessLog(instanceId, null)) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

                    if (Utils.isSame(pe.getStatus(), status, more)) {
                        return pe;
                    }
                }
            } catch (ApiException e) {
                if (--retries < 0) {
                    throw e;
                }

                if (e.getCode() == 404) {
                    log.warn("waitForStatus -> process not found, retrying... ({})", retries);
                }
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void assertEquals(String msg, Object expected, Object actual) {
        if (expected == null && actual == null) {
            return;
        }

        if (expected != null && expected.equals(actual)) {
            return;
        }

        throw new IllegalStateException(msg);
    }

    private static void assertTrue(String msg, boolean value) {
        if (value) {
            return;
        }

        throw new IllegalStateException(msg);
    }

    private interface ProcessSupplier {

        List<ProcessEntry> get() throws ApiException;
    }
}
