/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.elytron.common;

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
public class FileAttributeDefinitions {

    public static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.PATH, ModelType.STRING, true)
        .setAllowExpression(true)
        .setMinSize(1)
        .setAttributeGroup(ElytronCommonConstants.FILE)
        .setRestartAllServices()
        .build();

    public static final SimpleAttributeDefinition RELATIVE_TO = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.RELATIVE_TO, ModelType.STRING, true)
        .setMinSize(1)
        .setAttributeGroup(ElytronCommonConstants.FILE)
        .setRequires(ElytronCommonConstants.PATH)
        .setRestartAllServices()
        .build();

    public static ServiceName pathName(String relativeTo) {
        return ServiceName.JBOSS.append(ModelDescriptionConstants.SERVER, ModelDescriptionConstants.PATH, relativeTo);
    }

    public static PathResolver pathResolver() {
        return new PathResolver();
    }

    public static class PathResolver {

        private String path;
        private String relativeTo;
        private PathManager pathManager;

        private Handle callbackHandle;

        public PathResolver path(String path) {
            this.path = path;

            return this;
        }

        public PathResolver relativeTo(String relativeTo, PathManager pathManager) {
            this.relativeTo = relativeTo;
            this.pathManager = pathManager;
            return this;
        }


        public File resolve() {
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

        public void clear() {
            if (callbackHandle != null) {
                callbackHandle.remove();
                callbackHandle = null;
            }
        }
    }

}
