package com.fleetpulse.apigateway.filter;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Filtro che inietta l'identita' dell'utente autenticato nelle richieste
 * inoltrate ai microservizi downstream.
 * <p>
 * Dopo che il gateway ha validato il token JWT e autenticato l'utente,
 * questo filtro aggiunge l'header HTTP {@code X-User-Id} contenente lo
 * username dell'utente. I microservizi interni possono cosi' sapere chi
 * sta facendo la richiesta senza dover decodificare il token da soli.
 * </p>
 * <p>
 * Questo e' utile per:
 * <ul>
 *   <li>Audit logging (registrare quale utente ha fatto cosa)</li>
 *   <li>Autorizzazione a livello di dato (un utente vede solo i propri dati)</li>
 *   <li>Evitare che ogni microservizio debba re-implementare la validazione JWT</li>
 * </ul>
 * </p>
 */
@Component
public class AuthHeaderFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            ServerRequest mutated = ServerRequest.from(request)
                    .header("X-User-Id", auth.getName())
                    .build();
            return next.handle(mutated);
        }
        return next.handle(request);
    }

}
