/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.capability;

import java.util.function.Function;
import org.jboss.as.controller.PathAddress;

/**
 * Utility class defining name mappers.
 * @author Emmanuel Hugonnet (c) 2018 Red Hat, inc.
 */
public class DynamicNameMappers {

    private static class SimpleNameMapper implements Function<PathAddress, String[]> {
        private SimpleNameMapper() {
        }

        @Override
        public String[] apply(PathAddress pathAddress) {
            return new String[]{
                pathAddress.getLastElement().getValue()
            };
        }
    }

    private static class ParentNameMapper implements Function<PathAddress, String[]> {
        private ParentNameMapper() {
        }

        @Override
        public String[] apply(PathAddress pathAddress) {
            return new String[]{
                pathAddress.getParent().getLastElement().getValue(),
                pathAddress.getLastElement().getValue()
            };
        }
    }

    private static class GrandParentNameMapper implements Function<PathAddress, String[]> {
        private GrandParentNameMapper() {
        }

        @Override
        public String[] apply(PathAddress pathAddress) {
            return new String[]{
                pathAddress.getParent().getParent().getLastElement().getValue(),
                pathAddress.getParent().getLastElement().getValue(),
                pathAddress.getLastElement().getValue()
            };
        }
    }
    public static final Function<PathAddress, String[]> SIMPLE = new SimpleNameMapper();

    public static final Function<PathAddress, String[]> PARENT = new ParentNameMapper();

    public static final Function<PathAddress, String[]> GRAND_PARENT = new GrandParentNameMapper();
}
