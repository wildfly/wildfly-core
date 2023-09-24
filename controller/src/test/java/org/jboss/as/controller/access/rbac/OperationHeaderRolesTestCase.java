/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.rbac;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests of {@link RunAsRoleMapper#getOperationHeaderRoles(org.jboss.dmr.ModelNode)}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class OperationHeaderRolesTestCase {

    private static final String A = "a";
    private static final String B = "b";
    private static final String YUCK = "yuck";
    private static final String ICK = "ick";
    private static final String WEIRD = "yuck,ick";

    /** Simple {@code ModelType.STRING} header with the string naming a single role */
    @Test
    public void testSimpleString() {
        operationHeaderRolesTest(new ModelNode(A), Collections.singleton(A));
    }

    /** {@code ModelType.LIST} header with the list elements each naming a single role */
    @Test
    public void testDMRList() {
        ModelNode header = new ModelNode();
        header.add(A);
        header.add(B);
        operationHeaderRolesTest(header, new HashSet<String>(Arrays.asList(A, B)));
    }

    /** {@code ModelType.LIST} header with the list elements each naming a single role even though one element has a comma */
    @Test
    public void testDMRListWithCommas() {
        ModelNode header = new ModelNode();
        header.add(WEIRD);
        header.add(B);
        operationHeaderRolesTest(header, new HashSet<String>(Arrays.asList(WEIRD, B)));
    }

    /** {@code ModelType.STRING} header with a comma in the string, expected to produce 2 roles */
    @Test
    public void testBasicStringList() {
        operationHeaderRolesTest(new ModelNode(" " + A + " , " + B + " "), new HashSet<String>(Arrays.asList(A, B)));
    }

    /** {@code ModelType.STRING} header with 2 commas in the string, expected to produce 3 roles */
    @Test
    public void testBasicStringListWithCommas() {
        // Here we expect "yuck,ick" to parse into 2 roles
        operationHeaderRolesTest(new ModelNode(" " + WEIRD + " , " + B + " "),
                new HashSet<String>(Arrays.asList(YUCK, ICK, B)));
    }

    /**
     * Same as {@link #testDMRList()} except we call {@link org.jboss.dmr.ModelNode#toString()} on the header and
     * pass that output in as a {@code ModelType.STRING} node. Same result expected anyway.
     */
    @Test
    public void testParseStringToDMRList() {
        ModelNode header = new ModelNode();
        header.add(A);
        header.add(B);
        operationHeaderRolesTest(new ModelNode(header.toString()), new HashSet<String>(Arrays.asList(A, B)));
    }

    /**
     * Same as {@link #testDMRListWithCommas()} except we call {@link org.jboss.dmr.ModelNode#toString()} on the header and
     * pass that output in as a {@code ModelType.STRING} node. Same result expected anyway.
     */
    @Test
    public void testParseStringToDMRListWithCommas() {
        ModelNode header = new ModelNode();
        header.add(WEIRD);
        header.add(B);
        // Here we expect "yuck,ick" to parse into a single role
        operationHeaderRolesTest(new ModelNode(header.toString()), new HashSet<String>(Arrays.asList(WEIRD, B)));
    }

    /**
     * Same as {@link #testBasicStringList()} except we wrap the list with [] the way people do in the
     * simplified syntax supported by the CLI
     */
    @Test
    public void testCLIStringList() {
        operationHeaderRolesTest(new ModelNode(" [ " + A + " , " + B + " ] "), new HashSet<String>(Arrays.asList(A, B)));
    }

    /**
     * Same as {@link #testBasicStringListWithCommas()} except we wrap the list with [] the way people do in the
     * simplified syntax supported by the CLI
     */
    @Test
    public void testCLIStringListWithCommas() {
        // Here we expect "yuck,ick" to parse into 2 roles
        operationHeaderRolesTest(new ModelNode(" [ " + WEIRD + " , " + B + " ] "),
                new HashSet<String>(Arrays.asList(YUCK, ICK, B)));
    }

    private void operationHeaderRolesTest(ModelNode header, Set<String> expectedOutput) {
        ModelNode op = new ModelNode();
        op.get(ModelDescriptionConstants.OPERATION_HEADERS, ModelDescriptionConstants.ROLES).set(header);
        Assert.assertEquals(expectedOutput, RunAsRoleMapper.getOperationHeaderRoles(op));
    }
}
