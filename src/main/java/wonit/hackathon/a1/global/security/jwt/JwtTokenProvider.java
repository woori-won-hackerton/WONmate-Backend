package wonit.hackathon.a1.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationSeconds;
    private final String issuer;
    private final JwtParser jwtParser;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-seconds}")
            long expirationSeconds,
            @Value("${jwt.issuer}") String issuer
    ) {
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException(
                    "JWT 유효 시간은 0보다 커야 합니다."
            );
        }

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT 발급자를 설정해주세요."
            );
        }

        // Base64로 인코딩된 비밀키를 디코딩
        this.secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;

        // HS256 알고리즘만 허용
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .sig()
                .clear()
                .add(Jwts.SIG.HS256)
                .and()
                .build();
    }

    public String createAccessToken(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "올바른 회원 ID가 필요합니다."
            );
        }

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new MalformedJwtException("토큰이 비어 있습니다.");
        }

        // 서명, 허용 알고리즘, 발급자, 만료 여부 검증
        Claims claims = jwtParser.parseSignedClaims(token)
                .getPayload();

        if (claims.getExpiration() == null) {
            throw new MalformedJwtException("만료 시각이 없습니다.");
        }

        String subject = claims.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new MalformedJwtException("회원 ID가 없습니다.");
        }

        try {
            if (Long.parseLong(subject) <= 0) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new MalformedJwtException(
                    "회원 ID가 올바르지 않습니다."
            );
        }

        return claims;
    }
}