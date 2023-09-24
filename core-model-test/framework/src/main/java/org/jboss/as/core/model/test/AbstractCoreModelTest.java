/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import java.io.IOException;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.model.test.ModelFixer;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AbstractCoreModelTest {

    private final CoreModelTestDelegate delegate;

    protected AbstractCoreModelTest() {
        delegate = new CoreModelTestDelegate(this.getClass());
    }

    @Before
    public void initializeParser() throws Exception {
        delegate.initializeParser();
    }

    @After
    public void cleanup() throws Exception {
        delegate.cleanup();
        delegate.setCurrentTransformerClassloaderParameter(null);
    }

    CoreModelTestDelegate getDelegate() {
        return delegate;
    }

    protected KernelServicesBuilder createKernelServicesBuilder(TestModelType type) {
        return delegate.createKernelServicesBuilder(type);
    }

    /**
     * Checks that the transformed model is the same as the model built up in the legacy subsystem controller via the transformed operations,
     * and that the transformed model is valid according to the resource definition in the legacy subsystem controller.
     *
     * @param kernelServices the main kernel services
     * @param modelVersion   the model version of the targeted legacy subsystem
     * @return the whole model of the legacy controller
     */
    protected ModelNode checkCoreModelTransformation(KernelServices kernelServices, ModelVersion modelVersion) throws IOException {
        return checkCoreModelTransformation(kernelServices, modelVersion, new RbacModelFixer(modelVersion), null);
    }

    /**
     * Checks that the transformed model is the same as the model built up in the legacy subsystem controller via the transformed operations,
     * and that the transformed model is valid according to the resource definition in the legacy subsystem controller.
     *
     * @param kernelServices the main kernel services
     * @param modelVersion   the model version of the targeted legacy subsystem
     * @param legacyModelFixer use to touch up the model read from the legacy controller, use sparingly when the legacy model is just wrong. May be {@code null}
     * @return the whole model of the legacy controller
     */
    protected ModelNode checkCoreModelTransformation(KernelServices kernelServices, ModelVersion modelVersion, ModelFixer legacyModelFixer, ModelFixer transformedModelFixer) throws IOException {
        return delegate.checkCoreModelTransformation(kernelServices, modelVersion, legacyModelFixer, transformedModelFixer);
    }

    /**
     * Checks that the result was successful
     *
     * @param result the result to check
     * @return the result contents
     */
    protected static ModelNode checkOutcome(ModelNode result) {
        return ModelTestUtils.checkOutcome(result);
    }

    public static class RbacModelFixer implements ModelFixer {

        private final ModelVersion transformFromVersion;

        public RbacModelFixer(ModelVersion transformFromVersion) {
            this.transformFromVersion = transformFromVersion;
        }

        @Override
        public ModelNode fixModel(ModelNode modelNode) {
            ModelNode result = transformFromVersion.getMajor() < 9
                    ? fixVaultConstraint(fixSensitivityConstraint(fixApplicationConstraint(modelNode)))
                    : modelNode;
            if (!modelNode.hasDefined("core-service","management","access","authorization","constraint","sensitivity-classification","type", "core", "classification", "credential")) {
                throw new IllegalArgumentException(modelNode.toString());
            }
            return result;
        }

        private ModelNode fixApplicationConstraint(ModelNode modelNode) {
            if (modelNode.hasDefined("core-service","management","access","authorization","constraint","application-classification","type")) {
                ModelNode typeNode = modelNode.get("core-service","management","access","authorization","constraint","application-classification","type");
                for (String type : typeNode.keys()) {
                    if (typeNode.hasDefined(type, "classification")) {
                        ModelNode classificationNode = typeNode.get(type, "classification");
                        for (String classification : classificationNode.keys()) {
                            ModelNode target = classificationNode.get(classification);
                            if (target.has("default-application")) {
                                target.remove("default-application");
                            }
                        }
                    }
                }
            }
            return modelNode;
        }

        private ModelNode fixSensitivityConstraint(ModelNode modelNode) {
            if (modelNode.hasDefined("core-service","management","access","authorization","constraint","sensitivity-classification","type")) {
                ModelNode typeNode = modelNode.get("core-service","management","access","authorization","constraint","sensitivity-classification","type");
                for (String type : typeNode.keys()) {
                    if (typeNode.hasDefined(type, "classification")) {
                        ModelNode classificationNode = typeNode.get(type, "classification");
                        if ("core".equals(type)) {
                            if (!classificationNode.hasDefined("credential") || !classificationNode.hasDefined("domain-controller")) {
                                throw new IllegalArgumentException(classificationNode.toString());
                            }
                        }
                        for (String classification : classificationNode.keys()) {
                            ModelNode target = classificationNode.get(classification);
                            if (target.has("default-requires-addressable")) {
                                target.remove("default-requires-addressable");
                            }
                            if (target.has("default-requires-read")) {
                                target.remove("default-requires-read");
                            }
                            if (target.has("default-requires-write")) {
                                target.remove("default-requires-write");
                            }
                        }
                        if ("core".equals(type)) {
                            if (!classificationNode.hasDefined("credential") || !classificationNode.hasDefined("domain-controller")) {
                                throw new IllegalArgumentException(classificationNode.toString());
                            }
                        }
                    }
                }
            } else throw new IllegalArgumentException(modelNode.toString());
            return modelNode;
        }

        private ModelNode fixVaultConstraint(ModelNode modelNode) {
            if (modelNode.hasDefined("core-service","management","access","authorization","constraint","vault-expression")) {
                ModelNode target = modelNode.get("core-service","management","access","authorization","constraint","vault-expression");
                if (target.has("default-requires-addressable")) {
                    target.remove("default-requires-addressable");
                }
                if (target.has("default-requires-read")) {
                    target.remove("default-requires-read");
                }
                if (target.has("default-requires-write")) {
                    target.remove("default-requires-write");
                }
            }
            return modelNode;

        }
    }
}
