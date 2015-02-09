/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.host.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTO_START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UPDATE_AUTO_START_WITH_SERVER_STATUS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.registry.DelegatingResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ServerConfigResource extends DelegatingResource {

    private final ServerInventory serverInventory;
    private final String serverName;
    private final ControlledProcessState processState;
    private final Path autoStartDataDir;

    private static final String STOPPED_EXT = ".stopped";
    private static final String STARTED_EXT = ".started";

    public ServerConfigResource(ServerInventory serverInventory, ControlledProcessState processState, String serverName, File domainDataDir, Resource delegate) {
        super(delegate);
        this.serverInventory = serverInventory;
        this.serverName = serverName;
        this.processState = processState;
        this.autoStartDataDir = domainDataDir.toPath().resolve("auto-start");
        try {
            if (Files.notExists(autoStartDataDir)) {
                Files.createDirectory(autoStartDataDir);
            }
        } catch (IOException ex) {
            throw HostControllerLogger.ROOT_LOGGER.couldNotCreateDomainAutoStartDirectory(autoStartDataDir, ex);
        }
    }

    @Override
    public ModelNode getModel() {
        assert serverName != null && !serverName.isEmpty() : "ServerName is null";

        ModelNode model = super.getModel();
        if (processState.getState() == ControlledProcessState.State.STARTING) {
            getAutoStart(serverName, model);
        } else if (processState.getState() == ControlledProcessState.State.RUNNING) {
            persistAutoStart(serverName, model);
        }
        return model;
    }

    private void getAutoStart(String serverName, ModelNode model) {
        Path startedFile = autoStartDataDir.resolve(serverName + STARTED_EXT);
        Path stoppedFile = autoStartDataDir.resolve(serverName + STOPPED_EXT);
        if (shouldUpdateAutoStart(model)) {
            if (Files.exists(startedFile)) {
                model.get(AUTO_START).set(true);
            }
            if (Files.exists(stoppedFile)) {
                model.get(AUTO_START).set(false);
            }
        }
    }

    private void persistAutoStart(String serverName, ModelNode model) {
        Path startedFile = autoStartDataDir.resolve(serverName + STARTED_EXT);
        Path stoppedFile = autoStartDataDir.resolve(serverName + STOPPED_EXT);
        if (serverInventory != null && shouldUpdateAutoStart(model)) {
            ServerStatus status = serverInventory.determineServerStatus(serverName);
            try {
                if (status == ServerStatus.STARTED || status == ServerStatus.STARTING) {
                    if (Files.notExists(startedFile)) {
                        Files.createFile(startedFile);
                    }
                    Files.deleteIfExists(stoppedFile);
                    model.get(AUTO_START).set(true);
                } else if (status == ServerStatus.STOPPED || status == ServerStatus.STOPPING) {
                    if (Files.notExists(stoppedFile)) {
                        Files.createFile(stoppedFile);
                    }
                    Files.deleteIfExists(startedFile);
                    model.get(AUTO_START).set(false);
                }
            } catch (IOException ex) {
                HostControllerLogger.ROOT_LOGGER.couldNotPersistAutoStartServerStatus(ex);
            }
        }
    }

    private boolean shouldUpdateAutoStart(final ModelNode model) {
        return model.hasDefined(UPDATE_AUTO_START_WITH_SERVER_STATUS)
                && model.get(UPDATE_AUTO_START_WITH_SERVER_STATUS).asBoolean();
    }

    @Override
    public Resource clone() {
        Resource delegate = super.clone();
        return new ServerConfigResource(serverInventory, processState, serverName, autoStartDataDir.getParent().toFile(), delegate);
    }

}
