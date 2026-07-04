package com.fleetpulse.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Configurazione della sicurezza per l'API Gateway.
 * <p>
 * Il gateway agisce come Resource Server OAuth2: valida i token JWT in arrivo
 * prima di inoltrare le richieste ai microservizi interni. In questo modo i
 * servizi downstream non devono preoccuparsi della validazione del token,
 * perche' il gateway lo fa gia' per loro.
 * </p>
 * <p>
 * Regole di autorizzazione:
 * <ul>
 *   <li>{@code /iam/api/v1/auth/**} — accessibile senza token (login, register)</li>
 *   <li>{@code /iam/swagger-ui/**} e {@code /iam/v3/api-docs/**} — accessibili senza token</li>
 *   <li>Tutti gli altri percorsi — richiedono un token JWT valido</li>
 * </ul>
 * </p>
 * <p>
 * Il JWT e' firmato con HMAC-SHA256 utilizzando una chiave segreta condivisa
 * con l'auth-service (stessa chiave per emissione e validazione).
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Crea un decoder per validare i token JWT firmati con HMAC-SHA256.
     * <p>
     * La chiave segreta viene letta dalla proprieta' {@code jwt.secret}
     * (configurata in application.yaml con fallback a un valore predefinito
     * per l'ambiente locale). La chiave e' codificata in Base64.
     * </p>
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(
                Base64.getDecoder().decode(jwtSecret), "HmacSHA256");
        return NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Configura CORS per permettere al frontend Angular (localhost:4200) di
     * chiamare il gateway. Il {@code spring.cloud.gateway.globalcors} in YAML
     * non e' supportato dalla versione MVC del gateway, quindi va definito
     * esplicitamente via bean Spring Security.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:4200",
                "http://localhost:8080"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Configura il convertitore per estrarre i ruoli/authority dal token JWT.
     * <p>
     * I ruoli vengono letti dal claim {@code roles} del JWT e mappati con
     * il prefisso {@code ROLE_} per essere compatibili con Spring Security.
     * </p>
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * Configura la catena di filtri di sicurezza.
     * <p>
     * Definisce le regole di accesso: alcuni percorsi sono pubblici (permitAll),
     * mentre tutti gli altri richiedono autenticazione tramite JWT. La
     * protezione CSRF e' disabilitata perche' il gateway e' stateless e non
     * utilizza sessioni (ogni richiesta e' autenticata singolarmente).
     * </p>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/iam/api/v1/auth/**").permitAll()
                        .requestMatchers("/iam/swagger-ui/**", "/iam/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

}
