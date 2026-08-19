package com.example.frenchlearning.security;

import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;

/**
 * Applies T029 bearer problem handlers to the current {@link HttpSecurity} chain without defining
 * route authorization or method security.
 */
public final class BearerProblemHttpConfigurer
        extends AbstractHttpConfigurer<BearerProblemHttpConfigurer, HttpSecurity> {

    @Override
    public void init(HttpSecurity http) throws Exception {
        apply(http);
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        apply(http);
    }

    private void apply(HttpSecurity http) throws Exception {
        ApplicationContext context = http.getSharedObject(ApplicationContext.class);
        BearerAuthenticationEntryPoint authenticationEntryPoint =
                context.getBean(BearerAuthenticationEntryPoint.class);
        BearerAccessDeniedHandler accessDeniedHandler =
                context.getBean(BearerAccessDeniedHandler.class);

        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

        @SuppressWarnings("unchecked")
        OAuth2ResourceServerConfigurer<HttpSecurity> oauth2 =
                http.getConfigurer(OAuth2ResourceServerConfigurer.class);
        if (oauth2 != null) {
            oauth2.authenticationEntryPoint(authenticationEntryPoint);
            oauth2.accessDeniedHandler(accessDeniedHandler);
        } else {
            http.httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint));
        }
    }
}
