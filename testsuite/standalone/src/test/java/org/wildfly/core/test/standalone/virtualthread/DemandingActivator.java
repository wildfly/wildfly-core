/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.virtualthread;

import org.jboss.msc.service.ServiceName;

public final class DemandingActivator extends BaseActivator {
    public DemandingActivator() {
        super(true);
    }

    @Override
    protected ServiceName getServiceName() {
        return super.getServiceName().append("demanding");
    }
}
