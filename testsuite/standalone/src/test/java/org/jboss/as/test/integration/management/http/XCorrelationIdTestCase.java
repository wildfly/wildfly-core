/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.test.integration.management.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;

import javax.inject.Inject;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.http.Authentication;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Tests X-Correlation-Id header handling.
 *
 * @author Brian Stansberry
 */
@RunWith(WildflyTestRunner.class)
public class XCorrelationIdTestCase {

    private static final int MGMT_PORT = 9990;
    private static final String MGMT_CTX = "/management";
    private static final String APPLICATION_JSON = "application/json";
    private static final String HEADER = "X-Correlation-Id";
    private static final String VALUE1 = "WFCORE-972";
    private static final String VALUE2 = "CHANGED";

    @Inject
    protected ManagementClient managementClient;

    private URL url;
    private HttpClient httpClient;
    private HttpContext httpContext;

    @Before
    public void before() throws Exception {

        this.url = new URL("http", managementClient.getMgmtAddress(), MGMT_PORT, MGMT_CTX);
        this.httpContext = new BasicHttpContext();
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(url.getHost(), url.getPort()), new UsernamePasswordCredentials(Authentication.USERNAME, Authentication.PASSWORD));

        this.httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();
    }

    @After
    public void after() {
        if (httpClient instanceof Closeable) {
            try {
                ((Closeable) httpClient).close();
            } catch (IOException e) {
                Logger.getLogger(XCorrelationIdTestCase.class).error("Failed closing client", e);
            }
        }
    }

    @Test
    public void testGet() throws Exception {
        HttpGet get = new HttpGet(url.toURI().toString() + "?operation=attribute&name=server-state");

        assertNull(getCorrelationHeader(get));

        get.addHeader(HEADER, VALUE1);
        assertEquals(VALUE1, getCorrelationHeader(get));

        get.setHeader(HEADER, VALUE2);
        assertEquals(VALUE2, getCorrelationHeader(get));

        get.setHeader(HEADER, VALUE1);
        assertEquals(VALUE1, getCorrelationHeader(get));

        get.removeHeaders(HEADER);
        assertNull(getCorrelationHeader(get));
    }

    @Test
    public void testPost() throws Exception {
        ModelNode op = Util.getReadAttributeOperation(PathAddress.EMPTY_ADDRESS, "server-state");
        HttpPost post = new HttpPost(url.toURI());

        String cmdStr = op.toJSONString(true);
        StringEntity entity = new StringEntity(cmdStr);
        entity.setContentType(APPLICATION_JSON);
        post.setEntity(entity);

        assertNull(getCorrelationHeader(post));

        post.addHeader(HEADER, VALUE1);
        assertEquals(VALUE1, getCorrelationHeader(post));

        post.setHeader(HEADER, VALUE2);
        assertEquals(VALUE2, getCorrelationHeader(post));

        post.setHeader(HEADER, VALUE1);
        assertEquals(VALUE1, getCorrelationHeader(post));

        post.removeHeaders(HEADER);
        assertNull(getCorrelationHeader(post));
    }

    private String getCorrelationHeader(HttpUriRequest request) throws IOException {
        HttpResponse response = httpClient.execute(request, httpContext);
        assertTrue(response.getStatusLine().getStatusCode() == 200);
        assertTrue(EntityUtils.toString(response.getEntity()).contains("running"));
        Header header = response.getFirstHeader(HEADER);
        return header == null ? null : header.getValue();
    }
}
