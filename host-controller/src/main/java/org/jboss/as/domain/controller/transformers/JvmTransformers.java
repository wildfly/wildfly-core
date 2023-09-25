/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.transformers;

import static org.jboss.as.host.controller.model.jvm.JvmAttributes.LAUNCH_COMMAND;

import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.host.controller.model.jvm.JvmAttributes;
import org.jboss.as.host.controller.model.jvm.JvmResourceDefinition;

/**
 * The older versions of the model do not allow expressions for the jmv resource's attributes.
 * Reject the attributes if they contain an expression.
 *
 * @author <a href="http://jmesnil.net">Jeff Mesnil</a> (c) 2012 Red Hat, inc
 */
class JvmTransformers {


    public static void registerTransformers2_1_AndBelow(ResourceTransformationDescriptionBuilder parent) {
        parent.addChildResource(JvmResourceDefinition.GLOBAL.getPathElement())
            .getAttributeBuilder()
            .setDiscard(DiscardAttributeChecker.UNDEFINED, LAUNCH_COMMAND)
            .addRejectCheck(RejectAttributeChecker.DEFINED, LAUNCH_COMMAND)
        .end();
    }


    public static void registerTransformers13_AndBelow(ResourceTransformationDescriptionBuilder parent) {
        parent.addChildResource(JvmResourceDefinition.GLOBAL.getPathElement())
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, JvmAttributes.MODULE_OPTIONS)
                .addRejectCheck(RejectAttributeChecker.DEFINED, JvmAttributes.MODULE_OPTIONS)
                .end();
        parent.addChildResource(JvmResourceDefinition.SERVER.getPathElement())
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, JvmAttributes.MODULE_OPTIONS)
                .addRejectCheck(RejectAttributeChecker.DEFINED, JvmAttributes.MODULE_OPTIONS)
                .end();
    }
}
