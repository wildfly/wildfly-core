/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.transformers;

import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.createBuilderFromCurrent;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.createChainFromCurrent;

import org.jboss.as.controller.resource.AbstractSocketBindingGroupResourceDefinition;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.domain.controller.operations.SocketBindingGroupResourceDefinition;

/**
 * The older versions of the model do not allow {@code null} for the system property boottime attribute.
 * If it is {@code null}, make sure it is {@code true}
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class SocketBindingGroupTransformers {

    static ChainedTransformationDescriptionBuilder buildTransformerChain() {
        ChainedTransformationDescriptionBuilder chainedBuilder =
                createChainFromCurrent(AbstractSocketBindingGroupResourceDefinition.PATH);

        ResourceTransformationDescriptionBuilder builder =
                createBuilderFromCurrent(chainedBuilder, KernelAPIVersion.VERSION_1_8);
        builder.getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, SocketBindingGroupResourceDefinition.INCLUDES)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, SocketBindingGroupResourceDefinition.INCLUDES)
                .end();
        return chainedBuilder;
    }
}
