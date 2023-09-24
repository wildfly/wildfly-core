/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt.api.core;

import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

import java.io.IOException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.standalone.base.ContainerResourceMgmtTestBase;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test for functionality added with AS7-2139.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@RunWith(WildFlyRunner.class)
public class ResolveExpressionTestCase extends ContainerResourceMgmtTestBase {

    @Test
    public void testResolveExpression() throws Exception {
        ModelNode op = createOpNode(null, "resolve-expression");
        op.get("expression").set("${file.separator}");

        Assert.assertEquals(System.getProperty("file.separator"), executeOperation(op).asString());
    }

    @Test
    public void testNonExpression() throws Exception {
        ModelNode op = createOpNode(null, "resolve-expression");
        op.get("expression").set("hello");

        Assert.assertEquals("hello", executeOperation(op).asString());
    }

    @Test
    public void testUndefined() throws Exception {
        ModelNode op = createOpNode(null, "resolve-expression");

        Assert.assertFalse(executeOperation(op).isDefined());

        op.get("expression");

        Assert.assertFalse(executeOperation(op).isDefined());
    }

    @Test
    public void testUnresolvableExpression() throws Exception {
        ModelNode op = createOpNode(null, "resolve-expression");
        op.get("expression").set("${unresolvable}");

        ModelNode response = executeOperation(op, false);
        Assert.assertFalse("Management operation " + op.asString() + " succeeded: " + response.toString(),
                "success".equals(response.get("outcome").asString()));
    }

    @Test
    public void testNestedExpression() throws Exception {

        try {
            setupNestingProperties();
            ModelNode op = createOpNode(null, "resolve-expression");
            op.get("expression").set("${${A}b}");
        } finally {
            clearNestingProperties();
        }
    }

    private void clearNestingProperties() throws Exception {

        Exception ex = null;

        for (String value : new String[]{"A", "B", "ab"}) {
            ModelNode op = Util.createEmptyOperation("remove", PathAddress.pathAddress("system-property", value));
            try {
                executeOperation(op);
            } catch (IOException e1) {
                if (ex == null) {
                    ex = e1;
                }
            } catch (MgmtOperationException e1) {
                if (ex == null) {
                    ex = e1;
                }
            }
        }

        if (ex != null) {
            throw ex;
        }
    }

    private void setupNestingProperties() throws IOException, MgmtOperationException {
        ModelNode opA = Util.createAddOperation(PathAddress.pathAddress("system-property", "A"));
        opA.get("value").set("a");
        executeOperation(opA);
        ModelNode opB = Util.createAddOperation(PathAddress.pathAddress("system-property", "B"));
        opA.get("value").set("b");
        executeOperation(opB);
        ModelNode opab = Util.createAddOperation(PathAddress.pathAddress("system-property", "ab"));
        opA.get("value").set("asd");
        executeOperation(opab);
    }
}
