package com.fleetpulse.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

/**
 * Definisce le regole di instradamento (routing) del gateway verso i microservizi.
 * <p>
 * Ogni metodo {@code @Bean} crea una rotta che associa un percorso URL (path
 * pattern) all'indirizzo del microservizio di destinazione. Quando arriva una
 * richiesta HTTP, il gateway confronta il percorso richiesto con questi pattern
 * e inoltra la richiesta al microservizio corrispondente.
 * </p>
 * <p>
 * Pattern utilizzato — MVC Gateway (Servlet):
 * <ul>
 *   <li>{@code GatewayRouterFunctions.route(id)} — crea un builder per la rotta</li>
 *   <li>{@code .route(path("/..."), http())} — associa un path pattern a un handler HTTP</li>
 *   <li>{@code .before(uri("http://..."))} — indica l'indirizzo del microservizio di destinazione</li>
 *   <li>{@code .build()} — costruisce la rotta</li>
 * </ul>
 * </p>
 */
@Configuration
public class RouteConfig {

    /**
     * Rotta per il servizio di autenticazione (auth-service).
     * <p>
     * Inoltra le richieste {@code /iam/api/v1/auth/**} al servizio auth-service
     * sulla porta 8081. Non applica StripPrefix perche' il context-path
     * {@code /iam} viene gestito direttamente dal server Tomcat dell'auth-service.
     * </p>
     */
    @Bean
    public RouterFunction<ServerResponse> authServiceRoute() {
        return route("auth-service")
                .route(path("/iam/api/v1/auth/**"), http())
                .before(uri("http://localhost:8081"))
                .build();
    }

    /**
     * Rotta per l'interfaccia Swagger UI dell'auth-service.
     * <p>
     * Inoltra le richieste {@code /iam/swagger-ui/**} all'auth-service per
     * visualizzare la documentazione interattiva delle API REST.
     * </p>
     */
    @Bean
    public RouterFunction<ServerResponse> authServiceSwaggerUiRoute() {
        return route("auth-service-swagger-ui")
                .route(path("/iam/swagger-ui/**"), http())
                .before(uri("http://localhost:8081"))
                .build();
    }

    /**
     * Rotta per gli endpoint OpenAPI/Swagger dell'auth-service.
     * <p>
     * Inoltra le richieste {@code /iam/v3/api-docs/**} all'auth-service.
     * Questo include sia il documento JSON OpenAPI che la configurazione
     * Swagger ({@code /swagger-config}) necessaria per il caricamento
     * dell'interfaccia Swagger UI.
     * </p>
     */
    @Bean
    public RouterFunction<ServerResponse> authServiceApiDocsRoute() {
        return route("auth-service-api-docs")
                .route(path("/iam/v3/api-docs/**"), http())
                .before(uri("http://localhost:8081"))
                .build();
    }

    /**
     * Rotta per il servizio Fleet (fleet-service).
     * <p>
     * Inoltra le richieste {@code /fleet/api/fleet/**} al microservizio
     * fleet-service sulla porta 8082.
     * </p>
     */
    @Bean
    public RouterFunction<ServerResponse> fleetServiceRoute() {
        return route("fleet-service")
                .route(path("/fleet/api/fleet/**"), http())
                .before(uri("http://localhost:8082"))
                .build();
    }

}
