package com.xqbase.bn.transport.http.client;

import com.xqbase.bn.common.callback.Callback;
import com.xqbase.bn.m2.Request;
import com.xqbase.bn.m2.RequestContext;
import com.xqbase.bn.m2.Response;
import com.xqbase.bn.m2.http.HttpRequest;
import com.xqbase.bn.transport.apool.AsyncPool;
import com.xqbase.bn.transport.apool.impl.AsyncPoolImpl;
import com.xqbase.bn.transport.apool.impl.NoopCreateLatch;
import com.xqbase.bn.transport.apool.util.Cancellable;
import com.xqbase.bn.transport.apool.util.None;
import com.xqbase.bn.transport.bridge.client.*;
import com.xqbase.bn.transport.bridge.common.TimeoutTransportCallback;
import com.xqbase.bn.transport.bridge.common.TransportCallback;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HttpClient based on Netty.
 *
 * @author Tony He
 */
public class HttpNettyClient implements TransportClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpNettyClient.class);
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    private final ChannelPoolManager poolManager;
    private final ChannelGroup allChannels = new DefaultChannelGroup("Transport Channels", GlobalEventExecutor.INSTANCE);

    private final ScheduledExecutorService timeoutSchedule;
    private final ExecutorService callbackExecutor;

    private final int requestTimeout;
    private final String name;

    private final AtomicReference<State> stateRef = new AtomicReference<>(State.RUNNING);
    private enum State { RUNNING, SHUTTING_DOWN, SHUT_DOWN }

    public HttpNettyClient(ScheduledExecutorService timeoutSchedule,
                           ExecutorService callbackExecutor,
                           int requestTimeout,
                           int idleTimeout,
                           int poolSize,
                           int maxWaiters,
                           String name,
                           int minPoolSize,
                           AsyncPoolImpl.Strategy strategy) {
        this.name = name;
        this.requestTimeout = requestTimeout;
        this.timeoutSchedule = timeoutSchedule;
        this.callbackExecutor = callbackExecutor;
        this.poolManager = new ChannelPoolManager(new ChannelPoolFactoryImpl(
                new Bootstrap(),
                poolSize,
                minPoolSize,
                idleTimeout,
                maxWaiters,
                strategy
        ), name);
    }

    private void writeRequestWithTimeout(HttpRequest request, RequestContext requestContext, TransportCallback<Response> callback) {
        TimeoutTransportCallback<Response> timeoutCallback = new TimeoutTransportCallback<>(timeoutSchedule, callbackExecutor,
                requestTimeout, TimeUnit.MICROSECONDS, callback);
    }

    private void writeRequest(final HttpRequest request, RequestContext requestContext, final TimeoutTransportCallback callback) {
        State state = stateRef.get();
        if (state != State.RUNNING) {
            errorResponse(callback, new IllegalStateException("Client is " + state));
            return;
        }

        URI uri = request.getUri();
        if (null == uri) {
            errorResponse(callback, new IllegalArgumentException("URI cannot be null"));
            return;
        }
        String schema = uri.getScheme();
        if (!schema.equalsIgnoreCase("http") && !schema.equalsIgnoreCase("https")) {
            errorResponse(callback, new IllegalArgumentException("Unknown schema: " + schema + " only http/https support"));
            return;
        }
        String host = uri.getHost();
        int port = uri.getPort();
        if (-1 == port) {
            port = schema.equalsIgnoreCase("http") ? DEFAULT_HTTP_PORT : DEFAULT_HTTPS_PORT;
        }

        SocketAddress address = new InetSocketAddress(host, port);
        final AsyncPool<Channel> pool;
        try {
            pool = poolManager.getPoolForAddress(address);
        } catch (IllegalStateException e) {
            errorResponse(callback, e);
            return;
        }

        // Now we get the channel pool
        final Cancellable pendingGet = pool.get(new Callback<Channel>() {
            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onSuccess(final Channel channel) {
                final ChannelPoolHandler poolHandler = channel.pipeline().get(ChannelPoolHandler.class);
                final ChannelHandlerContext channelHandlerContext = channel.pipeline().context(ChannelPoolHandler.class);
                poolHandler.setAttachment(channelHandlerContext, pool);
                callback.addTimeoutTask(new Runnable() {
                    @Override
                    public void run() {
                        // get channel from channel pool time out
                        AsyncPool<Channel> asyncPool = (AsyncPool<Channel>) poolHandler.removeAttachment(channelHandlerContext);
                        if (asyncPool != null) {
                            asyncPool.dispose(channel);
                        }
                    }
                });

                channel.pipeline().get(HttpResponseHandler.class).setAttachment(channel.pipeline().context(HttpResponseHandler.class),
                        callback);

                final State s = stateRef.get();
                if (s == State.SHUT_DOWN) {

                }
                channel.write(request);
            }
        });
        if (pendingGet != null) {
            callback.addTimeoutTask(new Runnable() {
                @Override
                public void run() {
                    pendingGet.cancel();
                }
            });
        }
    }

    @Override
    public void request(Request request, RequestContext requestContext, TransportCallback<Response> callback) {

    }

    @Override
    public void shutdown(Callback<None> callback) {

    }

    static <T> void errorResponse(TransportCallback<T> callback, Throwable e) {
        callback.onResponse(null);
    }

    private class ChannelPoolFactoryImpl implements ChannelPoolFactory {

        private final Bootstrap clientBootstrap;
        private final int maxSize;
        private final int minSize;
        private final long idleTimeout;
        private final int maxWaiterSize;
        private final AsyncPoolImpl.Strategy strategy;

        private ChannelPoolFactoryImpl(Bootstrap clientBootstrap, int maxSize, int minSize,
                    long idleTimeout, int maxWaiterSize, AsyncPoolImpl.Strategy strategy) {
            this.clientBootstrap = clientBootstrap;
            this.maxSize = maxSize;
            this.minSize = minSize;
            this.idleTimeout = idleTimeout;
            this.maxWaiterSize = maxWaiterSize;
            this.strategy = strategy;
        }

        @Override
        public AsyncPool<Channel> getPool(SocketAddress address) {
            return new AsyncPoolImpl<>("Http Channel Pool",
                                                maxSize,
                                                idleTimeout,
                                                new ChannelPoolLifeCycle(
                                                        clientBootstrap,
                                                        address,
                                                        allChannels
                                                ),
                                                timeoutSchedule,
                                                callbackExecutor,
                                                maxWaiterSize,
                                                minSize,
                                                new NoopCreateLatch(),
                                                strategy);
        }
    }
}
