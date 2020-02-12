/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
