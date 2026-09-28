package com.jwt.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses and inspects a JWT token without signature verification,
 * and optionally verifies it when a secret is provided.
 */
public class JwtInspector {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public record InspectionResult(
            String headerJson,
            String payloadJson,
            String signature,
            boolean isExpired,
            boolean signatureVerified,
            String verificationMessage,
            String expiresAt,
            String issuedAt,
            String notBefore,
            Analysis analysis
     ) {}

     /** A single observed issue or note about a token, with a severity. */
    public record Finding(String severity, String title, String detail) {}

     /** High-level verdict about a token's type, quality, and impersonation risk. */
    public record Analysis(
            String tokenType,
            String quality,
            boolean hasImpersonation,
            List<Finding> findings
     ) {}

    /**
     * Decodes a JWT without verifying the signature (inspect-only mode).
     */
    public static InspectionResult decode(String token) throws IllegalArgumentException {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is empty.");
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException(
                    "Invalid JWT structure: expected 3 dot-separated parts, got " + parts.length + ".");
        }

        String headerJson  = decodeBase64Json(parts[0]);
        String payloadJson = decodeBase64Json(parts[1]);
        String signature   = parts.length == 3 ? parts[2] : "(none)";

        // Parse expiry/iat/nbf from payload
        Map<?, ?> claims = parseJsonToMap(payloadJson);
        boolean expired  = isExpired(claims);
        String expiresAt = formatEpochClaim(claims.get("exp"));
        String issuedAt  = formatEpochClaim(claims.get("iat"));
        String notBefore = formatEpochClaim(claims.get("nbf"));

