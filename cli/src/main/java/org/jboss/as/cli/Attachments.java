/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 *
 * @author jdenise@redhat.com
 */
public final class Attachments {

    public static final Attachments IMMUTABLE_ATTACHMENTS = new Attachments(true);

    private final boolean immutable;
    private final List<String> paths = new ArrayList<>();
    private final List<Consumer<Attachments>> listeners = new ArrayList<>();
    private Attachments(boolean immutable) {
        this.immutable = immutable;
    }

    public Attachments() {
        this(false);
    }

    public int addFileAttachment(String path) {
        if (immutable) {
            throw new UnsupportedOperationException("Attachments can't be mutated");
        }

        paths.add(path);
        return paths.size() - 1;
    }

    public List<String> getAttachedFiles() {
        return Collections.unmodifiableList(paths);
    }

    public void addConsumer(Consumer<Attachments> listener) {
        listeners.add(listener);
    }

    public void done() {
        for (Consumer<Attachments> l : listeners) {
            l.accept(this);
        }
    }
}
