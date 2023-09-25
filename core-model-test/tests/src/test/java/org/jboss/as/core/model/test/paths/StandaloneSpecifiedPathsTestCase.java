/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.paths;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.core.model.test.TestModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class StandaloneSpecifiedPathsTestCase extends AbstractSpecifiedPathsTestCase {

    public StandaloneSpecifiedPathsTestCase() {
        super(TestModelType.STANDALONE);
    }

    @Override
    protected String getXmlResource() {
        return "standalone.xml";
    }

    @Override
    protected PathAddress getPathsParent() {
        return PathAddress.EMPTY_ADDRESS;
    }
}
