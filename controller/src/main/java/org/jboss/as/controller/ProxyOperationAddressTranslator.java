/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * Used by {@link ProxyController} implementations to translate addresses to the
 * target controller's address space.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public interface ProxyOperationAddressTranslator {
        PathAddress translateAddress(PathAddress address);
        PathAddress restoreAddress(PathAddress opAddress, PathAddress translatedAddress);

        ProxyOperationAddressTranslator NOOP = new ProxyOperationAddressTranslator() {
            @Override
            public PathAddress translateAddress(PathAddress address) {
                return address;
            }

            public PathAddress restoreAddress(PathAddress opAddress, PathAddress translatedAddress) {
                return translatedAddress;
            }

        };

        ProxyOperationAddressTranslator SERVER = new ProxyOperationAddressTranslator() {
            public PathAddress translateAddress(PathAddress address) {
                PathAddress translated = address;
                translated = lookForAndTrim(translated, ModelDescriptionConstants.HOST);
                translated = lookForAndTrim(translated, ModelDescriptionConstants.SERVER);
                return translated;
            }

            public PathAddress restoreAddress(PathAddress opAddress, PathAddress translatedAddress) {
                // original op address could be /host=foo/server=bar/subsystem=*, which after translation might have a response
                // address of /subsystem=quux. We merge the result address(es) with the original op address to restore the full
                // address into the response, for example, /subsystem=quux becomes /host=foo/server=bar/subsystem=quux
                ModelNode result = new ModelNode();
                boolean found;
                for (PathElement pe : opAddress) {
                    found = false;
                    if (pe.isWildcard()) {
                        // replace the wildcards with the actual result key / values
                        for (PathElement tpe : translatedAddress) {
                            if (tpe.getKey().equals(pe.getKey())) {
                                found = true;
                                result.add(tpe.getKey(), tpe.getValue());
                                break;
                            }
                        }
                    }
                    if (!found) {
                        result.add(pe.getKey(), pe.getValue());
                    }
                }
                return PathAddress.pathAddress(result);
            }

            private PathAddress lookForAndTrim(PathAddress addr, String search) {
                if (addr.size() == 0) {
                    return addr;
                }
                if (addr.getElement(0).getKey().equals(search)){
                    return addr.subAddress(1);
                }
                return addr;
            }
        };

        ProxyOperationAddressTranslator HOST = new ProxyOperationAddressTranslator() {
            public PathAddress translateAddress(PathAddress address) {
                return address;
            }

            public PathAddress restoreAddress(PathAddress opAddress, PathAddress translatedAddress) {
                return translatedAddress;
            }

        };
}
