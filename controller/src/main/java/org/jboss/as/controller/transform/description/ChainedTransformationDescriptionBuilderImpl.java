/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.transform.description;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.version.Stability;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ChainedTransformationDescriptionBuilderImpl implements ChainedTransformationDescriptionBuilder {
    private final Stability stability;
    private final ModelVersion currentVersion;
    private final Map<ModelVersionPair, TransformationDescriptionBuilder> builders = new HashMap<>();

    private final PathElement element;
    ChainedTransformationDescriptionBuilderImpl(ModelVersion currentVersion, Stability stability, PathElement element) {
        this.currentVersion = currentVersion;
        this.stability = stability;
        this.element = element;
    }

    @Override
    public ResourceTransformationDescriptionBuilder createBuilder(ModelVersion fromVersion, ModelVersion toVersion) {
        ResourceTransformationDescriptionBuilder builder = new ResourceTransformationDescriptionBuilderImpl(this.stability, element);
        builders.put(new ModelVersionPair(fromVersion, toVersion), builder);
        return builder;
    }

    @Override
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
                builder = new ResourceTransformationDescriptionBuilderImpl(this.stability, element);
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