        return new InspectionResult(
                headerJson, payloadJson, signature,
                expired, false, "Signature not verified (inspect-only mode)",
                expiresAt, issuedAt, notBefore,
                analyze(headerJson, claims, expired)
         );
      }

    /**
     * Decodes and verifies a JWT using a HMAC secret key.
     */
    public static InspectionResult decodeAndVerify(String token, String secret) {
        try {
            // Use JJWT's unsigned parser to peek at structure first (for display)
            InspectionResult base = decode(token);

            // Now verify via JJWT
            byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes);

            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token.trim());

            return new InspectionResult(
                    base.headerJson(), base.payloadJson(), base.signature(),
                    base.isExpired(), true, "✓ Signature is VALID",
                    base.expiresAt(), base.issuedAt(), base.notBefore(),
                    base.analysis()
            );
        } catch (ExpiredJwtException e) {
            // Signature was valid but token is expired — still show decoded content
            try {
                InspectionResult base = decode(token);
                 return new InspectionResult(
                         base.headerJson(), base.payloadJson(), base.signature(),
                         true, true, "✓ Signature is VALID (token is expired)",
                         base.expiresAt(), base.issuedAt(), base.notBefore(),
                         base.analysis()
                  );
            } catch (Exception ex) {
                throw new IllegalArgumentException("Failed to parse expired token: " + ex.getMessage());
            }
        } catch (SignatureException e) {
            try {
                InspectionResult base = decode(token);
                 return new InspectionResult(
                         base.headerJson(), base.payloadJson(), base.signature(),
                         base.isExpired(), false, "✗ Signature is INVALID — " + e.getMessage(),
                         base.expiresAt(), base.issuedAt(), base.notBefore(),
                         base.analysis()
                   );
            } catch (Exception ex) {
                throw new IllegalArgumentException("Failed to parse token: " + ex.getMessage());
            }
        } catch (MalformedJwtException | UnsupportedJwtException e) {
            throw new IllegalArgumentException("Malformed JWT: " + e.getMessage());
        } catch (JwtException e) {
            throw new IllegalArgumentException("JWT error: " + e.getMessage());
        } catch (Exception e) {
            throw new IllegalArgumentException("Verification failed: " + e.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String decodeBase64Json(String part) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(part));
            // Pretty-print the JSON
            Object json = MAPPER.readValue(decoded, Object.class);
            return MAPPER.writeValueAsString(json);
        } catch (Exception e) {
            return "{ \"error\": \"Could not decode: " + e.getMessage() + "\" }";
        }
    }

    private static String padBase64(String s) {
        int remainder = s.length() % 4;
        if (remainder == 0) return s;
        return s + "=".repeat(4 - remainder);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonToMap(String json) {
        try {
            return (Map<String, Object>) MAPPER.readValue(json, LinkedHashMap.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static boolean isExpired(Map<?, ?> claims) {
        Object exp = claims.get("exp");
        if (exp instanceof Number n) {
            return Instant.now().getEpochSecond() > n.longValue();
        }
        return false;
    }

    private static String formatEpochClaim(Object value) {
        if (value instanceof Number n) {
            Instant instant = Instant.ofEpochSecond(n.longValue());
            return FORMATTER.format(instant) + "  (epoch: " + n.longValue() + ")";
        }
        return "—";
    }

    // ── token analysis ─────────────────────────────────────────────────────────

    /** Builds an Analysis of a token's type, quality, and impersonation risk. */
    private static Analysis analyze(String headerJson, Map<?, ?> claims, boolean expired) {
        Map<String, Object> header = parseJsonToMap(headerJson);
        Map<String, Object> payload = castPayload(claims);
        List<Finding> findings = new ArrayList<>();

        String alg = asString(header.get("alg"));
        String type = detectTokenType(header, payload);
        boolean impersonation = detectImpersonation(payload);

        // — algorithm —
        if (alg == null || alg.isBlank() || alg.equalsIgnoreCase("none")) {
            findings.add(new Finding("error", "Unsigned token",
                     "Algorithm is \"" + (alg == null ? "(missing)" : alg)
                     + "\". Any client can forge this token — treat it as fully untrustworthy."));
         } else if (alg.length() <= 3 || alg.equalsIgnoreCase("HS256")) {
            // HS256/HS384 are only safe with a strong secret; note the risk.
             findings.add(new Finding("warning", "HMAC signature",
                     "Algorithm " + alg + " signs with a shared secret. A weak or leaked secret "
                     + "allows anyone to forge tokens."));
         } else {
             findings.add(new Finding("info", "Asymmetric signature",
                     "Algorithm " + alg + " uses public-key (RSA/EC) verification."));
         }

         // — expiry / lifetime —
        Object exp = payload.get("exp");
        Object iat = payload.get("iat");
        if (exp == null) {
            findings.add(new Finding("error", "No expiry (exp)",
                    "Token never expires. Stolen tokens stay valid indefinitely — never use in production."));
        } else if (iat == null) {
            findings.add(new Finding("warning", "No issued-at (iat)",
                    "Without iat the original lifetime cannot be audited."));
        } else if (exp instanceof Number e && iat instanceof Number i) {
            long secs = e.longValue() - i.longValue();
            if (secs <= 0) {
                findings.add(new Finding("error", "Invalid lifetime",
                        "exp <= iat; the token was already expired when it was issued."));
            } else if (secs > 12 * 3600) {
                findings.add(new Finding("warning", "Long-lived token",
                        "Lives " + humanizeDuration(secs)
                        + ". Long lifetimes widen the window for replay of stolen tokens."));
            } else {
                findings.add(new Finding("info", "Lifetime",
                        "Valid for " + humanizeDuration(secs) + "."));
            }
        }

        if (expired) {
            findings.add(new Finding("error", "Expired",
                    "The token is past its exp; it must be rejected."));
        }

        // — required standard claims —
        if (!payload.containsKey("sub")) {
            findings.add(new Finding("warning", "No subject (sub)",
                    "Missing the \"sub\" claim that identifies the principal."));
        }
        if (!payload.containsKey("iss")) {
            findings.add(new Finding("info", "No issuer (iss)",
                    "Missing the \"iss\" claim; the issuing authority cannot be pinned."));
        }

          // — token type —
        findings.add(new Finding("info", "Detected type",
                  "This looks like " + articleFor(type) + " " + type + "."));

        if (impersonation) {
            findings.add(new Finding("warning", "User impersonation",
                    "Carries act-on-behalf-of / impersonation claims, so it can act as a "
                    + "different principal than the one who requested it."));
        }

        return new Analysis(type, grade(payload, exp, iat, expired), impersonation, findings);
    }

    /** Classifies the token into a coarse type: refresh / access / id / unknown. */
    private static String detectTokenType(Map<String, Object> header, Map<String, Object> payload) {
        String typ = asString(header.get("typ"));

        String scope = asString(payload.get("scope"));
        if (scope == null) scope = asString(payload.get("scp"));
        String grant = asString(payload.get("grant_type"));

        boolean looksRefresh =
                "RT".equalsIgnoreCase(typ)
                 || (scope != null && scope.toLowerCase().contains("refresh"))
                 || "refresh_token".equalsIgnoreCase(grant)
                 || containsImpersonationRefreshHint(payload);
        if (looksRefresh) return "refresh token";

        if ("ID_TOKEN".equalsIgnoreCase(typ) || "IdToken".equalsIgnoreCase(typ)) {
            return "id token (OAuth2/OIDC)";
        }
        if ("AT".equalsIgnoreCase(typ)) {
            return "access token";
        }

        // Heuristics when the header gives no explicit type.
        boolean hasIdentity = payload.containsKey("sub")
                || payload.containsKey("username")
                || payload.containsKey("user_name");
        boolean hasAuthz = payload.containsKey("scope") || payload.containsKey("scp")
                || payload.containsKey("roles") || payload.containsKey("authorities")
                || payload.containsKey("realm_access") || payload.containsKey("client_roles");

        if (hasIdentity && hasAuthz) return "access token";
        if (hasIdentity)             return "access token";
        if (header.containsKey("alg") && !"none".equalsIgnoreCase(asString(header.get("alg")))) {
            return "access token";
        }
        return "generic token";
    }

    /** True when a refresh-shaped grant is hinted via scope or token-name claims. */
    private static boolean containsImpersonationRefreshHint(Map<String, Object> claims) {
        for (String key : new String[]{"refresh", "refresh_token", "refreshToken"}) {
            if (claims.containsKey(key)) return true;
        }
        return false;
    }

    /**
     * Detects user-impersonation: RFC 8693 act-on-behalf-of (the "act" claim),
     * explicit impersonation claims, or an "impersonate" scope.
     */
    private static boolean detectImpersonation(Map<String, Object> claims) {
        String[] markers = {
            "act", "actor", "impersonator", "impersonator_id", "impersonated_by",
            "on_behalf_of", "onBehalfOf", "acting_as", "acting_user",
            "delegated_by", "delegatedBy", "subject_token", "subjectTokenType",
            "requester"
        };
        for (String key : markers) {
            if (claims.containsKey(key)) return true;
        }
        String scope = asString(claims.get("scope"));
        if (scope == null) scope = asString(claims.get("scp"));
        return scope != null && scope.toLowerCase().contains("impersonat");
    }

    /** Coarse A–D quality grade based on the issues found. */
    private static String grade(Map<String, Object> claims, Object exp, Object iat, boolean expired) {
        int penalty = 0;
        if (exp == null)        penalty += 3;   // no expiry is the worst fault
        if (iat == null && exp != null) penalty += 1;
        if (expired)            penalty += 2;
        if (!claims.containsKey("sub")) penalty += 2;
        if (!claims.containsKey("iss")) penalty += 1;
        if (iat instanceof Number i && exp instanceof Number e && e.longValue() - i.longValue() > 12 * 3600) {
            penalty += 1;
        }
        return switch (penalty) {
            case 0 -> "A — good";
            case 1, 2 -> "B — acceptable";
            case 3, 4 -> "C — needs attention";
            default -> "D — poor";
        };
    }

    private static Map<String, Object> castPayload(Map<?, ?> claims) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : claims.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String humanizeDuration(long seconds) {
        if (seconds < 60)        return seconds + "s";
        if (seconds < 3600)      return (seconds / 60) + "m" + (seconds % 60 > 0 ? " " + seconds % 60 + "s" : "");
        if (seconds < 86400)     return (seconds / 3600) + "h" + (seconds % 3600 > 0 ? " " + (seconds % 3600 / 60) + "m" : "");
        return (seconds / 86400) + "d" + (seconds % 86400 > 0 ? " " + (seconds % 86400 / 3600) + "h" : "");
      }

     /** Chooses "a"/"an" for the returned token-type strings. */
    private static String articleFor(String type) {
        String l = type.toLowerCase();
        return l.startsWith("a") || l.startsWith("i") ? "an" : "a";
      }
}
