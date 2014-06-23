/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller.transform.description;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ChainedTransformationDescriptionBuilderImpl implements ChainedTransformationDescriptionBuilder {
    private final ModelVersion currentVersion;
    //private final Map<ModelVersion, Map<ModelVersion, LazyDescription>> versions = new HashMap<>();
    private final Map<ModelVersion, Map<ModelVersion, TransformationDescriptionBuilder>> versions = new HashMap<>();

    private final PathElement element;
    ChainedTransformationDescriptionBuilderImpl(ModelVersion currentVersion, PathElement element) {
        this.currentVersion = currentVersion;
        this.element = element;
    }

    @Override
    public ResourceTransformationDescriptionBuilder createBuilder(ModelVersion fromVersion, ModelVersion toVersion) {
        //DelegatingResourceTransformationDescriptionBuilder builder = new DelegatingResourceTransformationDescriptionBuilder(fromVersion, toVersion, element);
        ResourceTransformationDescriptionBuilder builder = new ResourceTransformationDescriptionBuilderImpl(element);
        Map<ModelVersion, TransformationDescriptionBuilder> map = versions.get(fromVersion);
        if (map == null) {
            map = new HashMap<>();
            versions.put(fromVersion, map);
        }
        //map.put(toVersion, new LazyDescription(builder));
        map.put(toVersion, builder);
        return builder;
    }

    @Override
    public TransformationDescription build(ModelVersion toVersion, ModelVersion... intermediates) {
        ModelVersion[] allVersions = new ModelVersion[intermediates.length + 2];
        allVersions[0] = currentVersion;
        allVersions[intermediates.length + 1] = toVersion;
        System.arraycopy(intermediates, 0, allVersions, 1, intermediates.length);
        Arrays.sort(allVersions, new Comparator<ModelVersion>() {
            @Override
            public int compare(ModelVersion o1, ModelVersion o2) {
                return ModelVersion.compare(o1, o2);
            }
        });

        final LinkedHashMap<ModelVersionPair, ChainedPlaceholderResolver> extraResolvers = new LinkedHashMap<>();
        for (int i = 1 ; i < allVersions.length ; i++) {
            final ModelVersion from = allVersions[i - 1];
            final ModelVersion to = allVersions[i];
            final Map<ModelVersion, TransformationDescriptionBuilder> map = versions.get(from);
            if (map == null ) {
                throw ControllerLogger.ROOT_LOGGER.noChainedTransformerBetween(from, to);
            }
            TransformationDescriptionBuilder builder = map.get(to);
            if (builder == null) {
                throw ControllerLogger.ROOT_LOGGER.noChainedTransformerBetween(from, to);
            }
            extraResolvers.put(new ModelVersionPair(from, to), ChainedPlaceholderResolver.create(builder.build()));
        }
        return new ChainedTransformingDescription(element, extraResolvers);
    }

    static class ModelVersionPair {
        private final ModelVersion fromVersion;
        private final ModelVersion toVersion;

        public ModelVersionPair(ModelVersion fromVersion, ModelVersion toVersion) {
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
        }

        @Override
        public int hashCode() {
            int result = fromVersion.hashCode();
            return 31 * result + toVersion.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || obj instanceof ModelVersionPair == false) {
                return false;
            }
            ModelVersionPair other = (ModelVersionPair)obj;
            return other.fromVersion.equals(fromVersion) && other.toVersion.equals(toVersion);
        }

        @Override
        public String toString() {
            return "ModelVersionPair [fromVersion=" + fromVersion + ", toVersion=" + toVersion + "]";
        }
    }
}
