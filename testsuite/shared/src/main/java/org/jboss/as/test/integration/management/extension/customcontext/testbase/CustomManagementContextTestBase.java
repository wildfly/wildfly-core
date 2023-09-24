/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.extension.customcontext.testbase;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.http.Authentication;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.customcontext.CustomContextExtension;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.core.testrunner.ManagementClient;

/**
 * Base class for tests of integrating a custom management context on the http interface.
 * We install an extension that does this kind of integration and see the result.
 *
 * @author Brian Stansberry
 */
public abstract class CustomManagementContextTestBase {

    protected static final PathAddress EXT = PathAddress.pathAddress(EXTENSION, CustomContextExtension.EXTENSION_NAME);

    protected static final PathElement SUB = PathElement.pathElement(SUBSYSTEM, CustomContextExtension.SUBSYSTEM_NAME);

    @BeforeClass
    public static void setup() throws IOException {
        ExtensionUtils.createExtensionModule(CustomContextExtension.EXTENSION_NAME, CustomContextExtension.class,
                EmptySubsystemParser.class.getPackage());
    }

    @After
    public void teardown() throws IOException {

        IOException ioe = null;
        AssertionError ae = null;
        RuntimeException re = null;
        ManagementClient managementClient = getManagementClient();
        PathAddress[] cleanUp = {getSubsystemAddress(), getExtensionAddress()};
        for (int i = 0; i < cleanUp.length; i++) {
            try {
                ModelNode op = Util.createRemoveOperation(cleanUp[i]);
                op.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);
                executeOp(op, managementClient);
            } catch (IOException e) {
                if (ioe == null) {
                    ioe = e;
                }
            } catch (AssertionError e) {
                if (i > 0 && ae == null) {
                    ae = e;
                } // else ignore because in a failed test SUB may not exist, causing remove to fail
            } catch (RuntimeException e) {
                if (re == null) {
                    re = e;
                }
            }
        }

        if (ioe != null) {
            throw ioe;
        }

        if (ae != null) {
            throw ae;
        }

