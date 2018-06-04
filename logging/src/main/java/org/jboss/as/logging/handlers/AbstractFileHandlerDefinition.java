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

package org.jboss.as.logging.handlers;

import java.util.Comparator;
import java.util.logging.Handler;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.logmanager.PropertySorter.DefaultPropertySorter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractFileHandlerDefinition extends AbstractHandlerDefinition {

    private static final String CHANGE_FILE_OPERATION_NAME = "change-file";

    private final ResolvePathHandler resolvePathHandler;
    private final PathInfoHandler diskUsagePathHandler;
    private final boolean registerLegacyOps;

    AbstractFileHandlerDefinition(final PathElement path, final Class<? extends Handler> type,
                                  final ResolvePathHandler resolvePathHandler,
                                  final PathInfoHandler diskUsagePathHandler,
                                  final AttributeDefinition... attributes) {
        this(path, true, type, resolvePathHandler, diskUsagePathHandler, attributes);
    }

    AbstractFileHandlerDefinition(final PathElement path, final boolean registerLegacyOps,
                                  final Class<? extends Handler> type,
                                  final ResolvePathHandler resolvePathHandler,
                                  final PathInfoHandler diskUsagePathHandler,
                                  final AttributeDefinition... attributes) {
        super(path, registerLegacyOps, type, new DefaultPropertySorter(FileNameLastComparator.INSTANCE), attributes);
        this.registerLegacyOps = registerLegacyOps;
        this.resolvePathHandler = resolvePathHandler;
        this.diskUsagePathHandler = diskUsagePathHandler;
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration registration) {
        super.registerOperations(registration);
        if (registerLegacyOps) {
            registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(CHANGE_FILE_OPERATION_NAME, getResourceDescriptionResolver())
                    .setDeprecated(ModelVersion.create(1, 2, 0))
                    .setParameters(CommonAttributes.FILE)
                    .build(), HandlerOperations.CHANGE_FILE);
        }
        if (resolvePathHandler != null)
            registration.registerOperationHandler(resolvePathHandler.getOperationDefinition(), resolvePathHandler);
        if (diskUsagePathHandler != null)
            PathInfoHandler.registerOperation(registration, diskUsagePathHandler);
    }

    private static class FileNameLastComparator implements Comparator<String> {
        static final FileNameLastComparator INSTANCE = new FileNameLastComparator();
        static final int EQUAL = 0;
        static final int GREATER = 1;
        static final int LESS = -1;

        private final String filePropertyName = CommonAttributes.FILE.getPropertyName();

        @Override
        public int compare(final String o1, final String o2) {
            if (o1.equals(o2)) {
                return EQUAL;
            }
            // File should always be last
            if (filePropertyName.equals(o1)) {
                return GREATER;
            }
            if (filePropertyName.equals(o2)) {
                return LESS;
            }
            return o1.compareTo(o2);
        }
    }
}
