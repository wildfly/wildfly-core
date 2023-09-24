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
public class JmxRbacEnabledTestCase extends JmxRbacTestCase {

    public JmxRbacEnabledTestCase() {
        super(true);
    }

    @Override
    protected boolean canRead(StandardRole standardRole, boolean sensitiveMBeans) {
        if (!sensitiveMBeans) {
            return true;
        }
        switch (standardRole) {
        case SUPERUSER:
        case ADMINISTRATOR:
        case AUDITOR:
            return true;
        default:
            return false;
        }
    }

    @Override
    protected boolean canWrite(StandardRole standardRole, boolean sensitiveMBeans) {
        if (!sensitiveMBeans) {
            switch (standardRole) {
            case MONITOR:
            case DEPLOYER:
            case AUDITOR:
                return false;
            default:
                return true;
            }
        }
        switch (standardRole) {
        case SUPERUSER:
        case ADMINISTRATOR:
            return true;
        default:
            return false;
        }
    }

    @Override
    protected boolean canAccessSpecial(StandardRole standardRole) {
        return standardRole == StandardRole.ADMINISTRATOR || standardRole == StandardRole.SUPERUSER;
    }

}
