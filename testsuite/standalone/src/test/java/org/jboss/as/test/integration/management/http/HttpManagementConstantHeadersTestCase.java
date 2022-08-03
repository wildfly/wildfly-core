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

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.test.http.Authentication;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNull;

/**
 * Test case to test custom / constant headers are applied to existing contexts.
 * See WFCORE-1110.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:tterem@redhat.com">Tomas Terem</a>
 */
@RunWith(WildFlyRunner.class)
public class HttpManagementConstantHeadersTestCase {

    private static final int MGMT_PORT = 9990;
    private static final String ROOT_CTX = "/";
    private static final String MGMT_CTX = "/management";
    private static final String ERROR_CTX = "/error";

    private static final String TEST_HEADER = "TestHeader";
    private static final String TEST_VALUE = "TestValue";
    private static final String TEST_HEADER_2 = "TestHeader2";
    private static final String TEST_VALUE_2 = "TestValue2";

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
                Logger.getLogger(HttpManagementConstantHeadersTestCase.class).error("Failed closing client", e);
            }
        }
    }

    private void activateHeaders() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();
        headersMap.put(ROOT_CTX, Collections.singletonList(Collections.singletonMap("X-All", "All")));
        headersMap.put(MGMT_CTX, Collections.singletonList(Collections.singletonMap("X-Management", "Management")));
        headersMap.put(ERROR_CTX, Collections.singletonList(Collections.singletonMap("X-Error", "Error")));

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));

        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
    }

    private static ModelNode createConstantHeadersOperation(final Map<String, List<Map<String, String>>> constantHeadersValues) {
        ModelNode writeAttribute = new ModelNode();
        writeAttribute.get("address").set(INTERFACE_ADDRESS.toModelNode());
        writeAttribute.get("operation").set("write-attribute");
        writeAttribute.get("name").set("constant-headers");

        ModelNode constantHeaders = new ModelNode();
        for (Entry<String, List<Map<String, String>>> entry : constantHeadersValues.entrySet()) {
            for (Map<String, String> header: entry.getValue()) {
                constantHeaders.add(createHeaderMapping(entry.getKey(), header));
            }
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
        activateHeaders();

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

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
        activateHeaders();

        HttpGet get = new HttpGet(errorUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Header header = response.getFirstHeader("X-All");
        assertEquals("All", header.getValue());

        header = response.getFirstHeader("X-Error");
        assertEquals("Error", header.getValue());

        header = response.getFirstHeader("X-Management");
        assertNull("Header X-Management Unexpected", header);
    }

    /**
     * Test that security headers can be configured and are returned from '/management' endpoint
     */
    @Test
    public void testSecurity() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap("X-XSS-Protection", "1; mode=block"));
        headers.add(Collections.singletonMap("X-Frame-Options", "SAMEORIGIN"));
        headers.add(Collections.singletonMap("Content-Security-Policy", "default-src https: data: 'unsafe-inline' 'unsafe-eval'"));
        headers.add(Collections.singletonMap("Strict-Transport-Security", "max-age=31536000; includeSubDomains;"));
        headers.add(Collections.singletonMap("X-Content-Type-Options", "nosniff"));

        headersMap.put(MGMT_CTX, headers);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Header header = response.getFirstHeader("X-XSS-Protection");
        assertEquals("1; mode=block", header.getValue());

        header = response.getFirstHeader("X-Frame-Options");
        assertEquals("SAMEORIGIN", header.getValue());

        header = response.getFirstHeader("Content-Security-Policy");
        assertEquals("default-src https: data: 'unsafe-inline' 'unsafe-eval'", header.getValue());

        header = response.getFirstHeader("Strict-Transport-Security");
        assertEquals("max-age=31536000; includeSubDomains;", header.getValue());

        header = response.getFirstHeader("X-Content-Type-Options");
        assertEquals("nosniff", header.getValue());
    }

    /**
     * Test that it is possible to configure same header with multiple values.
     */
    @Test
    public void testMultipleValues() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap(TEST_HEADER, TEST_VALUE));
        headers.add(Collections.singletonMap(TEST_HEADER, TEST_VALUE_2));

        headersMap.put(MGMT_CTX, headers);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        validateExpectedValues(response.getHeaders(TEST_HEADER), TEST_VALUE, TEST_VALUE_2);
    }

    /**
     * Test that if same header is configured multiple times, it is returned multiple times from given endpoint.
     */
    @Test
    public void testDuplicateValues() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap(TEST_HEADER, TEST_VALUE));
        headers.add(Collections.singletonMap(TEST_HEADER, TEST_VALUE));

        headersMap.put(MGMT_CTX, headers);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        validateExpectedValues(response.getHeaders(TEST_HEADER), TEST_VALUE, TEST_VALUE);
    }

    /**
     * Test that header configured for "/" are applied to "/management" and "/error" as well
     */
    @Test
    public void testRootHeadersAppliesToOtherEndpoints() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap(TEST_HEADER, TEST_VALUE));
        headers.add(Collections.singletonMap(TEST_HEADER_2, TEST_VALUE_2));

        headersMap.put(ROOT_CTX, headers);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Header header = response.getFirstHeader(TEST_HEADER);
        assertEquals(TEST_VALUE, header.getValue());

        header = response.getFirstHeader(TEST_HEADER_2);
        assertEquals(TEST_VALUE_2, header.getValue());

        get = new HttpGet(errorUrl.toURI().toString());
        response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        header = response.getFirstHeader(TEST_HEADER);
        assertEquals(TEST_VALUE, header.getValue());

        header = response.getFirstHeader(TEST_HEADER_2);
        assertEquals(TEST_VALUE_2, header.getValue());
    }

    /**
     * Test that both headers configured for "/" and for "/management" endpoint are present on "/management" endpoint
     */
    @Test
    public void testRootHeadersCombineWithManagementHeadersWithDifferentName() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap(TEST_HEADER, TEST_VALUE));

        List<Map<String, String>> headers2 = new LinkedList<>();
        headers2.add(Collections.singletonMap(TEST_HEADER_2, TEST_VALUE_2));

        headersMap.put(ROOT_CTX, headers);
        headersMap.put(MGMT_CTX, headers2);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Header header = response.getFirstHeader(TEST_HEADER);
        assertEquals(TEST_VALUE, header.getValue());

        header = response.getFirstHeader(TEST_HEADER_2);
        assertEquals(TEST_VALUE_2, header.getValue());

        get = new HttpGet(errorUrl.toURI().toString());
        response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        header = response.getFirstHeader(TEST_HEADER);
        assertEquals(TEST_VALUE, header.getValue());

        header = response.getFirstHeader(TEST_HEADER_2);
        assertNull(header);
    }

    /**
     * Test that header values configured for "/" and for "/management" endpoint are all present on "/management" endpoint
     */
    @Test
    public void testRootHeadersCombineWithManagementHeadersWithSameName() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap(TEST_HEADER, TEST_VALUE));

        List<Map<String, String>> headers2 = new LinkedList<>();
        headers2.add(Collections.singletonMap(TEST_HEADER, TEST_VALUE_2));

        headersMap.put(ROOT_CTX, headers);
        headersMap.put(MGMT_CTX, headers2);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        validateExpectedValues(response.getHeaders(TEST_HEADER), TEST_VALUE, TEST_VALUE_2);

        get = new HttpGet(errorUrl.toURI().toString());
        response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());


        validateExpectedValues(response.getHeaders(TEST_HEADER), TEST_VALUE);
    }

    /**
     * Test that after configuring same header for "/" and for "/management", it will be present twice on response from "/management"
     */
    @Test
    public void testRootDuplicateHeadersCombineWithManagementHeaders() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap(TEST_HEADER, TEST_VALUE));

        List<Map<String, String>> headers2 = new LinkedList<>();
        headers2.add(Collections.singletonMap(TEST_HEADER, TEST_VALUE));

        headersMap.put(ROOT_CTX, headers);
        headersMap.put(MGMT_CTX, headers2);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Header[] headerArray = response.getHeaders(TEST_HEADER);

        List<Header> headerList = Arrays.asList(headerArray);

        assertEquals(2, headerList.size());
        assertEquals(TEST_HEADER, headerList.get(0).getName());
        assertEquals(TEST_VALUE, headerList.get(0).getValue());
        assertEquals(TEST_HEADER, headerList.get(1).getName());
        assertEquals(TEST_VALUE, headerList.get(1).getValue());

        get = new HttpGet(errorUrl.toURI().toString());
        response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        headerArray = response.getHeaders(TEST_HEADER);

        headerList = Arrays.asList(headerArray);

        assertEquals(1, headerList.size());
        assertEquals(TEST_HEADER, headerList.get(0).getName());
        assertEquals(TEST_VALUE, headerList.get(0).getValue());
    }

    /**
     * Test that configured headers override original headers set by given endpoint (expect for disallowed headers)
     */
    @Test
    public void testHeadersOverride() throws Exception {
        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Header[] headerArray = response.getAllHeaders();

        List<Header> headerList = Arrays.stream(headerArray)
              .filter(header -> !header.getName().equals("Connection") && !header.getName().equals("Date")
                    && !header.getName().equals("Transfer-Encoding") && !header.getName().equals("Content-Type")
                    && !header.getName().equals("Content-Length"))
              .collect(Collectors.toList());

        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();
        List<Map<String, String>> headers = new LinkedList<>();

        for (Header header : headerList) {
            headers.add(Collections.singletonMap(header.getName(), TEST_VALUE));
        }

        headersMap.put(MGMT_CTX, headers);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        get = new HttpGet(managementUrl.toURI().toString());
        response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        for (Header header : headerList) {
            assertEquals(TEST_VALUE, response.getFirstHeader(header.getName()).getValue());
        }
    }

    /**
     * Test that configuring headers for path "management" has effect on "/management" endpoint
     */
    @Test
    public void testPathWithMissingSlash() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap(TEST_HEADER, TEST_VALUE));

        headersMap.put("management", headers);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Header header = response.getFirstHeader(TEST_HEADER);
        assertEquals(TEST_VALUE, header.getValue());
    }

    /**
     * Test that attempt to use colon in header names correctly fails.
     */
    @Test
    public void testColonInHeaderName() {
        testBadHeaderName("X:Header", "WFLYCTL0457");
    }

    /**
     * Test that attempt to use space in header names correctly fails.
     */
    @Test
    public void testSpaceInHeaderName() {
        testBadHeaderName("X Header", "WFLYCTL0457");
    }

    /**
     * Test that attempt to use new line in header names correctly fails.
     */
    @Test
    public void testNewLineInHeaderName() {
        testBadHeaderName("X\nHeader", "WFLYCTL0457");
        testBadHeaderName("X\nHeader\n", "WFLYCTL0457");
    }

    /**
     * Test that attempt to use disallowed header name 'Connection' correctly fails.
     */
    @Test
    public void testConnectionHeader() {
        testBadHeaderName("Connection", "WFLYCTL0458");
    }

    /**
     * Test that attempt to use disallowed header name 'Date' correctly fails.
     */
    @Test
    public void testDateHeader() {
        testBadHeaderName("Date", "WFLYCTL0458");
    }

    /**
     * Test that attempt to use disallowed header name 'Content-Length' correctly fails.
     */
    @Test
    public void testContentLengthHeader() {
        testBadHeaderName("Content-Length", "WFLYCTL0458");
    }

    /**
     * Test that attempt to use disallowed header name 'Content-Type' correctly fails.
     */
    @Test
    public void testContentTypeHeader() {
        testBadHeaderName("Content-Type", "WFLYCTL0458");
    }

    /**
     * Test that attempt to use disallowed header name 'Transfer-Encoding' correctly fails.
     */
    @Test
    public void testTransferEncodingHeader() {
        testBadHeaderName("Transfer-Encoding", "WFLYCTL0458");
    }

    private void testBadHeaderName(String headerName, String errorCode) {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();
        headersMap.put(ROOT_CTX, Collections.singletonList(Collections.singletonMap(headerName, TEST_VALUE)));
        try {
            managementClient.executeForResult(createConstantHeadersOperation(headersMap));
            fail("Operation was expected to fail.");
        } catch (UnsuccessfulOperationException e) {
            assertTrue(e.getMessage().contains(errorCode));
        }
    }

    private static void validateExpectedValues(Header[] headers, String... values) {
        Collection<String> expectedValues = new ArrayList<>(Arrays.asList(values));
        assertEquals("Header Count", values.length, headers.length);
        for (Header header : headers) {
            assertTrue("Unique header value.", expectedValues.remove(header.getValue()));
        }
        assertTrue("All expected values received", expectedValues.isEmpty());
    }

}
