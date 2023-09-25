/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.paths;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.util.ServerConfigInitializers;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class HostServerSpecifiedPathsTestCase extends AbstractSpecifiedPathsTestCase {

    private static final PathAddress PARENT = PathAddress.pathAddress(HOST, "primary");

    public HostServerSpecifiedPathsTestCase() {
        super(TestModelType.HOST);
    }

    @Override
    protected String getXmlResource() {
        return "host.xml";
    }

    @Override
    protected PathAddress getPathsParent() {
        return PathAddress.pathAddress(PARENT.append(SERVER_CONFIG, "server-one"));
    }

    @Override
    protected ModelInitializer createEmptyModelInitalizer() {
        return new ModelInitializer() {
            @Override
            public void populateModel(Resource rootResource) {
                Resource host = Resource.Factory.create();
                rootResource.registerChild(PARENT.getLastElement(), host);
                ModelNode serverConfig = new ModelNode();
                serverConfig.get(GROUP).set("test");
                Resource server1 = Resource.Factory.create();
                server1.writeModel(serverConfig);
                host.registerChild(getPathsParent().getLastElement(), server1);
            }
        };
    }

    @Override
    protected ModelInitializer getModelInitalizer() {
        return ServerConfigInitializers.XML_MODEL_INITIALIZER;
    }
}
