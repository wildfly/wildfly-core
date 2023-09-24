/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.io.File;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.services.path.PathEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManager.Callback.Handle;
import org.jboss.as.controller.services.path.PathManager.Event;
import org.jboss.as.controller.services.path.PathManager.PathEventContext;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;

/**
 * A holder for {@link AttributeDefinition} instances related to file locations.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class FileAttributeDefinitions {

    static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATH, ModelType.STRING, true)
        .setAllowExpression(true)
        .setMinSize(1)
        .setAttributeGroup(ElytronDescriptionConstants.FILE)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition RELATIVE_TO = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RELATIVE_TO, ModelType.STRING, true)
        .setMinSize(1)
        .setAttributeGroup(ElytronDescriptionConstants.FILE)
        .setRequires(ElytronDescriptionConstants.PATH)
        .setRestartAllServices()
        .build();

    static ServiceName pathName(String relativeTo) {
        return ServiceName.JBOSS.append(ModelDescriptionConstants.SERVER, ModelDescriptionConstants.PATH, relativeTo);
    }

    static PathResolver pathResolver() {
        return new PathResolver();
    }

    static class PathResolver {

        private String path;
        private String relativeTo;
        private PathManager pathManager;

        private Handle callbackHandle;

        PathResolver path(String path) {
            this.path = path;

            return this;
        }

        PathResolver relativeTo(String relativeTo, PathManager pathManager) {
            this.relativeTo = relativeTo;
            this.pathManager = pathManager;
            return this;
        }


        File resolve() {
            if (relativeTo != null) {
                File resolvedPath = new File(pathManager.resolveRelativePathEntry(path, relativeTo));
                callbackHandle = pathManager.registerCallback(relativeTo, new org.jboss.as.controller.services.path.PathManager.Callback() {

                    @Override
                    public void pathModelEvent(PathEventContext eventContext, String name) {
                        if (eventContext.isResourceServiceRestartAllowed() == false) {
                            eventContext.reloadRequired();
                        }
                    }

                    @Override
                    public void pathEvent(Event event, PathEntry pathEntry) {
                        // Service dependencies should trigger a stop and start.
                    }
                }, Event.REMOVED, Event.UPDATED);

                return resolvedPath;
            } else {
                return new File(path);
            }
        }

        void clear() {
            if (callbackHandle != null) {
                callbackHandle.remove();
                callbackHandle = null;
            }
        }
    }

}
