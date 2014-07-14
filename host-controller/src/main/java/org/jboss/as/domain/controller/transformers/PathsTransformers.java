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

package org.jboss.as.domain.controller.transformers;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;

/**
 * Transformer registration for the domain-level path resources.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
class PathsTransformers {

    public static ChainedTransformationDescriptionBuilder buildTransformerChain(ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PathResourceDefinition.PATH_ADDRESS, currentVersion);
        chainedBuilder.createBuilder(currentVersion, DomainTransformers.VERSION_1_3)
             .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, PathResourceDefinition.PATH)
                .setValueConverter(AttributeConverter.NAME_FROM_ADDRESS, ModelDescriptionConstants.NAME)
                .end()
            .addOperationTransformationOverride(ADD)
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, PathResourceDefinition.PATH)
                .end();
        chainedBuilder.createBuilder(DomainTransformers.VERSION_1_3, DomainTransformers.VERSION_1_2);
        return chainedBuilder;
    }
}
