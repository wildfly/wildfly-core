/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.interfaces;

import java.util.Set;

import org.jboss.as.controller.logging.ControllerLogger;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class CriteriaValidator {
    final Set<InterfaceCriteria> criteria;

    CriteriaValidator(Set<InterfaceCriteria> criteria) {
        this.criteria = criteria;
    }

    String validate() {
        for (InterfaceCriteria current : criteria) {
            Validation validation = getValidation(current);
            if (validation == null) {
                continue;
            }
            for (InterfaceCriteria candidate : criteria) {
                if (current == candidate) {
                    continue;
                }
                String error = validation.validate(current, candidate);
                if (error != null) {
                    return error;
                }
            }
        }
        return null;
    }

    Validation getValidation(InterfaceCriteria criteria) {
        if (criteria instanceof LoopbackInterfaceCriteria) {
            return LOOPBACK_INTERFACE;
        } else if (criteria instanceof LinkLocalInterfaceCriteria) {
            //TODO
            //return LINK_LOCAL_INTERFACE;
            return null;
        } else if (criteria instanceof NotInterfaceCriteria) {
            return NOT_INTERFACE;
        }
        return null;
    }


    interface Validation {
        String validate(InterfaceCriteria current, InterfaceCriteria candidate);
    }

    static Validation LOOPBACK_INTERFACE = new Validation() {
        @Override
        public String validate(InterfaceCriteria current, InterfaceCriteria candidate) {
            if (candidate instanceof InetAddressMatchInterfaceCriteria) {
                return ControllerLogger.ROOT_LOGGER.cantHaveBothLoopbackAndInetAddressCriteria();
            }
            return null;
        }
    };

    //TODO This needs to check the inet address match interface criteria is not link local
//    static Validation LINK_LOCAL_INTERFACE = new Validation() {
//
//        @Override
//        public String validate(InterfaceCriteria current, InterfaceCriteria candidate) {
//            if (candidate instanceof InetAddressMatchInterfaceCriteria) {
//                return MESSAGES.cantHaveBothLinkLocalAndInetAddressCriteria();
//            }
//            return null;
//        }
//    };

    static Validation NOT_INTERFACE = new Validation() {
        @Override
        public String validate(InterfaceCriteria current, InterfaceCriteria candidate) {
            for (InterfaceCriteria curr : ((NotInterfaceCriteria)current).getAllCriteria()) {
                if (curr.equals(candidate)) {
                    return ControllerLogger.ROOT_LOGGER.cantHaveSameCriteriaForBothNotAndInclusion(candidate);
                }
            }
            return null;
        }
    };
}
