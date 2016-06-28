/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
