/*
 * Copyright 2018 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
