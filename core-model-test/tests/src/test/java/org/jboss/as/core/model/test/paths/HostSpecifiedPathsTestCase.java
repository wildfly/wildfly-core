/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.paths;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.util.ServerConfigInitializers;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class HostSpecifiedPathsTestCase extends AbstractSpecifiedPathsTestCase {

    public HostSpecifiedPathsTestCase() {
        super(TestModelType.HOST);
    }

    @Override
    protected String getXmlResource() {
        return "host.xml";
    }

    @Override
    protected PathAddress getPathsParent() {
        return PathAddress.pathAddress(HOST, "primary");
    }

    @Override
    protected ModelInitializer getModelInitalizer() {
        return ServerConfigInitializers.XML_MODEL_INITIALIZER;
    }

    @Override
    protected ModelInitializer createEmptyModelInitalizer() {
        return new ModelInitializer() {
            @Override
            public void populateModel(Resource rootResource) {
                //Register the host resource that will be the parent of the path
                rootResource.registerChild(getPathsParent().getLastElement(), Resource.Factory.create());
            }
        };
    }

}
