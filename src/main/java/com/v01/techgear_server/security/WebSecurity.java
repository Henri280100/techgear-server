package com.v01.techgear_server.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.v01.techgear_server.service.CustomOAuth2UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class WebSecurity {

        private final String[] SWAGGER_WHITELIST = {
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/swagger-resources"
        };

        private final OAuth2LoginSuccessHandler OAuth2LoginSuccessHandler;
        private final OAuth2LoginFailureHandler OAuth2LoginFailureHandler;
        private final JWTtoUserConvertor jwTtoUserConvertor;
        private final KeyUtils keyUtils;
        private final PasswordEncoder passwordEncoder;
        private final UserDetailsManager userDetailsManager;
        private final CustomAuthenticationProvider authProvider;
        private final LogoutHandler logoutHandler;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http.oauth2ResourceServer(oauth2 -> oauth2
                                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwTtoUserConvertor)))
                                .csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests(authorize -> authorize
                                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                                        .requestMatchers("/api/v01/auth/**").permitAll()
                                        .requestMatchers("/api/v01/user/**").hasAnyRole("USER", "ADMIN")
                                        .requestMatchers("/api/v01/admin/**").hasRole("ADMIN")
                                        .anyRequest().authenticated().and())

                                .formLogin(formLogin -> formLogin.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authenticationProvider(daoAuthenticationProvider())
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                                                .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
                                .oauth2Login(oauth2Login -> oauth2Login
                                                .loginPage("/api/v01/auth/login")
                                                .defaultSuccessUrl("/", true)
                                                .authorizationEndpoint(
                                                                authEndpoint -> authEndpoint.baseUri(
                                                                                "/api/v01/auth/oauth2/authorization")) // Access
                                                // authorization
                                                // endpoint
                                                // configuration
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(oAuth2UserService()))
                                                .successHandler(OAuth2LoginSuccessHandler)
                                                .failureHandler(OAuth2LoginFailureHandler))
                                .logout(logout -> logout.logoutUrl("/api/v01/auth/logout")
                                                .addLogoutHandler(logoutHandler)
                                                .logoutSuccessHandler((request, response,
                                                                authentication) -> SecurityContextHolder
                                                                                .clearContext())
                                                .invalidateHttpSession(true)
                                                .deleteCookies("JSESSIONID", "refreshToken"))
                                ;

                return http.build();
        }

        @Bean
        public PermissionEvaluator permissionEvaluator() {
                return new CustomPermissionEvaluator();
        }

        @Bean
        public DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler() {
                DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
                expressionHandler.setPermissionEvaluator(permissionEvaluator());
                return expressionHandler;
        }

        @Bean
        public LogoutHandler customLogoutHandler() {
                return new SecurityContextLogoutHandler();
        }

        // Custom OAuth2 user service for Facebook and GG OAuth2 login
        @Bean
        public OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService() {
                return new CustomOAuth2UserService(); // Custom implementation of DefaultOAuth2UserService
        }

        @Bean
        public AuthenticationManager authenticationManager(HttpSecurity security) throws Exception {
                AuthenticationManagerBuilder authenticationManagerBuilder = security
                                .getSharedObject(AuthenticationManagerBuilder.class);
                authenticationManagerBuilder.authenticationProvider(authProvider);
                return authenticationManagerBuilder.build();
        }

        @Bean
        @Primary
        JwtDecoder jwtAccessTokenDecoder() {
                return NimbusJwtDecoder.withPublicKey(keyUtils.getAccessTokenPublicKey()).build();
        }

        @Bean
        @Primary
        JwtEncoder jwtAccessTokenEncoder() {
                JWK jwk = new RSAKey.Builder(keyUtils.getAccessTokenPublicKey())
                                .privateKey(keyUtils.getAccessTokenPrivateKey())
                                .build();

                JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));

                return new NimbusJwtEncoder(jwks);

        }

        @Bean
        @Qualifier("jwtRefreshTokenDecoder")
        JwtDecoder jwtRefreshTokenDecoder() {

                return NimbusJwtDecoder.withPublicKey(keyUtils.getRefreshTokenPublicKey()).build();

        }

        @Bean
        @Qualifier("jwtRefreshTokenEncoder")
        JwtEncoder jwtRefreshTokenEncoder() {
                JWK jwk = new RSAKey.Builder(keyUtils.getRefreshTokenPublicKey())

                                .privateKey(keyUtils.getRefreshTokenPrivateKey()).build();

                JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));

                return new NimbusJwtEncoder(jwks);

        }

        @Bean
        @Qualifier("jwtRefreshTokenAuthProvider")
        public JwtAuthenticationProvider jwtRefreshTokenAuthProvider(JwtDecoder jwtDecoder) {
                return new JwtAuthenticationProvider(jwtDecoder);
        }

        @Bean
        DaoAuthenticationProvider daoAuthenticationProvider() {
                DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
                provider.setPasswordEncoder(passwordEncoder);
                provider.setUserDetailsService(userDetailsManager);
                return provider;
        }

}
