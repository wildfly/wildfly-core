/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management.deployment;

import org.jboss.as.server.deployment.AttachmentKey;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.ClassInfoScanner;

class UnstableApiAnnotationAttachments {
    static final AttachmentKey<ClassInfoScanner> UNSTABLE_API_ANNOTATION_SCANNER = AttachmentKey.create(ClassInfoScanner.class);

    static final AttachmentKey<Boolean> UNSTABLE_API_ANNOTATIONS_SCANNED = AttachmentKey.create(Boolean.class);

}
