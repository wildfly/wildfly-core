/*
* JBoss, Home of Professional Open Source.
* Copyright 2012, Red Hat Middleware LLC, and individual contributors
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
