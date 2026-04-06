package org.puppylab.mypassword.core.web.pkce;

import java.util.Map;

import org.puppylab.mypassword.util.HttpUtils;
import org.puppylab.mypassword.util.JsonUtils;

public class GithubAuthenticator extends OAuthAuthenticator {

    public GithubAuthenticator() {
        super("github");
    }

    @Override
    protected String getScope() {
        return "user:email";
    }

    @Override
    protected OAuthUser processOAuthResult(OAuthResult oauth) {
        String accessToken = oauth.access_token;
        if (accessToken != null) {
            Map<String, String> headers = Map.of("Authorization", "Bearer " + accessToken, "Accept",
                    "application/vnd.github+json");
            String userResult = HttpUtils.get("https://api.github.com/user", null, headers);
            logger.info("get user: {}", userResult);
            GithubUser ghUser = JsonUtils.fromJson(userResult, GithubUser.class);
            OAuthUser user = new OAuthUser();
            user.provider = "github";
            user.oauthId = ghUser.id;
            user.name = ghUser.login;
            user.email = "";
            // try get github user's email:
            String userProfile = HttpUtils.get("https://api.github.com/user/emails", null, headers);
            logger.info("get user: {}", userProfile);
            GithubEmail[] ghEmails = JsonUtils.fromJson(userProfile, GithubEmail[].class);
            for (GithubEmail ghEmail : ghEmails) {
                if (ghEmail.primary) {
                    user.email = ghEmail.email;
                }
            }
            return user;
        }
        return null;
    }

    public static class GithubUser {
        public String id;
        public String login;
        public String name;
    }

    public static class GithubEmail {
        public String  email;
        public boolean primary;
    }
}
