package org.puppylab.mypassword.core.web.pkce;

public class MicrosoftAuthenticator extends OAuthAuthenticator {

    @Override
    protected String getScope() {
        return "openid email profile";
    }

    @Override
    protected String getAuthUrl() {
        return "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
    }

    @Override
    protected String getTokenUrl() {
        return "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    }
}
