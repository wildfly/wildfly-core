/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;


/**
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
public class CommandSubstitutionTestCase {

    private static final String ADD = "add";
    private static final String NAME = "name";
    private static final String NODE = "node";
    private static final String READ_ATTRIBUTE = "read-attribute";
    private static final String SYSTEM_PROPERTY = "system-property";
    private static final String TEST = "prop_test";
    private static final String TEST2 = "prop_test2";
    private static final String VALUE = "value";

    private CommandContext ctx;

    @Before
    public void setup() throws Exception {
        ctx = CLITestUtil.getCommandContext();
        ctx.connectController();
        setProperty(ADD, ADD);
        setProperty(NAME, NAME);
        setProperty(NODE, NODE);
        setProperty(READ_ATTRIBUTE, READ_ATTRIBUTE);
        setProperty(SYSTEM_PROPERTY, SYSTEM_PROPERTY);
        setProperty(VALUE, VALUE);
    }

    @After
    public void tearDown() throws Exception {
        try {
            safeRemove(ADD);
            safeRemove(NAME);
            safeRemove(NODE);
            safeRemove(READ_ATTRIBUTE);
            safeRemove(SYSTEM_PROPERTY);
            safeRemove(TEST);
            safeRemove(TEST2);
            safeRemove(VALUE);
            ctx.setVariable("a", null);
            ctx.setVariable("b", null);
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testOperation() throws Exception {
        final StringBuilder buf = new StringBuilder();
        buf.append('/')
           .append(opSubstitution(SYSTEM_PROPERTY)).append('=').append(TEST).append(':')
           .append(cmdSubstitution(ADD)).append('(').append(opSubstitution(VALUE)).append('=').append("1").append(')');
        ctx.handle(buf.toString());
        assertEquals("1", readProperty(TEST));

        // Same substitution but this time with implicit values.
        buf.setLength(0);
        buf.append('/')
                .append(opSubstitution(SYSTEM_PROPERTY, true)).append('=').append(TEST2).append(':')
                .append(cmdSubstitution(ADD)).append('(').append(opSubstitution(VALUE, true)).append('=').append("1").append(')');
        ctx.handle(buf.toString());
        assertEquals("1", readProperty(TEST2));

        buf.setLength(0);
        buf.append(opSubstitution(READ_ATTRIBUTE))
           .append(" --").append(cmdSubstitution(NODE)).append('=')
           .append(opSubstitution(SYSTEM_PROPERTY)).append('=').append(TEST).append(' ')
           .append(cmdSubstitution(VALUE));
        assertEquals("1", executeReadProperty(buf.toString()));
    }

    @Test
    public void setVariableValues() throws Exception {
        assertNull(ctx.getVariable("a"));
        assertNull(ctx.getVariable("b"));
        ctx.handle("set a=" + opSubstitution(ADD) + " b=" + cmdSubstitution(NAME));
        assertEquals(ADD, ctx.getVariable("a"));
        assertEquals(NAME, ctx.getVariable("b"));
    }

    private String opSubstitution(String prop) {
        return opSubstitution(prop, false);
    }

    private String opSubstitution(String prop, boolean resolve) {
        return resolve ? "`/system-property=" + prop + ":read-attribute(name=value, resolve-expressions)`"
                : "`/system-property=" + prop + ":read-attribute(name=value)`";
    }

    private String cmdSubstitution(String prop) {
        return "`read-attribute --node=system-property=" + prop + " value`";
    }

    protected void setProperty(String prop, String value) throws Exception {
        ctx.handle("/system-property=" + prop + ":add(value=" + value + ")");
    }

    protected void safeRemove(String prop) {
        ctx.handleSafe("/system-property=" + prop + ":remove");
    }

    protected String readProperty(final String prop) throws Exception {
        String operation = "/system-property=" + prop + ":read-attribute(name=value)";
        return executeReadProperty(operation);
    }

    private String executeReadProperty(String operation) throws Exception {
        final ModelNode request = ctx.buildRequest(operation);
        final ModelControllerClient client = ctx.getModelControllerClient();
        final ModelNode response = client.execute(request);
        return response.get("result").asString();
    }
}
