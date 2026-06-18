package pillihuaman.com.pe.engine.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import pillihuaman.com.pe.engine.infrastructure.security.model.MyJsonWebToken;
import pillihuaman.com.pe.engine.infrastructure.security.model.ResponseUser;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith((SecretKey) getSignInKey()).build().parseSignedClaims(token).getPayload();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @SuppressWarnings("unchecked")
    public MyJsonWebToken parseTokenToMyJsonWebToken(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return null;
        }
        String token = bearerToken.substring(7);
        Claims claims;
        try {
            claims = extractAllClaims(token);
        } catch (Exception e) {
            return null;
        }

        MyJsonWebToken myJsonWebToken = new MyJsonWebToken();
        Map<String, Object> userMap = (Map<String, Object>) claims.get("user");
        String tenantIdFromToken = (String) claims.get("tenantId");
        List<Map<String, Object>> rolesFromToken = (List<Map<String, Object>>) claims.get("role");

        if (userMap != null) {
            ResponseUser user = new ResponseUser();
            Object rawId = userMap.get("id");
            if (rawId != null) {
                user.setId(new ObjectId(rawId.toString()));
            }
            user.setMail((String) userMap.get("email"));
            user.setAlias((String) userMap.get("alias"));

            if (tenantIdFromToken != null) {
                user.setTenantId(tenantIdFromToken.replace("\"", ""));
            }

            if (rolesFromToken != null) {
                user.setRoles(rolesFromToken.stream().map(roleMap -> (String) roleMap.get("name")).toList());
            }
            myJsonWebToken.setUser(user);
        }
        return myJsonWebToken;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        // >>> CHANGE
        final Claims claims = extractAllClaims(token);
        Object permissions = claims.get("permissions");
        return permissions instanceof List ? (List<String>) permissions : Collections.emptyList();
        // <<< CHANGE
    }
}
