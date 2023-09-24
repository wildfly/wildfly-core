/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.interfaces;

import org.jboss.as.core.model.test.TestModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class StandaloneSpecifiedInterfacesTestCase extends AbstractSpecifiedInterfacesTest {

    public StandaloneSpecifiedInterfacesTestCase() {
        super(TestModelType.STANDALONE);
    }

    @Override
    protected String getXmlResource() {
        return "standalone.xml";
    }

}
