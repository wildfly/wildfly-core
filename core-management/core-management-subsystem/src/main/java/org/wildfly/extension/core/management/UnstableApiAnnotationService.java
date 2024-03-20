/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2024 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.wildfly.extension.core.management;

import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.extension.core.management.UnstableApiAnnotationResourceDefinition.UnstableApiAnnotationLevel;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class UnstableApiAnnotationService implements Service {

    public static ServiceName SERVICE_NAME = ServiceName.JBOSS.append("core-management", "unstable-api-annotation", "level");
    private final Consumer<UnstableApiAnnotationService> serviceConsumer;
    private final UnstableApiAnnotationLevel level;
    static final UnstableApiAnnotationLevelSupplier LEVEL_SUPPLIER = new UnstableApiAnnotationLevelSupplier();

    public UnstableApiAnnotationService(Consumer<UnstableApiAnnotationService> serviceConsumer, UnstableApiAnnotationLevel level) {
        this.serviceConsumer = serviceConsumer;
        this.level = level;
    }

    public UnstableApiAnnotationLevel getLevel() {
        return level;
    }

    @Override
    public void start(StartContext context) throws StartException {
        serviceConsumer.accept(this);
        LEVEL_SUPPLIER.level = level;
    }

    @Override
    public void stop(StopContext context) {
        serviceConsumer.accept(null);
        LEVEL_SUPPLIER.level = level;
    }

    private static class UnstableApiAnnotationLevelSupplier implements Supplier<UnstableApiAnnotationLevel> {
        private volatile UnstableApiAnnotationLevel level;
        @Override
        public UnstableApiAnnotationLevel get() {
            return level;
        }
    }


}
