package org.jboss.as.server.deployment;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * Service listener that ensures that deployment services are only started a single time, and if a
 * restart is attempted the deployment is redeployed.
 * <p/>
 * This is basically a work around to the fact that deployment does not really map to the MSC idea of
 * repeatability, and it seems unlikely that is will be possible to make repeatability work without
 * significant complexity and additional memory usage
 *
 * @author Stuart Douglas
 */
public class DeploymentRestartListener extends AbstractServiceListener<Object> {

    private final DeploymentUnit deploymentUnit;

    public DeploymentRestartListener(DeploymentUnit deploymentUnit) {
        this.deploymentUnit = deploymentUnit;
    }

    @Override
    public void transition(ServiceController<?> serviceController, ServiceController.Transition transition) {
        if (transition.getAfter() == ServiceController.Substate.STARTING) {
            serviceController.removeListener(this);
            //we add another listener after the first start. This listener will force deployment restart
            serviceController.addListener(new AbstractServiceListener<Object>() {
                @Override
                public void transition(ServiceController<?> serviceController, ServiceController.Transition transition) {
                    if (transition.getAfter() == ServiceController.Substate.STARTING) {
                        ServerLogger.DEPLOYMENT_LOGGER.deploymentRestartDetected(deploymentUnit.getName());
                        //this only happens on deployment restart, which we don't support at the moment.
                        //instead we are going to restart the complete deployment.

                        //we get the deployment unit service name
                        //add a listener to perform a restart when the service goes down
                        //then stop the deployment unit service
                        final ServiceName serviceName;
                        if (deploymentUnit.getParent() == null) {
                            serviceName = deploymentUnit.getServiceName();
                        } else {
                            serviceName = deploymentUnit.getParent().getServiceName();
                        }
                        serviceController.setMode(ServiceController.Mode.NEVER);
                        ServiceController<?> controller = serviceController.getServiceContainer().getRequiredService(serviceName);
                        controller.addListener(new AbstractServiceListener<Object>() {

                            @Override
                            public void transition(final ServiceController<?> controller, final ServiceController.Transition transition) {
                                if (transition.getAfter().equals(ServiceController.Substate.DOWN)) {
                                    controller.setMode(ServiceController.Mode.ACTIVE);
                                    controller.removeListener(this);
                                }
                            }
                        });
                        controller.setMode(ServiceController.Mode.NEVER);
                    }
                }
            });
        }
    }
}
