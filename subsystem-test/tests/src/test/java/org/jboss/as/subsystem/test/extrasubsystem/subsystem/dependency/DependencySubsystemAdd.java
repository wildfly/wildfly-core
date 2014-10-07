package org.jboss.as.subsystem.test.extrasubsystem.subsystem.dependency;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class DependencySubsystemAdd extends AbstractBoottimeAddStepHandler {

    static final DependencySubsystemAdd INSTANCE = new DependencySubsystemAdd();

    private DependencySubsystemAdd() {
    }

    /** {@inheritDoc} */
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        model.setEmptyObject();
    }

    /** {@inheritDoc} */
    @Override
    public void performBoottime(OperationContext context, ModelNode operation, Resource resource)
            throws OperationFailedException {
        Dependency dependency = new Dependency();
        context.getServiceTarget().addService(Dependency.NAME, dependency).install();
    }
}
