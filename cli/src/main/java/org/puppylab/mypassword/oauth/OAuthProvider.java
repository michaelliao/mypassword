package org.puppylab.mypassword.oauth;

public interface OAuthProvider {

    String name();

    String uri(String clientId, String challenge);

    String verify(String token);

}
