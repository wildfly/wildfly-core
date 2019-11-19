/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.management.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.test.http.Authentication;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Test case to test custom / constant headers are applied to existing contexts.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(WildflyTestRunner.class)
public class HttpManagementConstantHeadersTestCase {

    private static final int MGMT_PORT = 9990;
    private static final String MGMT_CTX = "/management";
    private static final String ERROR_CTX = "/error";

    private static final PathAddress INTERFACE_ADDRESS = PathAddress.pathAddress(PathElement.pathElement("core-service", "management"),
            PathElement.pathElement("management-interface", "http-interface"));

    @Inject
    protected ManagementClient managementClient;

    private URL managementUrl;
    private URL errorUrl;
    private HttpClient httpClient;

    @Before
    public void createClient() throws Exception {
        String address = managementClient.getMgmtAddress();
        this.managementUrl = new URL("http", address, MGMT_PORT, MGMT_CTX);
        this.errorUrl = new URL("http", address, MGMT_PORT, ERROR_CTX);

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(managementUrl.getHost(), managementUrl.getPort()), new UsernamePasswordCredentials(Authentication.USERNAME, Authentication.PASSWORD));

        this.httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();
    }

    @After
    public void closeClient() {
        if (httpClient instanceof Closeable) {
            try {
                ((Closeable) httpClient).close();
            } catch (IOException e) {
                Logger.getLogger(XCorrelationIdTestCase.class).error("Failed closing client", e);
            }
        }
    }

    @Before
    public void activateHeaders() throws Exception {
        Map<String, Map<String, String>> headersMap = new HashMap<>();
        headersMap.put("/", Collections.singletonMap("X-All", "All"));
        headersMap.put("/management", Collections.singletonMap("X-Management", "Management"));
        headersMap.put("/error", Collections.singletonMap("X-Error", "Error"));

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));

        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
    }

    private static ModelNode createConstantHeadersOperation(final Map<String, Map<String, String>> constantHeadersValues) {
        ModelNode writeAttribute = new ModelNode();
        writeAttribute.get("address").set(INTERFACE_ADDRESS.toModelNode());
        writeAttribute.get("operation").set("write-attribute");
        writeAttribute.get("name").set("constant-headers");

        ModelNode constantHeaders = new ModelNode();
        for (Entry<String, Map<String, String>> entry : constantHeadersValues.entrySet()) {
            constantHeaders.add(createHeaderMapping(entry.getKey(), entry.getValue()));
        }

        writeAttribute.get("value").set(constantHeaders);

        return writeAttribute;
    }

    private static ModelNode createHeaderMapping(final String path, final Map<String, String> headerValues) {
        ModelNode headerMapping = new ModelNode();
        headerMapping.get("path").set(path);
        ModelNode headers = new ModelNode();
        headers.add();     // Ensure the type of 'headers' is List even if no content is added.
        headers.remove(0);
        for (Entry<String, String> entry : headerValues.entrySet()) {
            ModelNode singleMapping = new ModelNode();
            singleMapping.get("name").set(entry.getKey());
            singleMapping.get("value").set(entry.getValue());
            headers.add(singleMapping);
        }
        headerMapping.get("headers").set(headers);

        return headerMapping;
    }

    @After
    public void removeHeaders() throws Exception {
        ModelNode undefineAttribute = new ModelNode();
        undefineAttribute.get("address").set(INTERFACE_ADDRESS.toModelNode());
        undefineAttribute.get("operation").set("undefine-attribute");
        undefineAttribute.get("name").set("constant-headers");

        managementClient.executeForResult(undefineAttribute);

        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
    }

    /**
     * Test that a call to the '/management' endpoint returns the expected headers.
     */
    @Test
    public void testManagement() throws Exception {
        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header header = response.getFirstHeader("X-All");
        assertEquals("All", header.getValue());

        header = response.getFirstHeader("X-Management");
        assertEquals("Management", header.getValue());

        header = response.getFirstHeader("X-Error");
        assertNull("Header X-Error Unexpected", header);
    }

    /**
     * Test that a call to the '/error' endpoint returns the expected headers.
     */
    @Test
    public void testError() throws Exception {
        HttpGet get = new HttpGet(errorUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header header = response.getFirstHeader("X-All");
        assertEquals("All", header.getValue());

        header = response.getFirstHeader("X-Error");
        assertEquals("Error", header.getValue());

        header = response.getFirstHeader("X-Management");
        assertNull("Header X-Management Unexpected", header);
    }

    /**
     * Test that attempting to define a constant-headers attribute with an empty headers list is correctly rejected.
     */
    @Test
    public void testEmptyHeadersList() {
        Map<String, Map<String, String>> headersMap = new HashMap<>();
        headersMap.put("/", Collections.emptyMap());

        try {
            managementClient.executeForResult(createConstantHeadersOperation(headersMap));
            fail("Operation was expected to fail.");
        } catch (UnsuccessfulOperationException e) {
            assertTrue(e.getMessage().contains("WFLYCTL0115"));
        }
    }

}
