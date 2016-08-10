/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.test.shutdown;

import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;

/**
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a>  (c) 2015 Red Hat, inc.
 */
public class SlowStopServiceActivator implements ServiceActivator {
    public static final long SHUTDOWN_WAITING_TIME = TimeoutUtil.adjust(3000);
    /**
     * Class dependencies required to use the {@link org.wildfly.test.undertow.UndertowService}.
     */
    public static final Class<?>[] DEPENDENCIES = {
            SlowStopService.class,
            SlowStopServiceActivator.class,
            TimeoutUtil.class
    };

    @Override
    public final void activate(final ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        final long duration = getDuration();
        assert duration > 0 : "duration must be greater than 0";
        final SlowStopService service = new SlowStopService(duration);
        serviceActivatorContext.getServiceTarget().addService(getServiceName(), service).install();
    }

    /**
     * Returns the service name to use when adding the SlowStopService.
     *
     * @return the slow stop service name
     */
    protected ServiceName getServiceName() {
        return SlowStopService.DEFAULT_SERVICE_NAME;
    }

    protected long getDuration() {
        return SHUTDOWN_WAITING_TIME;
    }

}
