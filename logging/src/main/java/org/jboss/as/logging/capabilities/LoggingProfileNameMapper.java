/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.capabilities;

import java.util.function.Function;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.logging.CommonAttributes;

/**
 * Adds the logging profile name to the capability name if required.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingProfileNameMapper implements Function<PathAddress, String[]> {

    static final LoggingProfileNameMapper INSTANCE = new LoggingProfileNameMapper();

    private LoggingProfileNameMapper() {
    }

    @Override
    public String[] apply(final PathAddress address) {
        // Find the logging profile if it exists and add the profile name to the capability name
        for (PathElement pathElement : address) {
            if (CommonAttributes.LOGGING_PROFILE.equals(pathElement.getKey())) {
                return new String[] {pathElement.getValue(), address.getLastElement().getValue()};
            }
        }
        return new String[] {address.getLastElement().getValue()};
    }
}
