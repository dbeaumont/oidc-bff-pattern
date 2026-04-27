package com.enterprise.bff.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class WebClientConfig {

    @Value("${api.base-url}")
    private String apiBaseUrl;

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientRepository authorizedClientRepository
    ) {
        DefaultOAuth2AuthorizedClientManager manager = new DefaultOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientRepository
        );
        manager.setAuthorizedClientProvider(
            OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build()
        );
        return manager;
    }

    /**
     * RestClient qui injecte automatiquement le Bearer token depuis la session côté serveur.
     * Le token ne transite jamais vers le navigateur.
     */
    @Bean
    public RestClient apiRestClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        return RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestInterceptor((request, body, execution) -> {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null) {
                    ServletRequestAttributes attrs = (ServletRequestAttributes)
                        RequestContextHolder.currentRequestAttributes();
                    OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                        .withClientRegistrationId("keycloak")
                        .principal(auth)
                        .attributes(a -> {
                            a.put(HttpServletRequest.class.getName(), attrs.getRequest());
                            if (attrs.getResponse() != null) {
                                a.put(HttpServletResponse.class.getName(), attrs.getResponse());
                            }
                        })
                        .build();
                    OAuth2AuthorizedClient client = authorizedClientManager.authorize(authorizeRequest);
                    if (client != null) {
                        request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
                    }
                }
                return execution.execute(request, body);
            })
            .build();
    }
}
