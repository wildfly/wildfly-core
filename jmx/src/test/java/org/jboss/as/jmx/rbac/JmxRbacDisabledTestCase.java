/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.rbac;

import org.jboss.as.controller.access.rbac.StandardRole;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class JmxRbacDisabledTestCase extends JmxRbacTestCase {

    public JmxRbacDisabledTestCase() {
        super(false);
    }

    @Override
    protected boolean canRead(StandardRole standardRole, boolean sensitiveMBeans) {
        return true;
    }

    @Override
    protected boolean canWrite(StandardRole standardRole, boolean sensitiveMBeans) {
        return true;
    }

    @Override
    protected boolean canAccessSpecial(StandardRole standardRole) {
        return true;
    }

}
