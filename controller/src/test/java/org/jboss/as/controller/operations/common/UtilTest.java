/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.controller.operations.common;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.hamcrest.MatcherAssert.assertThat;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class UtilTest {

    public UtilTest() {
    }

    /**
     * Test of getNameFromAddress method, of class Util.
     */
    @Test
    public void testGetNameFromAddress_ModelNode() {
        ModelNode address = new ModelNode();
        assertThat(Util.getNameFromAddress(address), is(nullValue()));
        address = new ModelNode().get("subsystem");
        assertThat(Util.getNameFromAddress(address), is(nullValue()));
        address = new ModelNode().add("subsystem", "test");
        assertThat(Util.getNameFromAddress(address), is("test"));
    }

    /**
     * Test of getNameFromAddress method, of class Util.
     */
    @Test
    public void testGetNameFromAddress_PathAddress() {
        System.out.println("getNameFromAddress");
        PathAddress address = PathAddress.EMPTY_ADDRESS;
        assertThat(Util.getNameFromAddress(address), is(nullValue()));
        address = PathAddress.pathAddress(PathElement.pathElement("subsystem"));
        assertThat(Util.getNameFromAddress(address), is(PathElement.WILDCARD_VALUE));
        address = PathAddress.pathAddress("subsystem", "test");
        assertThat(Util.getNameFromAddress(address), is("test"));
    }

    /**
     * Test of createAddOperation method, of class Util.
     */
    @Test
    public void testCreateAddOperation_PathAddress() {
        PathAddress address = PathAddress.pathAddress("subsystem", "test");
        ModelNode operation = Util.createAddOperation(address);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(ADD));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address.toModelNode()));
    }

    /**
     * Test of createAddOperation method, of class Util.
     */
    @Test
    public void testCreateAddOperation() {
        ModelNode operation = Util.createAddOperation();
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(ADD));
        assertThat(operation.hasDefined(OP_ADDR), is(false));
    }

    /**
     * Test of createRemoveOperation method, of class Util.
     */
    @Test
    public void testCreateRemoveOperation() {
        PathAddress address = PathAddress.pathAddress("subsystem", "test");
        ModelNode operation = Util.createRemoveOperation(address);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(REMOVE));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address.toModelNode()));
    }

    /**
     * Test of createOperation method, of class Util.
     */
    @Test
    public void testCreateOperationWithNamePathAddress() {
        String operationName = "fake";
        PathAddress address = PathAddress.pathAddress("subsystem", "test");
        ModelNode operation = Util.createOperation(operationName, address);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(operationName));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address.toModelNode()));
    }

    /**
     * Test of createEmptyOperation method, of class Util.
     */
    @Test
    public void testCreateEmptyOperation() {
        String operationName = "fake";
        PathAddress address = PathAddress.pathAddress("subsystem", "test");
        ModelNode operation = Util.createEmptyOperation(operationName, address);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(operationName));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address.toModelNode()));
        operation = Util.createEmptyOperation(operationName, null);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(operationName));
        assertThat(operation.hasDefined(OP_ADDR), is(false));
    }

    /**
     * Test of getEmptyOperation method, of class Util.
     */
    @Test
    public void testGetEmptyOperation() {
        String operationName = "fake";
        ModelNode address = new ModelNode().add("subsystem", "test");
        ModelNode operation = Util.getEmptyOperation(operationName, address);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(operationName));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address));
        operation = Util.getEmptyOperation(operationName, null);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(operationName));
        assertThat(operation.hasDefined(OP_ADDR), is(false));
    }

    /**
     * Test of getResourceRemoveOperation method, of class Util.
     */
    @Test
    public void testGetResourceRemoveOperation() {
        PathAddress address = PathAddress.pathAddress("subsystem", "test");
        ModelNode operation = Util.getResourceRemoveOperation(address);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(REMOVE));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address.toModelNode()));
        operation = Util.getResourceRemoveOperation(null);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(REMOVE));
        assertThat(operation.hasDefined(OP_ADDR), is(false));
    }

    /**
     * Test of getWriteAttributeOperation method, of class Util.
     */
    @Test
    public void testGetWriteStringAttributeOperation() {
        ModelNode address = new ModelNode().add("subsystem", "test");
        String attributeName = "attr";
        String value = "testValue";
        ModelNode operation = Util.getWriteAttributeOperation(address, attributeName, value);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address));
        assertThat(operation.hasDefined(NAME), is(true));
        assertThat(operation.get(NAME).asString(), is(attributeName));
        assertThat(operation.hasDefined(VALUE), is(true));
        assertThat(operation.get(VALUE).asString(), is(value));
    }

    /**
     * Test of getWriteAttributeOperation method, of class Util.
     */
    @Test
    public void testGetWriteIntegerAttributeOperation() {
        PathAddress address = PathAddress.pathAddress("subsystem", "test");
        String attributeName = "attr";
        int value = 107;
        ModelNode operation = Util.getWriteAttributeOperation(address, attributeName, value);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address.toModelNode()));
        assertThat(operation.hasDefined(NAME), is(true));
        assertThat(operation.get(NAME).asString(), is(attributeName));
        assertThat(operation.hasDefined(VALUE), is(true));
        assertThat(operation.get(VALUE).asInt(), is(value));
    }

    /**
     * Test of getWriteAttributeOperation method, of class Util.
     */
    @Test
    public void testGetWriteBooleanAttributeOperation_3args_3() {
        PathAddress address = PathAddress.pathAddress("subsystem", "test");
        String attributeName = "attr";
        boolean value = true;
        ModelNode operation = Util.getWriteAttributeOperation(address, attributeName, value);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address.toModelNode()));
        assertThat(operation.hasDefined(NAME), is(true));
        assertThat(operation.get(NAME).asString(), is(attributeName));
        assertThat(operation.hasDefined(VALUE), is(true));
        assertThat(operation.get(VALUE).asBoolean(), is(value));
    }

    /**
     * Test of getWriteAttributeOperation method, of class Util.
     */
    @Test
    public void testGetWriteModelNodeAttributeOperation() {
        PathAddress address = PathAddress.pathAddress("subsystem", "test");
        String attributeName = "attr";
        ModelNode value = new ModelNode("testValue");
        ModelNode operation = Util.getWriteAttributeOperation(address, attributeName, value);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address.toModelNode()));
        assertThat(operation.hasDefined(NAME), is(true));
        assertThat(operation.get(NAME).asString(), is(attributeName));
        assertThat(operation.hasDefined(VALUE), is(true));
        assertThat(operation.get(VALUE), is(value));
    }

    /**
     * Test of getWriteAttributeOperation method, of class Util.
     */
    @Test
    public void testGetWriteModelNodeAttributeOperationWithModelNodeAddress() {
        ModelNode address = new ModelNode().add("subsystem", "test");
        String attributeName = "attr";
        ModelNode value = new ModelNode("testValue");
        ModelNode operation = Util.getWriteAttributeOperation(address, attributeName, value);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address));
        assertThat(operation.hasDefined(NAME), is(true));
        assertThat(operation.get(NAME).asString(), is(attributeName));
        assertThat(operation.hasDefined(VALUE), is(true));
        assertThat(operation.get(VALUE), is(value));
    }

    /**
     * Test of getReadAttributeOperation method, of class Util.
     */
    @Test
    public void testGetReadAttributeOperation() {
        PathAddress address = PathAddress.pathAddress("subsystem", "test");
        String attributeName = "attr";
        ModelNode operation = Util.getReadAttributeOperation(address, attributeName);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(READ_ATTRIBUTE_OPERATION));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address.toModelNode()));
        assertThat(operation.hasDefined(NAME), is(true));
        assertThat(operation.get(NAME).asString(), is(attributeName));
    }

    /**
     * Test of getUndefineAttributeOperation method, of class Util.
     */
    @Test
    public void testGetUndefineAttributeOperation() {
        PathAddress address = PathAddress.pathAddress("subsystem", "test");
        String attributeName = "attr";
        ModelNode operation = Util.getUndefineAttributeOperation(address, attributeName);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(UNDEFINE_ATTRIBUTE_OPERATION));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address.toModelNode()));
        assertThat(operation.hasDefined(NAME), is(true));
        assertThat(operation.get(NAME).asString(), is(attributeName));
    }

    /**
     * Test of isExpression method, of class Util.
     */
    @Test
    public void testIsExpression() {
        assertThat(Util.isExpression(""), is(false));
        assertThat(Util.isExpression("${test"), is(false));
        assertThat(Util.isExpression("${test}"), is(true));
    }

    /**
     * Test of getOperation method, of class Util.
     */
    @Test
    public void testGetOperationForPathAddress() {
        String operationName = "fake";
        PathAddress address = PathAddress.pathAddress("subsystem", "test");
        ModelNode params = new ModelNode();
        params.get("param1").set("value1");
        params.get("param2").set("value2");
        ModelNode operation = Util.getOperation(operationName, address, params);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(operationName));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address.toModelNode()));
        assertThat(operation.hasDefined("param1"), is(true));
        assertThat(operation.get("param1").asString(), is("value1"));
        assertThat(operation.hasDefined("param2"), is(true));
        assertThat(operation.get("param2").asString(), is("value2"));
    }

    /**
     * Test of getOperation method, of class Util.
     */
    @Test
    public void testGetOperation() {
        String operationName = "fake";
        ModelNode address = new ModelNode().add("subsystem", "test");
        ModelNode params = new ModelNode();
        params.get("param1").set("value1");
        params.get("param2").set("value2");
        ModelNode operation = Util.getOperation(operationName, address, params);
        assertThat(operation.hasDefined(OP), is(true));
        assertThat(operation.get(OP).asString(), is(operationName));
        assertThat(operation.hasDefined(OP_ADDR), is(true));
        assertThat(operation.get(OP_ADDR), is(address));
        assertThat(operation.hasDefined("param1"), is(true));
        assertThat(operation.get("param1").asString(), is("value1"));
        assertThat(operation.hasDefined("param2"), is(true));
        assertThat(operation.get("param2").asString(), is("value2"));
    }

    /**
     * Test of getParentAddressByKey method, of class Util.
     */
    @Test
    public void testGetParentAddressByKey() {
        PathAddress address = PathAddress.pathAddress(PathElement.pathElement("system", "test"),
                PathElement.pathElement("subsystem", "subtest"),
                PathElement.pathElement("test"),
                PathElement.pathElement("resource", "testResource"));
        assertThat(Util.getParentAddressByKey(address, "subsystem"), is(PathAddress.pathAddress(
                PathElement.pathElement("system", "test"),
                PathElement.pathElement("subsystem", "subtest"))));
        assertThat(Util.getParentAddressByKey(address, "system"), is(PathAddress.pathAddress(
                PathElement.pathElement("system", "test"))));
        assertThat(Util.getParentAddressByKey(address, "test"), is(PathAddress.pathAddress(
                PathElement.pathElement("system", "test"),
                PathElement.pathElement("subsystem", "subtest"),
                PathElement.pathElement("test"))));
    }

    /**
     * Test of validateOperation method, of class Util.
     */
    @Test
    public void testValidateOperation() {
        ModelNode operation = new ModelNode();
        assertThat(Util.validateOperation(operation).hasDefined(OUTCOME), is(true));
        assertThat(Util.validateOperation(operation).get(OUTCOME).asString(), is(FAILED));
        operation.get(OP).set("fake");
        assertThat(Util.validateOperation(operation).hasDefined(OUTCOME), is(false));
        operation.get(OP_ADDR).set("a");
        assertThat(Util.validateOperation(operation).hasDefined(OUTCOME), is(true));
        assertThat(Util.validateOperation(operation).get(OUTCOME).asString(), is(FAILED));
        operation.get(OP_ADDR).setEmptyList();
        assertThat(Util.validateOperation(operation).hasDefined(OUTCOME), is(false));
    }
}
