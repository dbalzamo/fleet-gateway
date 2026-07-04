package com.fleetpulse.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto di ingresso del microservizio API Gateway.
 * <p>
 * Un API Gateway è il punto di ingresso unico per tutte le richieste HTTP
 * provenienti dall'esterno (client web, mobile, terze parti). Riceve le
 * richieste e le smista ai microservizi interni (auth-service, fleet-service, ecc.)
 * in base a regole di routing definite.
 * </p>
 * <p>
 * Oltre al routing, il gateway gestisce:
 * <ul>
 *   <li>Autenticazione e autorizzazione tramite JWT</li>
 *   <li>CORS (per permettere richieste dal frontend Angular)</li>
 *   <li>Filtri per arricchire le richieste (es. header X-User-Id)</li>
 * </ul>
 * </p>
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

}
