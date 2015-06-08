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

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;

/**
 * Transformer registration for the server-group resources.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
class ServerGroupTransformers {

    public static ChainedTransformationDescriptionBuilder buildTransformerChain(ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(ServerGroupResourceDefinition.PATH, currentVersion);

        //////////////////////////////////
        //The EAP/AS 7.x chains
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(currentVersion, DomainTransformers.VERSION_1_7)
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, ServerGroupResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ServerGroupResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE)
                .end();
        JvmTransformers.registerTransformers2_1_AndBelow(builder);

        return chainedBuilder;
    }
}
