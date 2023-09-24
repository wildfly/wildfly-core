/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.descriptions;

import org.jboss.as.controller.DeprecationData;

/**
 * Default provider for a resource "remove" operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DefaultResourceRemoveDescriptionProvider extends DefaultOperationDescriptionProvider {

    public DefaultResourceRemoveDescriptionProvider(final ResourceDescriptionResolver descriptionResolver) {
        super(ModelDescriptionConstants.REMOVE, descriptionResolver);
    }

    public DefaultResourceRemoveDescriptionProvider(ResourceDescriptionResolver resolver, DeprecationData deprecationData) {
        super(ModelDescriptionConstants.REMOVE, resolver, deprecationData);
    }

    @Override
    protected boolean isAddAccessConstraints() {
        //This is auto-generated so don't add any access constraints
        return false;
    }
}
