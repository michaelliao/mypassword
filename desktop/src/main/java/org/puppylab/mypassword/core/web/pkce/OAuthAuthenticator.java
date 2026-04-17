package org.puppylab.mypassword.core.web.pkce;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import org.puppylab.mypassword.core.HttpDaemon;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.core.entity.RecoveryConfig;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.rpc.VaultException;
import org.puppylab.mypassword.util.Base64Utils;
import org.puppylab.mypassword.util.EncryptUtils;
import org.puppylab.mypassword.util.HashUtils;
import org.puppylab.mypassword.util.HttpUtils;
import org.puppylab.mypassword.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

public abstract class OAuthAuthenticator {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final String              provider;
    protected final Map<String, Object> config;

    // in recovery mode?
    boolean isRecover;
    // current code verifier:
    String codeVerifier = null;

    @SuppressWarnings("unchecked")
    public OAuthAuthenticator() {
        // XyzAuthenticator -> xyz:
        String provider = getClass().getSimpleName();
        provider = provider.substring(0, provider.length() - 13);
        this.provider = provider.toLowerCase();
        // load config:
        RecoveryConfig rc = VaultManager.getCurrent().getRecoveryConfig(this.provider);
        this.config = JsonUtils.fromJson(rc.oauth_config_json, Map.class);
        logger.info("Load oauth provider {}: {}", provider, rc.oauth_config_json);
    }

    public synchronized String startOAuth(boolean isRecover) {
        this.isRecover = isRecover;
        // generate code challenge:
        this.codeVerifier = Base64Utils.b64(EncryptUtils.generateKey());
        byte[] digest = HashUtils.sha256(codeVerifier.getBytes(StandardCharsets.UTF_8));
        String code_challenge = Base64Utils.b64(digest);
        Map<String, String> query = Map.of("client_id", (String) config.getOrDefault("client_id", ""), // client id
                "response_type", "code", // response type
                "prompt", "login", // force input password
                "max_age", "0", // disable session
                "scope", getScope(), // scope
                "code_challenge", code_challenge, // challenge
                "code_challenge_method", "S256", // sha-256
                "redirect_uri", getRedirectUri());
        return HttpUtils.appendQuery(getAuthUrl(), query);
    }

    protected String getRedirectUri() {
        return "http://127.0.0.1:" + HttpDaemon.PORT + "/oauth/" + this.provider + "/callback";
    }

    public String getProvider() {
        return this.provider;
    }

    public boolean isRecoverMode() {
        return this.isRecover;
    }

    protected abstract String getAuthUrl();

    protected abstract String getTokenUrl();

    protected abstract String getScope();

    public synchronized OAuthUser exchangeOAuthId(String code) {
        Map<String, String> query = Map.of("client_id", (String) config.getOrDefault("client_id", ""), // client id
                "client_secret", (String) config.getOrDefault("client_secret", ""), // ignore if client secret is empty
                "code", code, // received code
                "code_verifier", this.codeVerifier, // last stored code verifier
                "grant_type", "authorization_code", // grant_type
                "redirect_uri", getRedirectUri());
        // clear code verifier after use:
        this.codeVerifier = null;
        // send http post:
        String result = HttpUtils.postForm(getTokenUrl(), query, Map.of("Accept", "application/json"));
        OAuthResult oauth = JsonUtils.fromJson(result, OAuthResult.class);
        OAuthUser user = processOAuthResult(oauth);
        return user;
    }

    protected OAuthUser processOAuthResult(OAuthResult oauth) {
        if (oauth.id_token != null) {
            String jwt = oauth.id_token;
            JwtUser jwtUser;
            try {
                jwtUser = decodeJWTUser(jwt);
            } catch (Exception e) {
                logger.error("Parse JWT failed.", e);
                throw new VaultException(ErrorCode.BAD_REQUEST, "Cannot parse JWT.");
            }
            OAuthUser user = new OAuthUser();
            user.provider = this.provider;
            user.oauthId = jwtUser.sub;
            user.email = jwtUser.email;
            user.name = jwtUser.name;
            return user;
        }
        return null;
    }

    protected JwtUser decodeJWTUser(String jwt) throws Exception {
        String cert_url = (String) config.get("jwks");
        if (cert_url == null || cert_url.isEmpty()) {
            logger.warn("Missing jwks and cannot verify JWT signature!");
            String payload = new String(Base64Utils.b64(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
            return JsonUtils.fromJson(payload, JwtUser.class);
        }
        logger.info("try load jwks: {}", cert_url);
        URL jwksUrl = URI.create(cert_url).toURL();
        JWKSource<SecurityContext> keySource = JWKSourceBuilder.create(jwksUrl).build();
        ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
        jwtProcessor.setJWSKeySelector(keySelector);
        jwtProcessor.setJWTClaimsSetVerifier(
                new DefaultJWTClaimsVerifier<>(new JWTClaimsSet.Builder().issuer((String) config.get("issuer")).build(),
                        new HashSet<>(Arrays.asList("sub", "iat", "exp"))));
        JWTClaimsSet claimsSet = jwtProcessor.process(jwt, null);
        JwtUser jwtUser = new JwtUser();
        jwtUser.sub = claimsSet.getSubject();
        jwtUser.email = claimsSet.getStringClaim("email");
        jwtUser.name = claimsSet.getStringClaim("name");
        return jwtUser;
    }

    protected String toQueryString(Map<String, String> query) {
        StringBuilder sb = new StringBuilder();
        for (String key : query.keySet()) {
            String value = query.get(key);
            if (value != null && !value.isEmpty()) {
                sb.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&');
            }
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    @SuppressWarnings("resource")
    protected String loadProperty(String key) {
        if (props == null) {
            Properties p = new Properties();
            try {
                p.load(getClass().getResourceAsStream("/oauth.properties"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            props = p;
        }
        return props.getProperty(key);
    }

    static Properties props = null;

    public static class OAuthResult {
        public String token_type;
        public String access_token;
        public String scope;
        public String id_token;
    }

    public static class JwtUser {
        public String iss;
        public String sub;
        public String email;
        public String name;
    }
}
