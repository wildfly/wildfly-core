/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.modules.ClassTransformer;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Marius Bogoevici
 */
public class DelegatingClassTransformer implements ClassTransformer {

    private final List<ClassTransformer> delegateTransformers = new CopyOnWriteArrayList<ClassTransformer>();

    public static final AttachmentKey<DelegatingClassTransformer> ATTACHMENT_KEY = AttachmentKey.create(DelegatingClassTransformer.class);

    private volatile boolean active = false;

    public DelegatingClassTransformer() {
    }

    public void addTransformer(ClassTransformer classTransformer) {
        delegateTransformers.add(classTransformer);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public ByteBuffer transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, ByteBuffer originalBuffer) throws IllegalArgumentException {
        ByteBuffer transformedBuffer = originalBuffer;
        if (active) {
            for (ClassTransformer transformer : delegateTransformers) {
                ByteBuffer result = transformer.transform(loader, className, protectionDomain, transformedBuffer);
                if (result != null) {
                    transformedBuffer = result;
                }
            }
        }
        return transformedBuffer;
    }
}
