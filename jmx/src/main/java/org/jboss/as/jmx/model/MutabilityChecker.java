/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.model;

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

}
