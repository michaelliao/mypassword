package org.puppylab.mypassword.core.web.pkce;

public class OAuthUser {
    public String provider;
    public String oauthId;
    public String name;
    public String email;

    @Override
    public String toString() {
        return "OAuthUser [provider=" + provider + ", oauthId=" + oauthId + ", name=" + name + ", email=" + email + "]";
    }
}
