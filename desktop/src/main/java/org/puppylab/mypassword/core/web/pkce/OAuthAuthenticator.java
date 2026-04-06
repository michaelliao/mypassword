package org.puppylab.mypassword.core.web.pkce;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import org.puppylab.mypassword.core.entity.RecoveryConfig;
import org.puppylab.mypassword.util.Base64Utils;
import org.puppylab.mypassword.util.EncryptUtils;
import org.puppylab.mypassword.util.HashUtils;
import org.puppylab.mypassword.util.HttpUtils;
import org.puppylab.mypassword.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class OAuthAuthenticator {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    final String provider;
    final String clientId;
    final String clientSecret;

    // current code verifier:
    String codeVerifier = null;

    public OAuthAuthenticator(RecoveryConfig rc) {
        this.provider = rc.oauth_provider;
        this.clientId = rc.oauth_client_id;
        this.clientSecret = rc.oauth_client_secret;
        logger.info("Load oauth provider {}: client id: {}", provider, clientId);
    }

    public synchronized String startOAuth() {
        // generate code challenge:
        this.codeVerifier = Base64Utils.b64(EncryptUtils.generateKey());
        byte[] digest = HashUtils.sha256(codeVerifier.getBytes(StandardCharsets.UTF_8));
        String code_challenge = Base64Utils.b64(digest);
        Map<String, String> query = Map.of("client_id", this.clientId, // client id
                "response_type", "code", // response type
                "scope", getScope(), // scope
                "code_challenge", code_challenge, // challenge
                "code_challenge_method", "S256", // sha-256
                "redirect_uri", "http://127.0.0.1:27432/oauth/" + this.provider + "/callback");
        return HttpUtils.appendQuery(getAuthUrl(), query);
    }

    protected abstract String getAuthUrl();

    protected abstract String getTokenUrl();

    protected abstract String getScope();

    public synchronized OAuthUser exchangeOAuthId(String code) {
        Map<String, String> query = Map.of("client_id", this.clientId, // client id
                "client_secret", this.clientSecret == null ? "" : this.clientSecret, // ignore if client secret is empty
                "code", code, // received code
                "code_verifier", this.codeVerifier, // last stored code verifier
                "grant_type", "authorization_code", // grant_type
                "redirect_uri", "http://127.0.0.1:27432/oauth/" + this.provider + "/callback");
        // clear code verifier after use:
        this.codeVerifier = null;
        // send http post:
        String result = HttpUtils.postForm(getTokenUrl(), query, Map.of("Accept", "application/json"));
        logger.info("Post result: {}", result);
        OAuthResult oauth = JsonUtils.fromJson(result, OAuthResult.class);
        OAuthUser user = processOAuthResult(oauth);
        logger.info("OAuth user: {}", user);
        return user;
    }

    protected abstract OAuthUser processOAuthResult(OAuthResult oauth);

    protected <T> T decodeJWT(String s, Class<T> clazz) {
        String payload = new String(Base64Utils.b64(s), StandardCharsets.UTF_8);
        return JsonUtils.fromJson(payload, clazz);
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
        public String access_token;
        public String scope;
        public String token_type;
        public String id_token;
    }
}
