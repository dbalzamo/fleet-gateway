package com.fleetpulse.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro semplice che logga ogni richiesta HTTP in ingresso al gateway.
 * <p>
 * Stampa method, URI, query string e status code della risposta.
 * Utile per debug e monitoraggio delle chiamate che transitano dal gateway.
 * </p>
 */
@Component
@Order(-200)
public class HttpLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String qs = request.getQueryString();
        String fullPath = qs != null ? uri + "?" + qs : uri;

        log.info(">>> {} {}", request.getMethod(), fullPath);

        chain.doFilter(request, response);

        log.info("<<< {} {} -> {}", request.getMethod(), fullPath, response.getStatus());
    }

}
