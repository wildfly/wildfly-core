/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
