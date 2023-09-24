/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.model.test.ModelTestModelDescriptionValidator;
import org.jboss.dmr.ModelNode;

/**
 * Validates the types and entries of the of the description providers in the model, as read by
 * {@code /some/path:read-resource-description(recursive=true,inherited=false,operations=true)}
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ModelDescriptionValidator extends ModelTestModelDescriptionValidator {

    /**
     * Constructor
     *
     * @param address the address of the model
     * @param description the description of the model
     * @param validationConfiguration extra configuration
     */
    public ModelDescriptionValidator(ModelNode address, ModelNode description, ValidationConfiguration validationConfiguration) {
        super(address, description, validationConfiguration);
    }

    public List<ValidationFailure> validateResource() {
        List<org.jboss.as.model.test.ModelTestModelDescriptionValidator.ValidationFailure> failures = super.validateResources();

        List<ValidationFailure> converted = new ArrayList<ModelDescriptionValidator.ValidationFailure>();
        for (org.jboss.as.model.test.ModelTestModelDescriptionValidator.ValidationFailure failure : failures) {
            converted.add(new ValidationFailure(failure));
        }
        return converted;
    }


    /**
     * Validate an attribute or parameter descriptor
     */
    public interface AttributeOrParameterArbitraryDescriptorValidator extends ModelTestModelDescriptionValidator.AttributeOrParameterArbitraryDescriptorValidator{
    }

    /**
     * Validate a resource or operation descriptor
     */
    public interface ArbitraryDescriptorValidator {
        /**
         * Validate a resource or operation arbitrary descriptor
         *
         * @param currentNode the current attribute or parameter
         * @param descriptor the current descriptor being validated
         * @return {@code null} if it passed validation, or an error message describing the problem
         */
        String validate(ModelNode currentNode, String descriptor);
    }

    /**
     * Allows extra configuration of the validation
     */
    public static class ValidationConfiguration extends ModelTestModelDescriptionValidator.ValidationConfiguration{

    }

    /**
     * Contains a validation error
     */
    public static class ValidationFailure extends ModelTestModelDescriptionValidator.ValidationFailure {
        private final ModelTestModelDescriptionValidator.ValidationFailure delegate;

        public ValidationFailure(ModelTestModelDescriptionValidator.ValidationFailure delegate) {
            this.delegate = delegate;
        }

        public int hashCode() {
            return delegate.hashCode();
        }

        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        public String toString() {
            return delegate.toString();
        }

        public ModelNode getAddress() {
            return delegate.getAddress();
        }

        public String getOperationName() {
            return delegate.getOperationName();
        }

        public String getOperationParameterName() {
            return delegate.getOperationParameterName();
        }

        public String getAttributeName() {
            return delegate.getAttributeName();
        }
    }

}
