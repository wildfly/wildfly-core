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

import java.util.logging.Handler;

import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.capability.RuntimeCapability;

/**
 * Logging capabilities. Not for use outside the logging extension.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Capabilities {

    /**
     * Reference for the {@code relative-to} path capability.
     */
    public static final String PATH_CAPABILITY = "org.wildfly.management.path";

    /**
     * Reference for an outbound socket.
     */
    public static final String OUTBOUND_SOCKET_BINDING_CAPABILITY = "org.wildfly.network.outbound-socket-binding";

    /**
     * Reference for a socket binding.
     */
    public static final String SOCKET_BINDING_MANAGER_CAPABILITY = "org.wildfly.management.socket-binding-manager";

    /**
     * Reference to an SSL context.
     */
    public static final String SSL_CONTEXT_CAPABILITY = "org.wildfly.security.ssl-context";

    /**
     * A capability for logging formatters.
     */
    public static final RuntimeCapability<Void> FORMATTER_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.logging.formatter", true)
            .setDynamicNameMapper(LoggingProfileNameMapper.INSTANCE)
            .build();

    /**
     * A capability for logging handlers.
     * <p>
     * Note that while this capability can expose a {@linkplain Handler handler} it's not required. It's only used in
     * cases where a handler might need to register a service. This is not needed in most cases.
     * </p>
     */
    public static final RuntimeCapability<Void> HANDLER_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.logging.handler", true, Handler.class)
            .setDynamicNameMapper(LoggingProfileNameMapper.INSTANCE)
            .build();

    /**
     * A capability for loggers.
     */
    public static final RuntimeCapability<Void> LOGGER_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.logging.logger", true)
            .setDynamicNameMapper(LoggingProfileNameMapper.INSTANCE)
            .build();

    /**
     * A capability recorder for configuring a formatter on a handler.
     */
    public static final CapabilityReferenceRecorder HANDLER_FORMATTER_REFERENCE_RECORDER = new LoggingProfileCapabilityRecorder(
            HANDLER_CAPABILITY.getName(), FORMATTER_CAPABILITY.getName());

    /**
     * A capability recorder for configuring sub-handlers on a handler.
     */
    public static final CapabilityReferenceRecorder HANDLER_REFERENCE_RECORDER = new LoggingProfileCapabilityRecorder(
            HANDLER_CAPABILITY.getName(), HANDLER_CAPABILITY.getName());

    /**
     * A capability recorder for configuring handlers on a logger.
     */
    public static final CapabilityReferenceRecorder LOGGER_HANDLER_REFERENCE_RECORDER = new LoggingProfileCapabilityRecorder(
            LOGGER_CAPABILITY.getName(), HANDLER_CAPABILITY.getName());
}
