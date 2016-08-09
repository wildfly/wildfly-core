/*
 * Copyright (C) 2016 Red Hat, inc., and individual contributors
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
package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;

/**
 * @author <a href="mailto:kwills@redhat.com">Ken Wills</a> (c) 2016 Red Hat, inc.
 */
public class ProxyOperationAddressTranslatorTestCase {

    static final String BASIC_PATH = "/host=foo/server=bar/subsystem=*";
    static final String BASIC_TRANSLATED_PATH = "/subsystem=*";

    @Test
    public void testServerAddressTranslation() {
        PathAddress pa = PathAddress.parseCLIStyleAddress(BASIC_PATH);
        PathAddress translated = ProxyOperationAddressTranslator.SERVER.translateAddress(pa);
        Assert.assertEquals(BASIC_TRANSLATED_PATH, translated.toCLIStyleString());
    }

    @Test
    public void testHostAddressTranslation() {
        PathAddress pa = PathAddress.parseCLIStyleAddress(BASIC_PATH);
        PathAddress translated = ProxyOperationAddressTranslator.HOST.translateAddress(pa);
        Assert.assertEquals(pa.toCLIStyleString(), translated.toCLIStyleString());
    }

    @Test
    public void testNoopAddressTranslation() {
        PathAddress pa = PathAddress.parseCLIStyleAddress(BASIC_PATH);
        PathAddress translated = ProxyOperationAddressTranslator.NOOP.translateAddress(pa);
        Assert.assertEquals(pa.toCLIStyleString(), translated.toCLIStyleString());
    }

    @Test
    public void testServerRestore() {
        PathAddress pa = PathAddress.parseCLIStyleAddress(BASIC_PATH);
        Assert.assertTrue(pa.isMultiTarget());
        PathAddress translated = ProxyOperationAddressTranslator.SERVER.translateAddress(pa);
        Assert.assertEquals(BASIC_TRANSLATED_PATH, translated.toCLIStyleString());
        PathAddress restoredPath = ProxyOperationAddressTranslator.SERVER.restoreAddress(pa, translated);
        Assert.assertEquals(pa, restoredPath);
    }

    @Test
    public void testServerRestore2() {
        PathAddress pa = PathAddress.parseCLIStyleAddress("/host=*/server=*/subsystem=*");
        Assert.assertTrue(pa.isMultiTarget());
        PathAddress translated = ProxyOperationAddressTranslator.SERVER.translateAddress(pa);
        Assert.assertEquals(BASIC_TRANSLATED_PATH, translated.toCLIStyleString());
        PathAddress restoredPath = ProxyOperationAddressTranslator.SERVER.restoreAddress(pa, translated);
        Assert.assertEquals(pa, restoredPath);
    }

    @Test
    public void testServerRestore3() {
        PathAddress pa = PathAddress.parseCLIStyleAddress("/host=slave/server=*/subsystem=logging");
        Assert.assertTrue(pa.isMultiTarget());
        PathAddress translated = ProxyOperationAddressTranslator.SERVER.translateAddress(pa);
        Assert.assertEquals("/subsystem=logging", translated.toCLIStyleString());
        PathAddress restoredPath = ProxyOperationAddressTranslator.SERVER.restoreAddress(pa, translated);
        Assert.assertEquals(pa, restoredPath);
    }

    @Test
    public void testServerRestore4() {
        PathAddress pa = PathAddress.parseCLIStyleAddress("/host=slave/server=server-one/subsystem=*");
        Assert.assertTrue(pa.isMultiTarget());
        PathAddress translated = ProxyOperationAddressTranslator.SERVER.translateAddress(pa);
        Assert.assertEquals("/subsystem=*", translated.toCLIStyleString());
        PathAddress restoredPath = ProxyOperationAddressTranslator.SERVER.restoreAddress(pa, translated);
        Assert.assertEquals(pa, restoredPath);
    }

    @Test
    public void testServerRestore5() {
        PathAddress pa = PathAddress.parseCLIStyleAddress("/host=*/server=server-one/subsystem=*");
        Assert.assertTrue(pa.isMultiTarget());
        PathAddress translated = ProxyOperationAddressTranslator.SERVER.translateAddress(pa);
        Assert.assertEquals("/subsystem=*", translated.toCLIStyleString());
        PathAddress restoredPath = ProxyOperationAddressTranslator.SERVER.restoreAddress(pa, translated);
        Assert.assertEquals(pa, restoredPath);
    }

