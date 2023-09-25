/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.repository.ContentReference;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;

/**
 * ContentReference built from the Management Model.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ModelContentReference {

    public static final ContentReference fromDeploymentName(String name, String hash) {
        return new ContentReference(PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT, name)).toCLIStyleString(),
                hash);
    }

    public static final ContentReference fromDeploymentName(String name, byte[] hash) {
        return fromDeploymentName(name, HashUtil.bytesToHexString(hash));
    }

    public static final ContentReference fromModelAddress(PathAddress address, byte[] hash) {
        return new ContentReference(address.toCLIStyleString(), hash);
    }
}
