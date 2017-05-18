/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.controller.registry;

import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests of behavior of a ManagementResourceRegistration for a proxied resource.
 *
 * @author Brian Stansberry
 */
public class ProxyControllerRegistrationUnitTestCase {

    private static final PathElement PROXY_ELEMENT = PathElement.pathElement("proxy", "a");
    private static final PathAddress LAST_ADDRESS = PathAddress.EMPTY_ADDRESS.append(PROXY_ELEMENT).append("child", "one").append("grandchild", "z");

    private ManagementResourceRegistration root;
    private ProxyController proxyController;

    @Before
    public void setup() {
        root = ManagementResourceRegistration.Factory.forProcessType(ProcessType.HOST_CONTROLLER).createRegistration(new SimpleResourceDefinition(null, new NonResolvingResourceDescriptionResolver()));
        proxyController = new ProxyController() {
            @Override
            public PathAddress getProxyNodeAddress() {
                return PathAddress.pathAddress(PROXY_ELEMENT);
            }

            @Override
            public void execute(ModelNode operation, OperationMessageHandler handler, ProxyOperationControl control, OperationAttachments attachments, BlockingTimeout blockingTimeout) {
                throw new UnsupportedOperationException();
            }
        };
        root.registerProxyController(PROXY_ELEMENT, proxyController);
    }

    @Test
    public void testAddressesAndParents() {
        PathAddress address = LAST_ADDRESS;
        ImmutableManagementResourceRegistration mrr = root.getSubModel(address);
        while (address != null) {
            if (address.size() > 0) {
                Assert.assertEquals(LAST_ADDRESS.subAddress(0, address.size()), mrr.getPathAddress());
                Assert.assertTrue(mrr.isRemote());
                Assert.assertEquals(proxyController, mrr.getProxyController(PathAddress.EMPTY_ADDRESS));
                Assert.assertEquals(proxyController, root.getProxyController(address));
                address = address.getParent();
                mrr = mrr.getParent();
            } else {
                Assert.assertEquals(PathAddress.EMPTY_ADDRESS, mrr.getPathAddress());
                Assert.assertEquals(root, mrr);
                // break the loop
                address = null;
            }
        }
    }
}
