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
package org.jboss.as.jmx.model;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
abstract class MutabilityChecker {

    abstract boolean mutable(PathAddress address);

    static MutabilityChecker create(ProcessType processType, boolean isMasterHc) {
        if (processType == ProcessType.STANDALONE_SERVER) {
            return new StandaloneServerChecker();
        } else if (processType.isHostController()) {
            return new HostControllerChecker(isMasterHc);
        }
        return new NonMutableChecker();
    }

    private static class StandaloneServerChecker extends MutabilityChecker {
        @Override
        boolean mutable(PathAddress address) {
            return true;
        }
    }

    private static class NonMutableChecker extends MutabilityChecker {
        @Override
        boolean mutable(PathAddress address) {
            return false;
        }
    }

    private static class HostControllerChecker extends MutabilityChecker {
        private final boolean isMaster;

        public HostControllerChecker(boolean isMaster) {
            this.isMaster = isMaster;
        }

        @Override
        boolean mutable(PathAddress address) {

            //Turn off the writes for the host controller while we decide if it is a good idea or not
            if (true) {
                return false;
            }

            if (isMaster) {
                return true;
            }
            if (address.size() == 0) {
                return false;
            }
            return address.getElement(0).getKey().equals(HOST);
        }

    }
}
