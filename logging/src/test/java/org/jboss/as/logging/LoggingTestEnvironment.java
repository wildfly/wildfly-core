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

package org.jboss.as.logging;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.ControllerInitializer;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Date: 06.01.2012
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingTestEnvironment extends AdditionalInitialization implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final LoggingTestEnvironment INSTANCE;
    private static final LoggingTestEnvironment MANAGEMENT_INSTANCE;

    static {
        final Path configDir = getDirectory("jboss.server.config.dir", "target", "config");
        final Path logDir = getDirectory("jboss.server.log.dir", "target", "logs");
        INSTANCE = new LoggingTestEnvironment(logDir, configDir, RunningMode.NORMAL);
        MANAGEMENT_INSTANCE = new LoggingTestEnvironment(logDir, configDir, RunningMode.ADMIN_ONLY);
    }

    private final RunningMode runningMode;
    private transient Path logDir;
    private transient Path configDir;


    private LoggingTestEnvironment(final Path logDir, final Path configDir, final RunningMode runningMode) {
        this.logDir = logDir;
        this.configDir = configDir;
        this.runningMode = runningMode;
    }

    public static LoggingTestEnvironment get() {
        return INSTANCE;
    }

    static LoggingTestEnvironment getManagementInstance() {
        return MANAGEMENT_INSTANCE;
    }

    Path getLogDir() {
        return logDir;
    }

    Path getConfigDir() {
        return configDir;
    }

    @Override
    protected RunningMode getRunningMode() {
        return runningMode;
    }

    @Override
    protected void setupController(final ControllerInitializer controllerInitializer) {
        super.setupController(controllerInitializer);
        System.setProperty("jboss.server.log.dir", logDir.toAbsolutePath().toString());
        System.setProperty("jboss.server.config.dir", configDir.toAbsolutePath().toString());
        controllerInitializer.addPath("jboss.server.log.dir", logDir.toAbsolutePath().toString(), null);
        controllerInitializer.addPath("jboss.server.config.dir", configDir.toAbsolutePath().toString(), null);
        if (runningMode == RunningMode.NORMAL) {
            controllerInitializer.addRemoteOutboundSocketBinding("log-server", "localhost", 10514);
        }
    }

    @Override
    protected void initializeExtraSubystemsAndModel(final ExtensionRegistry extensionRegistry, final Resource rootResource, final ManagementResourceRegistration rootRegistration, final RuntimeCapabilityRegistry capabilityRegistry) {
        super.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration, capabilityRegistry);
        if (runningMode == RunningMode.NORMAL) {
            registerCapabilities(capabilityRegistry,
                    RuntimeCapability.Builder.of("org.wildfly.network.outbound-socket-binding", true, OutboundSocketBinding.class).build()
            );
        }
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        logDir = getDirectory("jboss.server.log.dir", "target", "logs");
        configDir = getDirectory("jboss.server.config.dir", "target", "config");
    }

    private static Path getDirectory(final String propName, final String... paths) {
        final String value = WildFlySecurityManager.getPropertyPrivileged(propName, null);
        final Path dir;
        if (value == null) {
            dir = Paths.get(".", paths);
        } else {
            dir = Paths.get(value);
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + propName, e);
        }
        return dir;
    }
}
