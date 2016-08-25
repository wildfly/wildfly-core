/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ChainedTransformationDescriptionBuilderImpl implements ChainedTransformationDescriptionBuilder {
    private final ModelVersion currentVersion;
    private final Map<ModelVersionPair, TransformationDescriptionBuilder> builders = new HashMap<>();

    private final PathElement element;
    ChainedTransformationDescriptionBuilderImpl(ModelVersion currentVersion, PathElement element) {
        this.currentVersion = currentVersion;
        this.element = element;
    }

    @Override
    public ResourceTransformationDescriptionBuilder createBuilder(ModelVersion fromVersion, ModelVersion toVersion) {
        ResourceTransformationDescriptionBuilder builder = new ResourceTransformationDescriptionBuilderImpl(element);
        builders.put(new ModelVersionPair(fromVersion, toVersion), builder);
        return builder;
    }

    public Map<ModelVersion, TransformationDescription> build(ModelVersion...versions) {
        ModelVersion[] allVersions = new ModelVersion[versions.length + 1];
        allVersions[0] = currentVersion;
        System.arraycopy(versions, 0, allVersions, 1, versions.length);
        Arrays.sort(allVersions, new Comparator<ModelVersion>() {
            @Override
            public int compare(ModelVersion o1, ModelVersion o2) {
                return ModelVersion.compare(o1, o2);
            }
        });
        return doBuild(allVersions);
    }


    @Override
    public void buildAndRegister(SubsystemRegistration registration, ModelVersion[]...chains) {
        for (ModelVersion[] chain : chains) {
            for (Map.Entry<ModelVersion, TransformationDescription> entry : build(chain).entrySet()) {
                TransformationDescription.Tools.register(entry.getValue(), registration, entry.getKey());
            }
        }
    }

    @Override
    public void buildAndRegister(SubsystemTransformerRegistration registration, ModelVersion[]...chains) {
        for (ModelVersion[] chain : chains) {
            for (Map.Entry<ModelVersion, TransformationDescription> entry : build(chain).entrySet()) {
                TransformationDescription.Tools.register(entry.getValue(), registration, entry.getKey());
            }
        }
    }


    private Map<ModelVersion, TransformationDescription> doBuild(ModelVersion...versions) {
        final Map<ModelVersion, TransformationDescription> result = new HashMap<>();
        final LinkedHashMap<ModelVersionPair, ChainedPlaceholderResolver> placeholderResolvers = new LinkedHashMap<>();
        for (int i = 1 ; i < versions.length ; i++) {
            ModelVersionPair pair = new ModelVersionPair(versions[i - 1], versions[i]);
            TransformationDescriptionBuilder builder = builders.get(pair);
            if (builder == null) {
                //Insert an empty builder in the chain for version deltas which don't have one registered
                builder = new ResourceTransformationDescriptionBuilderImpl(element);
            }
            placeholderResolvers.put(pair, ChainedPlaceholderResolver.create(builder.build()));
            TransformationDescription desc = new ChainedTransformingDescription(element, new LinkedHashMap<>(placeholderResolvers));
            result.put(pair.toVersion, desc);
        }


        return result;
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