        if (re != null) {
            throw re;
        }

    }

    @AfterClass
    public static void cleanupExtension() {
        //if (true) return;
        ExtensionUtils.deleteExtensionModule(CustomContextExtension.EXTENSION_NAME);
    }

    protected abstract PathAddress getExtensionAddress();
    protected abstract PathAddress getSubsystemAddress();

    protected abstract ManagementClient getManagementClient();

    @Test
    public void test() throws IOException {
        test(getManagementClient());
    }

    private void test(final ManagementClient managementClient) throws IOException {
        //if (true) return;

        final String urlBase = "http://" + managementClient.getMgmtAddress() + ":9990/";
        final String remapUrl = urlBase + "remap/foo";
        final String badRemapUrl = urlBase + "remap/bad";
        final String staticUrl = urlBase + "static/hello.txt";
        final String staticUrlDirectory = urlBase + "static/";
        final String badStaticUrl = urlBase + "static/bad.txt";
        final String errorUrl = urlBase + "error/index.html";
        final String securedDynamicUrl = urlBase + "secured-dynamic/";
        final String unsecuredDynamicUrl = urlBase + "unsecured-dynamic/";

        // Sanity check

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            CloseableHttpResponse resp = client.execute(new HttpGet(remapUrl));
            assertEquals(404, resp.getStatusLine().getStatusCode());
            resp.close();

            resp = client.execute(new HttpGet(staticUrl));
            assertEquals(404, resp.getStatusLine().getStatusCode());
            resp.close();

            resp = client.execute(new HttpGet(securedDynamicUrl));
            assertEquals(404, resp.getStatusLine().getStatusCode());
            resp.close();

            resp = client.execute(new HttpGet(unsecuredDynamicUrl));
            assertEquals(404, resp.getStatusLine().getStatusCode());
            resp.close();
        }

        // Add extension and subsystem

        executeOp(Util.createAddOperation(getExtensionAddress()), managementClient);
        executeOp(Util.createAddOperation(getSubsystemAddress()), managementClient);

        // Unauthenticated check

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            CloseableHttpResponse resp = client.execute(new HttpGet(remapUrl));
            assertEquals(401, resp.getStatusLine().getStatusCode());
            resp.close();
            resp = client.execute(new HttpGet(staticUrl));
            assertEquals(200, resp.getStatusLine().getStatusCode());
            resp.close();
            // secured handler must return 401 Unauthorized
            resp = client.execute(new HttpGet(securedDynamicUrl));
            System.out.println("resp = " + resp);
            assertEquals(401, resp.getStatusLine().getStatusCode());
            resp.close();
            resp = client.execute(new HttpGet(unsecuredDynamicUrl));
            assertEquals(200, resp.getStatusLine().getStatusCode());
            resp.close();

            // POST check for unsecured  dynamic URL
            final String newValue = "MY NEW VALUE";
            final HttpPost postDynamic = new HttpPost(unsecuredDynamicUrl);
            postDynamic.setEntity(new StringEntity(newValue));
            resp = client.execute(postDynamic);
            assertEquals(204, resp.getStatusLine().getStatusCode());
            resp.close();
            resp = client.execute(new HttpGet(unsecuredDynamicUrl));
            assertEquals(200, resp.getStatusLine().getStatusCode());
            String text = EntityUtils.toString(resp.getEntity());
            assertEquals(newValue, text);
            resp.close();
        }

        try (CloseableHttpClient client = createAuthenticatingClient(managementClient)) {

            // Authenticated check

            CloseableHttpResponse resp = client.execute(new HttpGet(remapUrl));
            assertEquals(200, resp.getStatusLine().getStatusCode());
            ModelNode respNode = ModelNode.fromJSONString(EntityUtils.toString(resp.getEntity()));
            assertEquals(respNode.toString(), CustomContextExtension.EXTENSION_NAME, respNode.get("module").asString());
            assertFalse(respNode.toString(), respNode.hasDefined("subsystem"));
            resp.close();

            resp = client.execute(new HttpGet(staticUrl));
            assertEquals(200, resp.getStatusLine().getStatusCode());
            String text = EntityUtils.toString(resp.getEntity());
            assertTrue(text, text.startsWith("Hello"));

            // the response should contain headers:
            // X-Frame-Options: SAMEORIGIN
            // Cache-Control: public, max-age=2678400
            final Map<String, String> headersMap = new HashMap<>();
            for (Header header : resp.getAllHeaders()) {
                if (headersMap.put(header.getName(), header.getValue()) != null) {
                    throw new IllegalStateException("Duplicate key");
                }
            }
            Assert.assertTrue("'X-Frame-Options: SAMEORIGIN' header is expected",
                    headersMap.getOrDefault("X-Frame-Options", "").contains("SAMEORIGIN"));
            Assert.assertTrue("Cache-Control header with max-age=2678400 is expected,",
                    headersMap.getOrDefault("Cache-Control", "").contains("max-age=2678400"));
            resp.close();

            // directory listing is not allowed
            resp = client.execute(new HttpGet(staticUrlDirectory));
            assertEquals(404, resp.getStatusLine().getStatusCode());
            resp.close();

            // POST check
            resp = client.execute(new HttpPost(remapUrl));
            assertEquals(405, resp.getStatusLine().getStatusCode());
            resp.close();
            resp = client.execute(new HttpPost(staticUrl));
            assertEquals(200, resp.getStatusLine().getStatusCode());
            resp.close();

            // POST check for secured dynamic URL
            final String newValue = "MY NEW VALUE";
            final HttpPost postDynamic = new HttpPost(securedDynamicUrl);
            postDynamic.setEntity(new StringEntity(newValue));
            resp = client.execute(postDynamic);
            assertEquals(204, resp.getStatusLine().getStatusCode());
            resp.close();
            resp = client.execute(new HttpGet(securedDynamicUrl));
            assertEquals(200, resp.getStatusLine().getStatusCode());
            text = EntityUtils.toString(resp.getEntity());
            assertEquals(newValue, text);

            // Bad URL check

            resp = client.execute(new HttpGet(badRemapUrl));
            assertEquals(404, resp.getStatusLine().getStatusCode());
            resp.close();
            resp = client.execute(new HttpGet(badStaticUrl));
            assertEquals(404, resp.getStatusLine().getStatusCode());
            resp.close();

            // Remove subsystem

            executeOp(Util.createRemoveOperation(getSubsystemAddress()), managementClient);

            // Requests fail

            resp = client.execute(new HttpGet(remapUrl));
            assertEquals(404, resp.getStatusLine().getStatusCode());
            resp.close();
            resp = client.execute(new HttpGet(staticUrl));
            assertEquals(404, resp.getStatusLine().getStatusCode());
            resp.close();
            resp = client.execute(new HttpGet(securedDynamicUrl));
            assertEquals(404, resp.getStatusLine().getStatusCode());
            resp.close();
            resp = client.execute(new HttpGet(unsecuredDynamicUrl));
            assertEquals(404, resp.getStatusLine().getStatusCode());
            resp.close();

            // Error Context
            resp = client.execute(new HttpGet(errorUrl));
            // X-Frame-Options: SAMEORIGIN
            final Map<String, String> errorContextHeadersMap = new HashMap<>();
            for (Header header : resp.getAllHeaders()) {
                if (errorContextHeadersMap.put(header.getName(), header.getValue()) != null) {
                    throw new IllegalStateException("Duplicate key");
                }
            }
            Assert.assertTrue("'X-Frame-Options: SAMEORIGIN' error context header is expected as well",
                              errorContextHeadersMap.getOrDefault("X-Frame-Options", "").contains("SAMEORIGIN"));
            resp.close();
        }
    }

    private static CloseableHttpClient createAuthenticatingClient(ManagementClient managementClient) {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(managementClient.getMgmtAddress(), 9990),
                new UsernamePasswordCredentials(Authentication.USERNAME, Authentication.PASSWORD));

        return HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setMaxConnPerRoute(10)
                .build();
    }

    private static ModelNode executeOp(ModelNode op, ManagementClient managementClient) throws IOException {
        ModelNode response = managementClient.getControllerClient().execute(op);
        assertTrue(response.toString(), response.hasDefined(OUTCOME));
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        return response;
    }
}
