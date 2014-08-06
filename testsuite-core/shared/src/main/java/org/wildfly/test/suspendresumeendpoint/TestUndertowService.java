package org.wildfly.test.suspendresumeendpoint;

import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.as.server.requestcontroller.ControlPoint;
import org.jboss.as.server.requestcontroller.GlobalRequestController;
import org.jboss.as.server.requestcontroller.RunResult;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

import io.undertow.Undertow;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
public class TestUndertowService implements Service<TestUndertowService> {


    private static final AtomicInteger COUNT = new AtomicInteger();


    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("test-undertow-server");
    public static final String SKIP_GRACEFUL = "skip-graceful";

    private volatile Undertow undertow;
    private final InjectedValue<GlobalRequestController> requestControllerInjectedValue = new InjectedValue<>();
    private final InjectedValue<SocketBindingManager> socketBindingManagerInjectedValue = new InjectedValue<>();

    @Override
    public void start(StartContext context) throws StartException {        //add graceful shutdown support
        final SuspendResumeHandler suspendResumeHandler = new SuspendResumeHandler();
        final ControlPoint controlPoint = requestControllerInjectedValue.getValue().getEntryPoint("test", "test");
        HttpHandler shutdown = new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                if(exchange.isInIoThread()) {
                    exchange.dispatch(this);
                    return;
                }
                final int count = COUNT.incrementAndGet();
                if(exchange.getQueryParameters().containsKey(SKIP_GRACEFUL)) {
                    //bit of a hack, allows to send in some requests even when the server is paused
                    //very useful for testing
                    System.out.println("Skipping request " + count + " " + exchange);
                    suspendResumeHandler.handleRequest(exchange);
                    return;
                }
                System.out.println("Attempting " + count + " " + exchange);
                RunResult result = controlPoint.beginRequest();
                if (result == RunResult.REJECTED) {
                    System.out.println("Rejected " + count + " " + exchange);
                    exchange.setResponseCode(503);
                    return;
                }
                exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                    @Override
                    public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                        System.out.println("Completed " + count + " " + exchange);
                        controlPoint.requestComplete();
                        nextListener.proceed();
                    }
                });
                suspendResumeHandler.handleRequest(exchange);
            }
        };
        undertow = Undertow.builder().addHttpListener(8080 + socketBindingManagerInjectedValue.getValue().getPortOffset(), "0.0.0.0").setHandler(shutdown).build();
        undertow.start();
    }

    @Override
    public void stop(StopContext context) {
        undertow.stop();
        undertow = null;
    }

    @Override
    public TestUndertowService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public InjectedValue<GlobalRequestController> getRequestControllerInjectedValue() {
        return requestControllerInjectedValue;
    }

    public InjectedValue<SocketBindingManager> getSocketBindingManagerInjectedValue() {
        return socketBindingManagerInjectedValue;
    }
}
