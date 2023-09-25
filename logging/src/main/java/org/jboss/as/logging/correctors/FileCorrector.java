/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.correctors;

import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH;
import static org.jboss.as.logging.CommonAttributes.RELATIVE_TO;

import org.jboss.as.controller.ParameterCorrector;
import org.jboss.as.controller.services.path.AbstractPathService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Corrects the {@link org.jboss.as.controller.services.path.PathResourceDefinition#RELATIVE_TO relative-to} attribute.
 * <p/>
 * Checks the {@link org.jboss.as.controller.services.path.PathResourceDefinition#PATH path} attribute for an absolute
 * path. If the path is absolute, the current {@link org.jboss.as.controller.services.path.PathResourceDefinition#RELATIVE_TO
 * relative-to} attribute is not copied over. If the path is not absolute the current {@link
 * org.jboss.as.controller.services.path.PathResourceDefinition#RELATIVE_TO relative-to} attribute is copied over.
 * <p/>
 * Date: 29.11.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class FileCorrector implements ParameterCorrector {

    public static final FileCorrector INSTANCE = new FileCorrector();

    @Override
    public ModelNode correct(final ModelNode newValue, final ModelNode currentValue) {
        if (newValue.getType() == ModelType.UNDEFINED) {
            return newValue;
        }
        if (newValue.getType() != ModelType.OBJECT || currentValue.getType() != ModelType.OBJECT) {
            return newValue;
        }
        final ModelNode newPath = newValue.get(PATH.getName());
        if (newPath.isDefined() && !AbstractPathService.isAbsoluteUnixOrWindowsPath(newPath.asString())) {
            if (currentValue.hasDefined(RELATIVE_TO.getName()) && !newValue.hasDefined(RELATIVE_TO.getName())) {
                newValue.get(RELATIVE_TO.getName()).set(currentValue.get(RELATIVE_TO.getName()));
            }
        }
        return newValue;
    }
}
