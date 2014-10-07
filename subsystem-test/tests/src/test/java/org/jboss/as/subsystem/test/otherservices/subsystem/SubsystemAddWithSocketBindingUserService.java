package org.jboss.as.subsystem.test.otherservices.subsystem;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;

/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SubsystemAddWithSocketBindingUserService extends AbstractBoottimeAddStepHandler {

    public static final SubsystemAddWithSocketBindingUserService INSTANCE = new SubsystemAddWithSocketBindingUserService();

    private SubsystemAddWithSocketBindingUserService() {
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

        SocketBindingUserService mine = new SocketBindingUserService();
        context.getServiceTarget().addService(SocketBindingUserService.NAME, mine)
            .addDependency(SocketBinding.JBOSS_BINDING_NAME.append("test2"), SocketBinding.class, mine.socketBindingValue)
            .install();

    }
}
