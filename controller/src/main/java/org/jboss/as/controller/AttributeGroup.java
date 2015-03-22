/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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


package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 *  Group of an {@link org.jboss.as.controller.AttributeDefinition} which consist of a list of {@link java.lang.String} elements.
 *
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
public final class AttributeGroup implements Comparable<AttributeGroup>, Iterable<String> {

    private final List<String> elements;

    public AttributeGroup(final String... elements) {
        this.elements = new LinkedList<>();
        for (final String element : elements) {
            this.elements.add(element);
        }
    }

    public AttributeGroup(final Iterable<ModelNode> elements) {
        this.elements = new LinkedList<>();
        for (final ModelNode element : elements) {
            this.elements.add(element.asString());
        }
    }

    @Override
    public int compareTo(final AttributeGroup o) {
        final Iterator<String> ai = this.elements.iterator();
        final Iterator<String> bi = o.elements.iterator();
        while (ai.hasNext() || bi.hasNext()) {
            if (!ai.hasNext()) {
                return -1;
            }
            if (!bi.hasNext()) {
                return 1;
            }
            int r = ai.next().compareTo(bi.next());
            if (r != 0) {
                return r;
            }
        }
        return 0;
    }

    @Override
    public Iterator<String> iterator() {
        return elements.iterator();
    }

    @Override
    public String toString() {
        return String.join(".", elements);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        for (String s : elements) {
            result = result * prime + s.hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof AttributeGroup)) {
            return false;
        } else {
            return this.compareTo((AttributeGroup) o) == 0;
        }
    }
}
