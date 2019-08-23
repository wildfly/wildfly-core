/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.discovery;

import org.jboss.as.controller.ModelVersion;

/**
 * Enumeration of discovery subsystem model versions.
 * @author Paul Ferraro
 */
enum DiscoveryModel {
    VERSION_1_0_0(1, 0, 0),
    VERSION_2_0_0(2, 0, 0),
    ;
    static final DiscoveryModel CURRENT = VERSION_2_0_0;

    private final ModelVersion version;

    DiscoveryModel(int major, int minor, int micro) {
        this.version = ModelVersion.create(major, minor, micro);
    }

    /**
     * Returns the version of this model.
     * @return a model version
     */
    ModelVersion getVersion() {
        return this.version;
    }

    /**
     * Indicates whether this model is more recent than the specified version and thus requires transformation
     * @param version a model version
     * @return true this this model is more recent than the specified version, false otherwise
     */
    boolean requiresTransformation(ModelVersion version) {
        return ModelVersion.compare(this.getVersion(), version) < 0;
    }
}