    @Test
    public void testServerRestore6() {
        PathAddress pa = PathAddress.parseCLIStyleAddress("/host=*/server=server-one/subsystem=logging");
        Assert.assertTrue(pa.isMultiTarget());
        PathAddress translated = ProxyOperationAddressTranslator.SERVER.translateAddress(pa);
        Assert.assertEquals("/subsystem=logging", translated.toCLIStyleString());
        PathAddress resultAddress = PathAddress.parseCLIStyleAddress("/host=master/server=server-one/subsystem=logging");
        PathAddress restoredPath = ProxyOperationAddressTranslator.SERVER.restoreAddress(resultAddress, translated);
        Assert.assertEquals(resultAddress, restoredPath);
    }

    @Test
    public void testServerRestore7() {
        ModelNode node = createOpNode("host=slave/server=*/subsystem=1", READ_RESOURCE_OPERATION);
        PathAddress pa = PathAddress.pathAddress(node.get(OP_ADDR));
        Assert.assertTrue(pa.isMultiTarget());
        PathAddress translated = ProxyOperationAddressTranslator.SERVER.translateAddress(pa);
        Assert.assertEquals("/subsystem=1", translated.toCLIStyleString());
        PathAddress resultAddress = PathAddress.parseCLIStyleAddress("/server=server-one/subsystem=1");
        PathAddress restoredPath = ProxyOperationAddressTranslator.SERVER.restoreAddress(pa, resultAddress);
        PathAddress resolvedResultAddress = PathAddress.parseCLIStyleAddress("/host=slave/server=server-one/subsystem=1");
        Assert.assertEquals(resolvedResultAddress, restoredPath);
    }

    @Test
    public void testServerRestore8() {
        ModelNode node = createOpNode("host=slave/server=*/subsystem=*", READ_RESOURCE_OPERATION);
        PathAddress pa = PathAddress.pathAddress(node.get(OP_ADDR));
        Assert.assertTrue(pa.isMultiTarget());
        PathAddress translated = ProxyOperationAddressTranslator.SERVER.translateAddress(pa);
        Assert.assertEquals("/subsystem=*", translated.toCLIStyleString());
        PathAddress resultAddress = PathAddress.parseCLIStyleAddress("/server=server-one/subsystem=1");
        PathAddress restoredPath = ProxyOperationAddressTranslator.SERVER.restoreAddress(pa, resultAddress);
        PathAddress resolvedResultAddress = PathAddress.parseCLIStyleAddress("/host=slave/server=server-one/subsystem=1");
        Assert.assertEquals(resolvedResultAddress, restoredPath);
    }

    @Test
    public void testServerRestore9() {
        ModelNode node = createOpNode("host=master/server=server-one/socket-binding-group=*/socket-binding=*", READ_RESOURCE_OPERATION);
        PathAddress pa = PathAddress.pathAddress(node.get(OP_ADDR));
        Assert.assertTrue(pa.isMultiTarget());
        PathAddress translated = ProxyOperationAddressTranslator.SERVER.translateAddress(pa);
        Assert.assertEquals("/socket-binding-group=*/socket-binding=*", translated.toCLIStyleString());
        PathAddress resultAddress = PathAddress.parseCLIStyleAddress("/socket-binding-group=full-sockets/socket-binding=https");
        PathAddress restoredPath = ProxyOperationAddressTranslator.SERVER.restoreAddress(pa, resultAddress);
        PathAddress resolvedResultAddress = PathAddress.parseCLIStyleAddress("/host=master/server=server-one/socket-binding-group=full-sockets/socket-binding=https");
        Assert.assertEquals(resolvedResultAddress, restoredPath);
    }

    @Test
    public void testHostRestore() {
        PathAddress pa = PathAddress.parseCLIStyleAddress(BASIC_PATH);
        PathAddress translated = ProxyOperationAddressTranslator.HOST.translateAddress(pa);
        Assert.assertEquals(pa, translated);
        PathAddress restoredPath = ProxyOperationAddressTranslator.HOST.restoreAddress(pa, translated);
        Assert.assertEquals(pa, restoredPath);
    }

    @Test
    public void testNoopRestore() {
        PathAddress pa = PathAddress.parseCLIStyleAddress(BASIC_PATH);
        PathAddress translated = ProxyOperationAddressTranslator.NOOP.translateAddress(pa);
        Assert.assertEquals(pa, translated);
        PathAddress restoredPath = ProxyOperationAddressTranslator.NOOP.restoreAddress(pa, translated);
        Assert.assertEquals(pa, restoredPath);
    }

    static ModelNode createOpNode(String address, String operation) {
        ModelNode op = new ModelNode();

        // set address
        ModelNode list = op.get("address").setEmptyList();
        if (address != null) {
            String[] pathSegments = address.split("/");
            for (String segment : pathSegments) {
                String[] elements = segment.split("=");
                list.add(elements[0], elements[1]);
            }
        }
        op.get("operation").set(operation);
        return op;
    }

}
