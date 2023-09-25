/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.HttpURLConnection;
import java.net.URL;

import jakarta.inject.Inject;

import org.apache.http.client.methods.HttpGet;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Since console is not loaded in core dist, in both modes the server is supposed to return 404.
 *
 * @author Tomas Hofman (thofman@redhat.com)
 */
@RunWith(WildFlyRunner.class)
public class WebConsoleRedirectionTestCase {

    @SuppressWarnings("unused")
    @Inject
    private static ManagementClient managementClient;

    @Test
    public void testRedirectionInAdminMode() throws Exception {
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient(), true);
        try {
            final HttpURLConnection connection = getConnection();
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, connection.getResponseCode());
        } finally {
            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient(), false);
        }
    }

    @Test
    public void testRedirectionInNormalMode() throws Exception {
        final HttpURLConnection connection = getConnection();
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, connection.getResponseCode());
    }

    private HttpURLConnection getConnection() throws Exception {
        final URL url = new URL("http://" + TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort() + "/");
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertNotNull(connection);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(HttpGet.METHOD_NAME);
        connection.connect();
        return connection;
    }

}
