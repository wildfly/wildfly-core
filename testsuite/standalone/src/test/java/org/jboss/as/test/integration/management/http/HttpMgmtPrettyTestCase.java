/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.http;

import java.net.URL;
import jakarta.inject.Inject;

import org.jboss.as.test.integration.management.util.HttpMgmtProxy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests all management operation types which are available via HTTP GET requests.
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
@RunWith(WildFlyRunner.class)
public class HttpMgmtPrettyTestCase {

    private static final int MGMT_PORT = 9990;
    private static final String MGMT_CTX = "/management";
    private HttpMgmtProxy httpMgmt;

    @Inject
    protected ManagementClient managementClient;

    @Before
    public void before() throws Exception {
        URL mgmtURL = new URL("http", managementClient.getMgmtAddress(), MGMT_PORT, MGMT_CTX);
        httpMgmt = new HttpMgmtProxy(mgmtURL);
    }

    @Test
    public void testPrettyDefaultNo() throws Exception {
        checkServerJson(null, false);
    }

    @Test
    public void testPrettyFalse() throws Exception {
        checkServerJson("json.pretty=false", false);
    }

    @Test
    public void testPrettyZero() throws Exception {
        checkServerJson("json.pretty=0", false);
    }

    @Test
    public void testPrettyTrue() throws Exception {
        checkServerJson("json.pretty=true", true);
    }

    @Test
    public void testPrettyOne() throws Exception {
        checkServerJson("json.pretty=1", true);
    }

    private void checkServerJson(String queryString, boolean pretty) throws Exception {
        final String query = queryString == null ? "null" : "?" + queryString;
        String reply = httpMgmt.sendGetCommandJson(query);
        if (pretty) {
            Assert.assertTrue(reply.contains("\n"));
        } else {
            Assert.assertFalse(reply.contains("\n"));
        }
    }
}