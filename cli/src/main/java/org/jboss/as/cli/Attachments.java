/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
