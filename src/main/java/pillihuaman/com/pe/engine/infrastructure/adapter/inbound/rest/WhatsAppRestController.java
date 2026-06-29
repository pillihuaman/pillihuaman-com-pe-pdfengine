package pillihuaman.com.pe.engine.infrastructure.adapter.inbound.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import pillihuaman.com.pe.engine.application.port.inbound.WhatsAppUseCase;
import pillihuaman.com.pe.engine.domain.model.ChannelStateDTO;
import pillihuaman.com.pe.engine.domain.model.WhatsAppContact;
import pillihuaman.com.pe.engine.domain.model.WhatsAppMessage;
import pillihuaman.com.pe.engine.infrastructure.common.RespBase;
import pillihuaman.com.pe.engine.infrastructure.security.TenantWebFilter;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.reflections.Reflections.log;

@RestController
@RequiredArgsConstructor
public class WhatsAppRestController {
    private final WhatsAppUseCase whatsAppUseCase;

    @GetMapping("/private/v1/whatsapp/qr")
    public Mono<RespBase<Map<String, Object>>> getQrCode(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "DEFAULT");
            return whatsAppUseCase.generateLinkQr(tenantId, bearerToken)
                    .map(qr -> new RespBase<Map<String, Object>>().ok(qr));
        });
    }

    @PostMapping(value = {
            "/public/v1/whatsapp/webhook",
            "/public/v1/whatsapp/webhook/**"
    }, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RespBase<WhatsAppMessage>> incomingWebhook(
            @RequestBody final Map<String, Object> payload,
            final ServerWebExchange exchange) {

        final String path = exchange.getRequest().getPath().value();
        log.info("[WH-RECEIVE] Path: {} | Event: {}", path, payload.get("event"));

        return whatsAppUseCase.handleIncomingWebhook(payload)
                .map(msg -> new RespBase<WhatsAppMessage>().ok(msg))
                .defaultIfEmpty(new RespBase<WhatsAppMessage>().ok(null));
    }

    @GetMapping("/private/v1/whatsapp/status")
    public Mono<RespBase<ChannelStateDTO>> getConnectionStatus(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "DEFAULT");
            return whatsAppUseCase.getLinkState(tenantId, bearerToken)
                    .map(state -> new RespBase<ChannelStateDTO>().ok(state));
        });
    }

    @PostMapping("/private/v1/whatsapp/contact")
    public Mono<RespBase<WhatsAppContact>> saveContact(
            @RequestBody WhatsAppContact contact) {
        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "DEFAULT");
            return whatsAppUseCase.registerOrUpdateContact(tenantId, contact)
                    .map(saved -> new RespBase<WhatsAppContact>().ok(saved));
        });
    }

    @PostMapping(value = "/public/v1/whatsapp/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RespBase<WhatsAppMessage>> incomingWebhook(@RequestBody Map<String, Object> payload) {
        return whatsAppUseCase.handleIncomingWebhook(payload)
                .map(msg -> new RespBase<WhatsAppMessage>().ok(msg));
    }

    @GetMapping("/private/v1/whatsapp/contacts")
    public Mono<RespBase<List<WhatsAppContact>>> getContacts() {
        return Mono.deferContextual(ctx -> {
            final String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "DEFAULT");
            return whatsAppUseCase.getContacts(tenantId)
                    .map(contacts -> new RespBase<List<WhatsAppContact>>().ok(contacts));
        });
    }

    @GetMapping("/private/v1/whatsapp/chat/{phoneNumber}")
    public Mono<RespBase<List<WhatsAppMessage>>> getChatHistory(@PathVariable final String phoneNumber) {
        return Mono.deferContextual(ctx -> {
            final String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "DEFAULT");
            return whatsAppUseCase.getChatHistory(tenantId, phoneNumber)
                    .map(messages -> new RespBase<List<WhatsAppMessage>>().ok(messages));
        });
    }

    @PostMapping("/private/v1/whatsapp/send")
    public Mono<RespBase<WhatsAppMessage>> sendTranslated(
            @RequestBody Map<String, String> request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "DEFAULT");
            String recipient = request.get("recipient");
            String text = request.get("text");
            String mediaBase64 = request.get("mediaBase64");
            String mimeType = request.get("mimeType");
            String fileName = request.get("fileName");

            log.info("[WhatsAppRestController] Outgoing request. Tenant: {}, Recipient: {}, isMedia: {}",
                    tenantId, recipient, mediaBase64 != null && !mediaBase64.isBlank());

            return whatsAppUseCase.sendOutgoingMessage(
                            tenantId, recipient, text, mediaBase64, mimeType, fileName, bearerToken)
                    .map(msg -> {
                        log.info("[WhatsAppRestController] Dispatched successfully. ID: {}", msg.id());
                        return new RespBase<WhatsAppMessage>().ok(msg);
                    })
                    .doOnError(err -> log.error("[WhatsAppRestController] Send failure: {}", err.getMessage()));
        });
    }
}