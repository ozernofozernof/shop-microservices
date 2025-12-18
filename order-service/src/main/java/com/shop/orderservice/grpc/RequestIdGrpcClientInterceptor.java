package com.shop.orderservice.grpc;

import com.shop.orderservice.filter.RequestIdFilter;
import io.grpc.*;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.slf4j.MDC;

/**
 * Глобальный gRPC client-interceptor (order-service).
 *
 * <p>Прокидывает {@code requestId} из MDC в gRPC metadata ключом {@code x-request-id}.
 */
@GrpcGlobalClientInterceptor
public class RequestIdGrpcClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> RID =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String requestId = MDC.get(RequestIdFilter.MDC_KEY);
                if (requestId != null && !requestId.isBlank()) {
                    headers.put(RID, requestId);
                }
                super.start(responseListener, headers);
            }
        };
    }
}



