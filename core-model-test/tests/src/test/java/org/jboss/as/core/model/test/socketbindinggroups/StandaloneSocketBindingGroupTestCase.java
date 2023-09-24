/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.socketbindinggroups;

import org.jboss.as.core.model.test.TestModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class StandaloneSocketBindingGroupTestCase extends AbstractSocketBindingGroupTest {

    public StandaloneSocketBindingGroupTestCase() {
        super(TestModelType.STANDALONE);
    }

    @Override
    protected String getXmlResource() {
        return "standalone.xml";
    }
}
