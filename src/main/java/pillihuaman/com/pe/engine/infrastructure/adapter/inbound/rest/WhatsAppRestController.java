package pillihuaman.com.pe.engine.infrastructure.adapter.inbound.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import pillihuaman.com.pe.engine.application.port.inbound.WhatsAppUseCase;
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
    public Mono<RespBase<String>> getQrCode(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "DEFAULT");
            return whatsAppUseCase.generateLinkQr(tenantId, bearerToken)
                    .map(qr -> new RespBase<String>().ok(qr));
        });
    }

    @GetMapping("/private/v1/whatsapp/status")
    public Mono<RespBase<String>> getConnectionStatus(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault(TenantWebFilter.TENANT_KEY, "DEFAULT");
            return whatsAppUseCase.getLinkState(tenantId, bearerToken)
                    .map(state -> new RespBase<String>().ok(state));
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

            // >>> CHANGE
            log.info("[WhatsAppRestController] Outgoing request initiated. Tenant: {}, Recipient: {}, Text length: {}",
                    tenantId, recipient, text != null ? text.length() : 0);

            return whatsAppUseCase.sendOutgoingMessage(tenantId, recipient, text, bearerToken)
                    .map(msg -> {
                        log.info("[WhatsAppRestController] Outgoing message translated & dispatched successfully. ID: {}", msg.id());
                        return new RespBase<WhatsAppMessage>().ok(msg);
                    })
                    .doOnError(err -> log.error("[WhatsAppRestController] Critical failure during sendTranslated stream: {}",
                            err.getMessage(), err));
            // <<< CHANGE
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
}