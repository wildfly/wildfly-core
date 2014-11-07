package org.wildfly.core.testrunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * A lightweight test runner for running management based tests
 *
 * @author Stuart Douglas
 */
public class WildflyTestRunner extends BlockJUnit4ClassRunner {

    private final ServerController controller = new ServerController();
    private final boolean automaticServerControl;
    private final List<ServerSetupTask> serverSetupTasks = new LinkedList<>();

    /**
     * Creates a BlockJUnit4ClassRunner to run {@code klass}
     *
     * @throws org.junit.runners.model.InitializationError if the test class is malformed.
     */
    public WildflyTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
        if (klass.isAnnotationPresent(ServerControl.class)) {
            ServerControl serverControl = klass.getAnnotation(ServerControl.class);
            automaticServerControl = !serverControl.manual();
        }else{
            automaticServerControl = true;
        }
        startServerIfRequired();
        doInject(klass, null);
        prepareSetupTasks(klass);
    }

    private void doInject(Class<?> klass, Object instance) {
        Class c = klass;
        try {
            while (c != null && c != Object.class) {
                for (Field field : c.getDeclaredFields()) {
                    if ((instance == null && Modifier.isStatic(field.getModifiers()) ||
                            instance != null && !Modifier.isStatic(field.getModifiers()))) {
                        if (field.isAnnotationPresent(Inject.class)) {
                            field.setAccessible(true);
                            if (field.getType() == ManagementClient.class && controller.isStarted()) {
                                field.set(instance, controller.getClient());
                            } else if (field.getType() == ModelControllerClient.class && controller.isStarted()) {
                                field.set(instance, controller.getClient().getControllerClient());
                            } else if (field.getType() == ServerController.class) {
                                field.set(instance, controller);
                            }
                        }
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject", e);
        }
    }

    @Override
    protected Object createTest() throws Exception {
        Object res = super.createTest();
        doInject(getTestClass().getJavaClass(), res);
        return res;
    }

    @Override
    public void run(final RunNotifier notifier){
        notifier.addListener(new RunListener() {
            @Override
            public void testRunFinished(Result result) throws Exception {
                super.testRunFinished(result);
                if (automaticServerControl) {
                    controller.stop();
                }
            }
        });
        startServerIfRequired();
        if (!serverSetupTasks.isEmpty() && !automaticServerControl) {
            throw new RuntimeException("Can't run setup tasks with manual server control");
        }
        if (automaticServerControl) {
            runSetupTasks();
        }
        super.run(notifier);
        if (automaticServerControl) {
            runTearDownTasks();
        }
    }

    private void runSetupTasks() {
        for (ServerSetupTask task : serverSetupTasks) {
            try {
                task.setup(controller.getClient());
            } catch (Exception e) {
                throw new RuntimeException(String.format("Could not run setup task '%s'", task), e);
            }
        }
    }

    private void runTearDownTasks() {
        List<ServerSetupTask> reverseServerSetupTasks = new LinkedList<>(serverSetupTasks);
        Collections.reverse(reverseServerSetupTasks);
        for (ServerSetupTask task : reverseServerSetupTasks) {
            try {
                task.tearDown(controller.getClient());
            } catch (Exception e) {
                throw new RuntimeException(String.format("Could not run tear down task '%s'", task), e);
            }
        }
    }

    private void prepareSetupTasks(Class<?> klass) throws InitializationError {
        try {
            if (klass.isAnnotationPresent(ServerSetup.class)) {
                ServerSetup serverSetup = klass.getAnnotation(ServerSetup.class);
                for (Class<? extends ServerSetupTask> clazz : serverSetup.value()) {
                    Constructor<? extends ServerSetupTask> ctor = clazz.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    serverSetupTasks.add(ctor.newInstance());
                }
            }
        } catch (Exception e) {
            throw new InitializationError(e);
        }
    }

    private void startServerIfRequired() {
        if (automaticServerControl) {
            controller.start();
        }
    }
}
