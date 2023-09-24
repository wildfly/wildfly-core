/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.interfaces;

import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.util.ServerConfigInitializers;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class HostSpecifiedInterfacesTestCase extends AbstractSpecifiedInterfacesTest {

    public HostSpecifiedInterfacesTestCase() {
        super(TestModelType.HOST);
    }

    @Override
    protected String getXmlResource() {
        return "host.xml";
    }

    @Override
    protected ModelInitializer getModelInitializer() {
        return ServerConfigInitializers.XML_MODEL_INITIALIZER;
    }

}
