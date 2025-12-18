package com.shop.inventoryservice.grpc;

import com.shop.inventoryservice.filter.RequestIdFilter;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Глобальный gRPC server-interceptor (inventory-service).
 *
 * <p>Читает {@code x-request-id} из metadata, кладёт в MDC под ключом {@code requestId}
 * на время обработки RPC, чтобы логи gRPC были коррелируемыми.
 */
@GrpcGlobalServerInterceptor
public class RequestIdGrpcServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> RID =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String requestId = headers.get(RID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(RequestIdFilter.MDC_KEY, requestId);

        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override public void onComplete() { try { super.onComplete(); } finally { MDC.remove(RequestIdFilter.MDC_KEY); } }
            @Override public void onCancel() { try { super.onCancel(); } finally { MDC.remove(RequestIdFilter.MDC_KEY); } }
        };
    }
}



