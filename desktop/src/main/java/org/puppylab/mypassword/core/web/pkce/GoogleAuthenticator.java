package org.puppylab.mypassword.core.web.pkce;

public class GoogleAuthenticator extends OAuthAuthenticator {

    public GoogleAuthenticator() {
        super("google");
    }

    @Override
    protected String getScope() {
        return "openid email profile";
    }

    @Override
    protected OAuthUser processOAuthResult(OAuthResult oauth) {
        if (oauth.id_token != null) {
            String[] parts = oauth.id_token.split("\\.");
            JwtUser jwtUser = decodeJWT(parts[1], JwtUser.class);
            OAuthUser user = new OAuthUser();
            user.provider = "google";
            user.oauthId = jwtUser.sub;
            user.email = jwtUser.email;
            user.name = jwtUser.name;
            return user;
        }
        return null;
    }

    public static class JwtUser {
        public String iss;
        public String sub;
        public String email;
        public String name;
    }

}
