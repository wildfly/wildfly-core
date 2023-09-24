/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class RegistryProxyControllerTestCase {

    PathElement profileA = PathElement.pathElement("profile", "profileA");
    PathElement profileB = PathElement.pathElement("profile", "profileB");
    PathElement proxyA = PathElement.pathElement("proxy", "proxyA");
    PathElement proxyB = PathElement.pathElement("proxy", "proxyB");

    ManagementResourceRegistration root;
    ManagementResourceRegistration profileAReg;
    ManagementResourceRegistration profileBReg;
    ManagementResourceRegistration profileAChildAReg;

    final ResourceDefinition rootResource = ResourceBuilder.Factory.create(PathElement.pathElement("test"), NonResolvingResourceDescriptionResolver.INSTANCE).build();

    @Before
    public void setup() {
        root = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(rootResource);
        assertNotNull(root);

        profileAReg = registerSubModel(root, profileA);
        assertNotNull(profileAReg);

        profileBReg = registerSubModel(root, profileB);
        assertNotNull(profileBReg);

        root.registerProxyController(proxyA, new TestProxyController(proxyA));
        root.registerProxyController(proxyB, new TestProxyController(proxyB));

        profileBReg.registerProxyController(proxyA, new TestProxyController(profileB, proxyA));
        profileBReg.registerProxyController(proxyB, new TestProxyController(profileB, proxyB));
    }

    @Test
    public void testCannotCreateProxyRegistryForExistingNode() {
        try {
            root.registerProxyController(profileA, new TestProxyController(profileA));
            fail("Expected failure for " + profileA);
        } catch (IllegalArgumentException expected) {
        }

        try {
            profileBReg.registerProxyController(proxyA, new TestProxyController(profileB, proxyA));
            fail("Expected failure for " + PathAddress.pathAddress(profileB, proxyA));
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testGetProxyController() {
        assertNull(root.getProxyController(PathAddress.pathAddress(profileA)));
        assertNull(root.getProxyController(PathAddress.pathAddress(profileA, PathElement.pathElement("a", "b"))));

        assertNull(root.getProxyController(PathAddress.pathAddress(profileB)));
        assertNull(root.getProxyController(PathAddress.pathAddress(profileB, PathElement.pathElement("a", "b"))));

        PathAddress address = PathAddress.pathAddress(profileB, proxyA);
        ProxyController proxy = root.getProxyController(address);
        assertNotNull(proxy);
        assertEquals(address, proxy.getProxyNodeAddress());

        address = PathAddress.pathAddress(profileB, proxyB);
        proxy = root.getProxyController(address);
        assertNotNull(proxy);
        assertEquals(address, proxy.getProxyNodeAddress());

        address = PathAddress.pathAddress(profileB, proxyA, PathElement.pathElement("a", "b"), PathElement.pathElement("c", "d"));
        proxy = root.getProxyController(address);
        assertNotNull(proxy);
        assertEquals(PathAddress.pathAddress(profileB, proxyA), proxy.getProxyNodeAddress());


        address = PathAddress.pathAddress(proxyA);
        proxy = root.getProxyController(address);
        assertNotNull(proxy);
        assertEquals(address, proxy.getProxyNodeAddress());

        address = PathAddress.pathAddress(proxyB);
        proxy = root.getProxyController(address);
        assertNotNull(proxy);
        assertEquals(address, proxy.getProxyNodeAddress());

        address = PathAddress.pathAddress(proxyA, PathElement.pathElement("a", "b"), PathElement.pathElement("c", "d"));
        proxy = root.getProxyController(address);
        assertNotNull(proxy);
        assertEquals(PathAddress.pathAddress(proxyA), proxy.getProxyNodeAddress());
    }

    @Test
    public void testGetProxyControllers() {
        Set<PathAddress> addresses = getProxyAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(4, addresses.size());
        assertTrue(addresses.contains(PathAddress.pathAddress(proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(proxyB)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyB)));

        addresses = getProxyAddresses(PathAddress.pathAddress(profileB));
        assertEquals(2, addresses.size());
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyB)));

        addresses = getProxyAddresses(PathAddress.pathAddress(profileA));
        assertEquals(0, addresses.size());

        addresses = getProxyAddresses(PathAddress.pathAddress(profileA, PathElement.pathElement("c", "d")));
        assertEquals(0, addresses.size());
    }

    @Test
    public void removeAndAddTopLevelProxyController() {
        assertNotNull(root.getProxyController(PathAddress.pathAddress(proxyB)));
        root.unregisterProxyController(proxyB);
        assertNull(root.getProxyController(PathAddress.pathAddress(proxyB)));

        Set<PathAddress> addresses = getProxyAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(3, addresses.size());
        assertTrue(addresses.contains(PathAddress.pathAddress(proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyB)));

        root.registerProxyController(proxyB, new TestProxyController(proxyB));

        addresses = getProxyAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(4, addresses.size());
        assertTrue(addresses.contains(PathAddress.pathAddress(proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(proxyB)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyB)));
    }

    @Test
    public void removeAndAddChildLevelProxyController() {
        assertNotNull(root.getProxyController(PathAddress.pathAddress(profileB, proxyA)));
        profileBReg.unregisterProxyController(proxyA);
        assertNull(profileBReg.getProxyController(PathAddress.pathAddress(proxyA)));

        Set<PathAddress> addresses = getProxyAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(3, addresses.size());
        assertTrue(addresses.contains(PathAddress.pathAddress(proxyB)));
        assertTrue(addresses.contains(PathAddress.pathAddress(proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyB)));

        profileBReg.registerProxyController(proxyA, new TestProxyController(profileB, proxyA));

        addresses = getProxyAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(4, addresses.size());
        assertTrue(addresses.contains(PathAddress.pathAddress(proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(proxyB)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyB)));
    }

    @Test
    public void testRemoveNonExistentProxyController() {
        PathElement element = PathElement.pathElement("profile", "profileA");
        root.unregisterProxyController(element);
        Set<PathAddress> addresses = getProxyAddresses(PathAddress.EMPTY_ADDRESS);
        assertEquals(4, addresses.size());
        assertTrue(addresses.contains(PathAddress.pathAddress(proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(proxyB)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyA)));
        assertTrue(addresses.contains(PathAddress.pathAddress(profileB, proxyB)));
    }

    private Set<PathAddress> getProxyAddresses(PathAddress address){
        Set<PathAddress> addresses = new HashSet<PathAddress>();
        for (ProxyController proxy : root.getProxyControllers(address)) {
            addresses.add(proxy.getProxyNodeAddress());
        }
        return addresses;
    }

    private ManagementResourceRegistration registerSubModel(final ManagementResourceRegistration parent, final PathElement address) {
        return parent.registerSubModel(new SimpleResourceDefinition(address, NonResolvingResourceDescriptionResolver.INSTANCE));
    }

    static class TestProxyController implements ProxyController {

        private final PathAddress address;

        public TestProxyController(PathElement...elements) {
            super();
            this.address = PathAddress.pathAddress(elements);
        }

        @Override
        public PathAddress getProxyNodeAddress() {
            return address;
        }

        @Override
        public void execute(ModelNode operation, OperationMessageHandler handler, ProxyOperationControl control, OperationAttachments attachments, BlockingTimeout blockingTimeout) {
        }

    }
}
