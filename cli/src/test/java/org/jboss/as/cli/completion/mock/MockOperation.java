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
package org.jboss.as.cli.completion.mock;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author Alexey Loubyansky
 */
public class MockOperation {

    public static class Property {
        private final String name;
        private final int index;

        public Property(String name, int index) {
            this.name = name;
            this.index = index;
        }

        public String getName() {
            return name;
        }

        public int getIndex() {
            return index;
        }
    }

    private final String name;

    private List<Property> properties = Collections.emptyList();

    public MockOperation(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setPropertyNames(List<String> parameterNames) {
        properties = parameterNames.stream().map(n->new Property(n, -1)).collect(Collectors.toList());
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public List<Property> getProperties() {
        return properties;
    }
}
