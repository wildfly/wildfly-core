/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.module;

import org.jboss.as.server.deployment.AttachmentKey;

/**
 * Marker that indicates that the contents of a resource roots META-INF directory should be ignored.
 *
 * @author Stuart Douglas
 */
public class IgnoreMetaInfMarker {

    private static AttachmentKey<Boolean> IGNORE_META_INF = AttachmentKey.create(Boolean.class);

    public static void mark(ResourceRoot root) {
        root.putAttachment(IGNORE_META_INF, true);
    }

    public static boolean isIgnoreMetaInf(ResourceRoot resourceRoot) {
        final Boolean res = resourceRoot.getAttachment(IGNORE_META_INF);
        return res != null && res;
    }

}
