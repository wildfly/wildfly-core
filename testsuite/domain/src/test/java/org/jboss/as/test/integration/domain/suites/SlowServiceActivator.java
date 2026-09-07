/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import java.util.concurrent.TimeUnit;

import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceRegistryException;

/**
 * Simple service activator that delays the activation some seconds.
 *
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
public class SlowServiceActivator implements ServiceActivator {
    @Override
    public void activate(ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        try {
            long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(60)*1000;
            while (System.currentTimeMillis() - timeout < 0) {
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (InterruptedException e) {
            throw new ServiceRegistryException(e);
        }
    }
}
