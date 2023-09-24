/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.module;

import org.jboss.as.server.deployment.AttachmentKey;

/**
 * Marker for module roots. These are resource roots that are added to the module.
 *
 * @author Stuart Douglas
 *
 */
public class ModuleRootMarker {
    private static final AttachmentKey<Boolean> MODULE_ROOT_MARKER = AttachmentKey.create(Boolean.class);

    public static void mark(ResourceRoot attachable) {
        attachable.putAttachment(MODULE_ROOT_MARKER, true);
    }

    public static void mark(ResourceRoot attachable, boolean value) {
        attachable.putAttachment(MODULE_ROOT_MARKER, value);
    }

    public static boolean isModuleRoot(ResourceRoot resourceRoot) {
        Boolean res = resourceRoot.getAttachment(MODULE_ROOT_MARKER);
        return res != null && res;
    }

    private ModuleRootMarker() {

    }
}
