package pillihuaman.com.pe.engine.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtReactiveFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);


        String jwt = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        } else {
            // Fallback: check "token" query parameter for WebSocket handshakes
            jwt = exchange.getRequest().getQueryParams().getFirst("token");
        }

        if (jwt == null || jwt.isBlank()) {
            return chain.filter(exchange);
        }
  
        try {
            if (jwtTokenProvider.isTokenExpired(jwt)) {
                return chain.filter(exchange);
            }

            String username = jwtTokenProvider.extractUsername(jwt);
            List<String> permissions = jwtTokenProvider.extractPermissions(jwt);

            List<SimpleGrantedAuthority> authorities =
                    permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);

            return chain
                    .filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));

        } catch (Exception e) {
            return chain.filter(exchange);
        }
    }


}
