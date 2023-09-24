/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.perimeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpTrace;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.logging.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test verifies that the web management console is secured
 *
 * @author jlanik@redhat.com
 */

@RunWith(WildFlyRunner.class)
public class WebConsoleSecurityTestCase {


    private static final Logger log = Logger.getLogger(WebConsoleSecurityTestCase.class);

    private HttpURLConnection getConnection() throws Exception {
        final URL url = new URL("http://" + TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort() + "/management");
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertNotNull(connection);
        log.debug("connection opened");
        connection.setDoInput(true);
        connection.setRequestProperty("Cookie", "MODIFY ME IF NEEDED");
        return connection;
    }

    @Test
    public void testGet() throws Exception {
        final HttpURLConnection connection = getConnection();
        connection.setRequestMethod(HttpGet.METHOD_NAME);
        connection.connect();
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, connection.getResponseCode());
    }

    @Test
    public void testPost() throws Exception {
        final HttpURLConnection connection = getConnection();
        connection.setRequestMethod(HttpPost.METHOD_NAME);
        connection.connect();
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, connection.getResponseCode());
    }

    @Test
    public void testHead() throws Exception {
        final HttpURLConnection connection = getConnection();
        connection.setRequestMethod(HttpHead.METHOD_NAME);
        connection.connect();
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, connection.getResponseCode());
    }

    @Test
    public void testOptions() throws Exception {
        final HttpURLConnection connection = getConnection();
        connection.setRequestMethod(HttpOptions.METHOD_NAME);
        connection.connect();
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, connection.getResponseCode());
    }

    @Test
    public void testPut() throws Exception {
        final HttpURLConnection connection = getConnection();
        connection.setRequestMethod(HttpPut.METHOD_NAME);
        connection.connect();
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, connection.getResponseCode());
    }

    @Test
    public void testTrace() throws Exception {
        final HttpURLConnection connection = getConnection();
        connection.setRequestMethod(HttpTrace.METHOD_NAME);
        connection.connect();
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, connection.getResponseCode());
    }

    @Test
    public void testDelete() throws Exception {
        final HttpURLConnection connection = getConnection();
        connection.setRequestMethod(HttpDelete.METHOD_NAME);
        connection.connect();
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, connection.getResponseCode());
    }

}
