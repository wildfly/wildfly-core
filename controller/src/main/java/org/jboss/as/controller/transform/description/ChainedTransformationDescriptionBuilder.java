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
package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.ModelVersion;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface ChainedTransformationDescriptionBuilder {

    /**
     * Add a transformer for a version delta
     *
     * @param fromVersion the version to transform from
     * @param toVersion the version to transform to
     * @return the builder for the transformation
     */
    ResourceTransformationDescriptionBuilder createBuilder(ModelVersion fromVersion, ModelVersion toVersion);


    /**
     * Build the transformer chain for a version, specifying the chained versions.
     *
     * @param toVersion the version to transform to
     * @param intermediates the intermediate model versions to use in the chain.
     * @return the transformation description
     *
     */
    TransformationDescription build(ModelVersion toVersion, ModelVersion...intermediates);
}
