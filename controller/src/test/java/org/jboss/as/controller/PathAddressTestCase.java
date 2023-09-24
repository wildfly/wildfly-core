/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import org.jboss.as.controller._private.OperationFailedRuntimeException;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class PathAddressTestCase {

    @Test
    public void testValidAddresses() {
        //If the address starts with host=>*,server=>* we allow another host and another server
        PathAddress pathAddress = PathAddress.pathAddress(PathElement.pathElement(HOST), PathElement.pathElement(SERVER));
        Assert.assertEquals(2, pathAddress.size());
        Assert.assertEquals(HOST, pathAddress.getElement(0).getKey());
        Assert.assertEquals(SERVER, pathAddress.getElement(1).getKey());

        pathAddress = PathAddress.pathAddress(new ModelNode().add(HOST, "*").add(SERVER, "*"));
        Assert.assertEquals(2, pathAddress.size());
        Assert.assertEquals(HOST, pathAddress.getElement(0).getKey());
        Assert.assertEquals(SERVER, pathAddress.getElement(1).getKey());

        pathAddress = PathAddress.pathAddress(PathElement.pathElement(HOST), PathElement.pathElement(SERVER), PathElement.pathElement(HOST), PathElement.pathElement(SERVER));
        Assert.assertEquals(4, pathAddress.size());
        Assert.assertEquals(HOST, pathAddress.getElement(0).getKey());
        Assert.assertEquals(SERVER, pathAddress.getElement(1).getKey());
        Assert.assertEquals(HOST, pathAddress.getElement(2).getKey());
        Assert.assertEquals(SERVER, pathAddress.getElement(3).getKey());

        pathAddress = PathAddress.pathAddress(new ModelNode().add(HOST, "*").add(SERVER, "*").add(HOST, "*").add(SERVER, "*"));
        Assert.assertEquals(4, pathAddress.size());
        Assert.assertEquals(HOST, pathAddress.getElement(0).getKey());
        Assert.assertEquals(SERVER, pathAddress.getElement(1).getKey());
        Assert.assertEquals(HOST, pathAddress.getElement(2).getKey());
        Assert.assertEquals(SERVER, pathAddress.getElement(3).getKey());

        pathAddress = PathAddress.pathAddress(PathElement.pathElement(HOST), PathElement.pathElement(SERVER, "server"), PathElement.pathElement(HOST, "host2"), PathElement.pathElement(SERVER), PathElement.pathElement("X"));
        Assert.assertEquals(5, pathAddress.size());
        Assert.assertEquals(HOST, pathAddress.getElement(0).getKey());
        Assert.assertEquals("*", pathAddress.getElement(0).getValue());
        Assert.assertEquals(SERVER, pathAddress.getElement(1).getKey());
        Assert.assertEquals("server", pathAddress.getElement(1).getValue());
        Assert.assertEquals(HOST, pathAddress.getElement(2).getKey());
        Assert.assertEquals("host2", pathAddress.getElement(2).getValue());
        Assert.assertEquals(SERVER, pathAddress.getElement(3).getKey());
        Assert.assertEquals("*", pathAddress.getElement(3).getValue());
        Assert.assertEquals("X", pathAddress.getElement(4).getKey());
        Assert.assertEquals("*", pathAddress.getElement(4).getValue());

        pathAddress = PathAddress.pathAddress(new ModelNode().add(HOST, "host").add(SERVER, "server").add(SERVER, "server2").add(HOST, "host2").add("X", "*").add("Y", "y"));
        Assert.assertEquals(6, pathAddress.size());
        Assert.assertEquals(HOST, pathAddress.getElement(0).getKey());
        Assert.assertEquals("host", pathAddress.getElement(0).getValue());
        Assert.assertEquals(SERVER, pathAddress.getElement(1).getKey());
        Assert.assertEquals("server", pathAddress.getElement(1).getValue());
        Assert.assertEquals(SERVER, pathAddress.getElement(2).getKey());
        Assert.assertEquals("server2", pathAddress.getElement(2).getValue());
        Assert.assertEquals(HOST, pathAddress.getElement(3).getKey());
        Assert.assertEquals("host2", pathAddress.getElement(3).getValue());
        Assert.assertEquals("X", pathAddress.getElement(4).getKey());
        Assert.assertEquals("*", pathAddress.getElement(4).getValue());
        Assert.assertEquals("Y", pathAddress.getElement(5).getKey());
        Assert.assertEquals("y", pathAddress.getElement(5).getValue());

        pathAddress = PathAddress.pathAddress(PathElement.pathElement("one"));
        Assert.assertEquals(1, pathAddress.size());
        Assert.assertEquals("one", pathAddress.getElement(0).getKey());
        Assert.assertEquals("*", pathAddress.getElement(0).getValue());

        pathAddress = PathAddress.pathAddress(new ModelNode().add("one", "1").add("two", "2").add("three", "3"));
        Assert.assertEquals(3, pathAddress.size());
        Assert.assertEquals("one", pathAddress.getElement(0).getKey());
        Assert.assertEquals("1", pathAddress.getElement(0).getValue());
        Assert.assertEquals("two", pathAddress.getElement(1).getKey());
        Assert.assertEquals("2", pathAddress.getElement(1).getValue());
        Assert.assertEquals("three", pathAddress.getElement(2).getKey());
        Assert.assertEquals("3", pathAddress.getElement(2).getValue());
    }

    @Test(expected=OperationFailedRuntimeException.class)
    public void testDuplicateFailsElement() {
        PathAddress.pathAddress(PathElement.pathElement("one", "1"), PathElement.pathElement("one", "2"));
    }

    @Test(expected=OperationFailedRuntimeException.class)
    public void testDuplicateFailsModelNode() {
        PathAddress.pathAddress(new ModelNode().add("one", "1").add("one", "2"));
    }


    @Test(expected=OperationFailedRuntimeException.class)
    public void testDuplicateHostOnlyFailsElement() {
        PathAddress.pathAddress(PathElement.pathElement(HOST, "1"), PathElement.pathElement(HOST, "2"));
    }

    @Test(expected=OperationFailedRuntimeException.class)
    public void testDuplicateHostOnlyFailsModelNode() {
        PathAddress.pathAddress(new ModelNode().add(HOST, "1").add(HOST, "2"));
    }

    @Test(expected=OperationFailedRuntimeException.class)
    public void testDuplicateServerOnlyFailsElement() {
        PathAddress.pathAddress(PathElement.pathElement(SERVER, "1"), PathElement.pathElement(SERVER, "2"));
    }

    @Test(expected=OperationFailedRuntimeException.class)
    public void testDuplicateServerOnlyFailsModelNode() {
        PathAddress.pathAddress(new ModelNode().add(SERVER, "1").add(SERVER, "2"));
    }


    @Test(expected=OperationFailedRuntimeException.class)
    public void testDuplicateWrongOrderServerFailsElement() {
        PathAddress.pathAddress(PathElement.pathElement(SERVER, "1"), PathElement.pathElement(HOST, "2"), PathElement.pathElement(SERVER));
    }

    @Test(expected=OperationFailedRuntimeException.class)
    public void testDuplicateWrongOrderServerFailsModelNode() {
        PathAddress.pathAddress(new ModelNode().add(SERVER, "1").add(HOST, "2").add(SERVER, "*"));
    }


    @Test(expected=OperationFailedRuntimeException.class)
    public void testDuplicateWrongOrderHostFailsElement() {
        PathAddress.pathAddress(PathElement.pathElement(SERVER, "1"), PathElement.pathElement(HOST, "2"), PathElement.pathElement(HOST));
    }

    @Test(expected=OperationFailedRuntimeException.class)
    public void testDuplicateWrongOrderHostFailsModelNode() {
        PathAddress.pathAddress(new ModelNode().add(SERVER, "1").add(HOST, "2").add(HOST, "*"));
    }

    @Test
    public void testParseCLIStyleAddress() {
        assertThat(PathAddress.parseCLIStyleAddress(""), is(PathAddress.EMPTY_ADDRESS));
        PathAddress expectedResult = PathAddress.pathAddress(PathElement.pathElement("subsystem", "io"), PathElement.pathElement("worker", "new-worker1"));
        assertThat(PathAddress.parseCLIStyleAddress("/subsystem=io/worker=new-worker1"), is(expectedResult));
        expectedResult = PathAddress.pathAddress(PathElement.pathElement("subsystem", "io"), PathElement.pathElement("workers"), PathElement.pathElement("worker", "new-worker1"));
        assertThat(PathAddress.parseCLIStyleAddress("/subsystem=io/workers=*/worker=new-worker1"), is(expectedResult));
    }

    @Test
    public void testParseCLIStyleWrongAddress() {
        String wrongAddress = "/subsystem=io/workers/worker=new-worker1";
        try {
            PathAddress.parseCLIStyleAddress(wrongAddress);
            fail();
        } catch (IllegalArgumentException ex) {
            assertThat(ex.getMessage(), containsString("WFLYCTL0387"));
            assertThat(ex.getMessage(), containsString(wrongAddress));
        }
    }

    @Test
    public void testMatchingPaths() throws OperationFailedException {
        Assert.assertTrue(PathAddress.parseCLIStyleAddress("/subsystem=*").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto")));
        Assert.assertTrue(PathAddress.parseCLIStyleAddress("/subsystem=*/ext=*").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto/ext=foo")));
        Assert.assertTrue(PathAddress.parseCLIStyleAddress("/subsystem=toto/ext=foo").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto/ext=foo")));
        Assert.assertTrue(PathAddress.parseCLIStyleAddress("/subsystem=*/ext=foo").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto/ext=foo")));
        Assert.assertTrue(PathAddress.parseCLIStyleAddress("/subsystem=[toto1,toto2]/ext=foo").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto1/ext=foo")));
        Assert.assertTrue(PathAddress.parseCLIStyleAddress("/subsystem=[toto1,toto2]/ext=foo").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto2/ext=foo")));
        Assert.assertTrue(PathAddress.parseCLIStyleAddress("/subsystem=[toto1,toto2]/ext=[foo1,foo2]").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto2/ext=foo2")));

        Assert.assertFalse(PathAddress.parseCLIStyleAddress("/subsys=*").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto")));
        Assert.assertFalse(PathAddress.parseCLIStyleAddress("/subsystem=*").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto/ext=foo")));
        Assert.assertFalse(PathAddress.parseCLIStyleAddress("/subsystem=*/ext=*").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto")));
        Assert.assertFalse(PathAddress.parseCLIStyleAddress("/subsystem=*/ext=*").matches(
                null));
        Assert.assertFalse(PathAddress.parseCLIStyleAddress("/subsystem=[toto1,toto2]/ext=foo").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto3/ext=foo")));
        Assert.assertFalse(PathAddress.parseCLIStyleAddress("/subsystem=[toto1,toto2]/ext=[foo1,foo2]").matches(
                PathAddress.parseCLIStyleAddress("/subsystem=toto2/ext=foo3")));
    }
}
