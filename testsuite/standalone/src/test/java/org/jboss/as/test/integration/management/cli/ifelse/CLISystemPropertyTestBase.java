/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli.ifelse;

import java.io.ByteArrayOutputStream;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

/**
 *
 * @author Alexey Loubyansky
 */
public class CLISystemPropertyTestBase {

    private static final String RESPONSE_VALUE_PREFIX = "\"value\" => ";
    private static final String PROP_NAME = "jboss-cli-test";
    protected static ByteArrayOutputStream cliOut;

    @BeforeClass
    public static void setup() throws Exception {
        cliOut = new ByteArrayOutputStream();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        cliOut = null;
    }

    protected String getAddPropertyReqWithoutValue() {
        return "/system-property=" + PROP_NAME + ":add()";
    }

    protected String getAddPropertyReq(String value) {
        return getAddPropertyReq(PROP_NAME, value);
    }

    protected String getAddPropertyReq(String name, String value) {
        return "/system-property=" + name + ":add(value=" + value + ")";
    }

    protected String getWritePropertyReq(String value) {
        return getWritePropertyReq(PROP_NAME, value);
    }

    protected String getWritePropertyReq(String name, String value) {
        return "/system-property=" + name + ":write-attribute(name=\"value\",value=" + value + ")";
    }

    protected String getReadPropertyReq() {
        return getReadPropertyReq(PROP_NAME);
    }

    protected String getReadPropertyReq(String name) {
        return "/system-property=" + name + ":read-resource";
    }

    protected String getReadNonexistingPropReq() {
        return "/system-property=itcantexist:read-resource";
    }

    protected String getRemovePropertyReq() {
        return getRemovePropertyReq(PROP_NAME);
    }

    protected String getRemovePropertyReq(String name) {
        return "/system-property=" + name + ":remove";
    }

    protected String getValue() {
        final String response = cliOut.toString();
        int start = response.indexOf(RESPONSE_VALUE_PREFIX);
        if(start < 0) {
            Assert.fail("Value not found in the response: " + response);
        }
        start += RESPONSE_VALUE_PREFIX.length();
        if(response.charAt(start) == '\"') {
            final int end = response.indexOf('"', start + 2);
            if(end < 0) {
                Assert.fail("Couldn't locate the closing quote: " + response);
            }
            return response.substring(start + 1, end);
        } else if(response.startsWith("undefined", start)){
            return "undefined";
        }
        Assert.fail("Value not found in the response: " + response);
        throw new IllegalStateException();
    }
}