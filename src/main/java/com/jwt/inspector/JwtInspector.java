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
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
     * Decodes a JWT and verifies its signature.
     *
     * Verification is algorithm-aware:
     *   - HMAC (HS256/384/512) tokens are verified with the provided {@code secret}.
      *   - asymmetric (RSA/ECDS/EdDSA, e.g. RS256/ES256/PS256) tokens are verified
      *     against the issuer's public key, fetched from the {@code iss} claim's JWKS
      *     endpoint. Most OIDC providers rotate keys, so the token's {@code kid} is used
      *     to select the matching key.
     */
    public static InspectionResult decodeAndVerify(String token, String secret) {
        InspectionResult base;
        try {
            base = decode(token);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed JWT: " + e.getMessage());
        }

        Map<String, Object> header = parseJsonToMap(base.headerJson());
        Map<String, Object> claims = parseJsonToMap(base.payloadJson());
        String alg = asString(header.get("alg"));
        String kid = asString(header.get("kid"));
        String iss = asString(claims.get("iss"));

        io.jsonwebtoken.JwtParser parser;
        String keyNote;
        try {
            if (isHmacAlgorithm(alg)) {
                byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                var key = Keys.hmacShaKeyFor(keyBytes);
                parser = Jwts.parser().verifyWith(key).build();
                keyNote = "HMAC secret";
             } else {
                PublicKey publicKey = resolveAsymmetricKey(iss, kid, alg);
                if (publicKey == null) {
                    throw new IllegalArgumentException(
                         "Cannot verify " + alg + " signature: no issuer JWKS reachable"
                         + (iss == null ? "" : " at " + jwksUrl(iss))
                         + ". Paste the public key, or inspect without verification.");
                 }
                parser = Jwts.parser().verifyWith(publicKey).build();
                keyNote = "issuer JWKS" + (kid != null ? " (kid=" + kid + ")" : "");
             }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve verification key: " + e.getMessage());
        }

        try {
            parser.parseSignedClaims(token.trim());
            return new InspectionResult(
                    base.headerJson(), base.payloadJson(), base.signature(),
                    base.isExpired(), true, "✓ Signature is VALID (via " + keyNote + ")",
                    base.expiresAt(), base.issuedAt(), base.notBefore(),
                    base.analysis()
            );
        } catch (ExpiredJwtException e) {
            // Signature was valid but token is expired — still show decoded content
            return new InspectionResult(
                    base.headerJson(), base.payloadJson(), base.signature(),
                    true, true, "✓ Signature is VALID via " + keyNote + " (token is expired)",
                    base.expiresAt(), base.issuedAt(), base.notBefore(),
                    base.analysis()
            );
        } catch (SignatureException e) {
            return new InspectionResult(
                    base.headerJson(), base.payloadJson(), base.signature(),
                    base.isExpired(), false, "✗ Signature is INVALID (via " + keyNote + ")",
                    base.expiresAt(), base.issuedAt(), base.notBefore(),
                    base.analysis()
            );
        } catch (MalformedJwtException | UnsupportedJwtException e) {
            throw new IllegalArgumentException("Malformed JWT: " + e.getMessage());
        } catch (JwtException e) {
            throw new IllegalArgumentException("JWT error: " + e.getMessage());
        } catch (Exception e) {
            throw new IllegalArgumentException("Verification failed: " + e.getMessage());
        }
    }

    /** True when the algorithm signs with a shared (symmetric) secret. */
    private static boolean isHmacAlgorithm(String alg) {
        return alg != null && alg.toUpperCase(Locale.ROOT).startsWith("HS");
    }

    /** Derives a provider's JWKS endpoint from its issuer URL. */
    private static String jwksUrl(String iss) {
        if (iss == null || iss.isBlank()) return null;
        String base = iss.trim();
        // Cognito / Cognito-IdP expose keys at <iss>/.well-known/jwks.json
        if (base.contains("cognito")) {
            return base.replaceFirst("/+$", "") + "/.well-known/jwks.json";
        }
        // Generic OIDC discovery, with the standard issuer-suffix trimmed off.
        return base + "/.well-known/jwks.json";
    }

    /** Fetches and parses the issuer's JWKS, returning the key matching {@code kid}. */
    private static PublicKey resolveAsymmetricKey(String iss, String kid, String alg) {
        String url = jwksUrl(iss);
        if (url == null) return null;

        String jwksJson;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                              .timeout(java.time.Duration.ofSeconds(10))
                              .GET()
                              .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return null;
            jwksJson = resp.body();
        } catch (Exception e) {
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = parseJsonToMap(jwksJson);
            Object keysObj = root.get("keys");
            if (!(keysObj instanceof List<?> keys)) return null;

            for (Object keyObj : keys) {
                if (!(keyObj instanceof Map<?, ?> raw)) continue;
                Map<String, Object> k = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : raw.entrySet()) {
                    k.put(String.valueOf(e.getKey()), e.getValue());
                }
                if (kid != null && !kid.equals(k.get("kid"))) continue;
                if (k.get("use") != null && !"sig".equals(k.get("use"))) continue;
                try {
                    PublicKey pk = buildPublicKey((String) alg, k);
                    if (pk != null) return pk;
                } catch (Exception ignored) {
                    // try the next candidate key
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /** Builds a JCA PublicKey from a JWK entry, keyed on the token's algorithm. */
    private static PublicKey buildPublicKey(String alg, Map<String, Object> jwk) throws Exception {
        String kty = String.valueOf(jwk.getOrDefault("kty", "RSA"));
        String algUpper = alg == null ? "" : alg.toUpperCase(Locale.ROOT);

        if (kty.equalsIgnoreCase("RSA") || algUpper.startsWith("RS") || algUpper.startsWith("PS")) {
            RSAPublicKeySpec spec = new RSAPublicKeySpec(
                    new BigInteger(1, fromBase64Url((String) jwk.get("n"))),
                    new BigInteger(1, fromBase64Url((String) jwk.get("e"))));
            return KeyFactory.getInstance("RSA").generatePublic(spec);
         }

        if (kty.equalsIgnoreCase("EC") || algUpper.startsWith("ES") || algUpper.startsWith("ED")) {
            String crv = String.valueOf(jwk.get("crv"));
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec(crv));
            ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);
            ECPublicKeySpec spec = new ECPublicKeySpec(
                    new java.security.spec.ECPoint(
                            new BigInteger(1, fromBase64Url((String) jwk.get("x"))),
                            new BigInteger(1, fromBase64Url((String) jwk.get("y")))),
                    ecSpec);
            return KeyFactory.getInstance("EC").generatePublic(spec);
         }
        return null;
    }

    private static byte[] fromBase64Url(String s) {
        return Base64.getUrlDecoder().decode(padBase64(s));
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
