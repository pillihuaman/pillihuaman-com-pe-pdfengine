package pillihuaman.com.pe.pdfengine.infrastructure.security;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class TenantWebFilter implements WebFilter {
  public static final String TENANT_KEY = "tenantId";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");
    return chain
        .filter(exchange)
        .contextWrite(Context.of(TENANT_KEY, tenantId != null ? tenantId : "DEFAULT"));
  }
}
