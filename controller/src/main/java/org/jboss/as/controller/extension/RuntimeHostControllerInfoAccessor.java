/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller.extension;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface RuntimeHostControllerInfoAccessor {
    /**
     * If the {@link OperationContext#getProcessType()#isHostController()} is true return an instance of
     * LocalHostControllerInfoImpl
     *
     * @param context the operation context
     * @return the LocalHostControllerInfoImpl, or {@code null} if we are not a host controller
     * @throws IllegalStateException if we are a host controller booting and not yet in the {@link OperationContext.Stage#RUNTIME} stage
     */
    HostControllerInfo getHostControllerInfo(OperationContext context) throws OperationFailedException ;

    RuntimeHostControllerInfoAccessor SERVER = new RuntimeHostControllerInfoAccessor() {
        @Override
        public HostControllerInfo getHostControllerInfo(OperationContext context) {
            return null;
        }
    };

    interface HostControllerInfo {
        boolean isMasterHc();
    }
}
