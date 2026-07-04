# Fleet Pulse — API Gateway

> **Linguaggio**: italiano (semplice, pensato per essere compreso anche da un junior).

---

## Sommario

1. [Cos'è un API Gateway](#1-cosè-un-api-gateway)
2. [A cosa serve](#2-a-cosa-serve)
3. [Overview architetturale](#3-overview-architetturale)
4. [Teoria essenziale (per colloquio tecnico)](#4-teoria-essenziale-per-colloquio-tecnico)
   - [Pattern: Gateway Routing](#41-pattern-gateway-routing)
   - [Pattern: Gateway Offloading](#42-pattern-gateway-offloading)
   - [Reactive vs MVC Gateway](#43-reactive-vs-mvc-gateway)
   - [CORS (Cross-Origin Resource Sharing)](#44-cors-cross-origin-resource-sharing)
   - [JWT e OAuth2 Resource Server](#45-jwt-e-oauth2-resource-server)
   - [Stateless vs Stateful](#46-stateless-vs-stateful)
   - [Gateway e Service Discovery (K8s / Eureka)](#47-gateway-e-service-discovery-k8s--eureka)
5. [Pratica: come implementare un Gateway (step-by-step)](#5-pratica-come-implementare-un-gateway-step-by-step)
   - [Step 1 — Creare il progetto Spring Boot](#51-step-1--creare-il-progetto-spring-boot)
   - [Step 2 — Aggiungere le dipendenze](#52-step-2--aggiungere-le-dipendenze)
   - [Step 3 — Configurare le rotte (RouteConfig)](#53-step-3--configurare-le-rotte-routeconfig)
   - [Step 4 — Configurare la sicurezza (SecurityConfig)](#54-step-4--configurare-la-sicurezza-securityconfig)
   - [Step 5 — Aggiungere i filtri (es. AuthHeaderFilter)](#55-step-5--aggiungere-i-filtri-es-authheaderfilter)
   - [Step 6 — Configurare application.yaml](#56-step-6--configurare-applicationyaml)
   - [Step 7 — Testare localmente con netcat](#57-step-7--testare-localmente-con-netcat)
   - [Step 8 — Containerizzare con Docker](#58-step-8--containerizzare-con-docker)
6. [Riferimenti al codice](#6-riferimenti-al-codice)

---

## 1. Cos'è un API Gateway

Un **API Gateway** è un server che si pone **tra il client (frontend, app mobile, terze parti) e i servizi backend**. Tutte le richieste passano attraverso di lui, che decide dove inoltrarle.

Immagina un centralino telefonico: chiami un unico numero e il centralino ti smista al reparto giusto. Il gateway fa la stessa cosa con le richieste HTTP.

---

## 2. A cosa serve

In un'architettura a microservizi, hai tanti piccoli servizi che comunicano tra loro. Il problema è: **il client (es. un'app Angular) come fa a sapere l'indirizzo di ogni servizio?**

Senza gateway il client dovrebbe chiamare:
- `http://servizioA:8081/api/...`
- `http://servizioB:8082/api/...`
- `http://servizioC:8083/api/...`

Questo è un disastro:
- Il client deve conoscere l'indirizzo di TUTTI i servizi
- Se un servizio cambia porta o indirizzo, devi aggiornare TUTTI i client
- Manca un punto centralizzato per gestire autenticazione, log, rate limiting, ecc.

**Il gateway risolve tutto questo**: il client chiama **un solo indirizzo** (`http://localhost:8080`) e il gateway smista internamente.

---

## 3. Overview architetturale

```
        Client (Angular su :4200)
                 │
                 ▼
         ┌───────────────┐
         │  API Gateway   │  ← porta 8080
         │  (Questo progetto)
         └───────┬───────┘
                 │
        ┌────────┴────────┐
        ▼                  ▼
 ┌────────────┐    ┌────────────┐
 │ auth-service │    │fleet-service│
 │  (porta 8081) │    │  (porta 8082) │
 └──────────────┘    └─────────────┘
```

- **Client** → chiama il gateway su `http://localhost:8080`
- **Gateway** → esamina il percorso della richiesta e decide dove inoltrarla
  - `/iam/api/v1/auth/**` → inoltra a **auth-service** (`:8081`)
  - `/iam/swagger-ui/**` → inoltra a **auth-service** (`:8081`)
  - `/iam/v3/api-docs/**` → inoltra a **auth-service** (`:8081`)
  - `/fleet/api/fleet/**` → inoltra a **fleet-service** (`:8082`)

---

## 4. Teoria essenziale (per colloquio tecnico)

Queste domande capitano spesso nei colloqui per posizioni DevOps/Software Engineer.

### 4.1 Pattern: Gateway Routing

Il **routing** è il compito principale del gateway. In base al percorso URL (e/o ad altre condizioni come header HTTP, metodo, ecc.), il gateway decide quale microservizio chiamare.

**Esempio**:
```
Richiesta: GET /iam/api/v1/auth/login
Route match: /iam/api/v1/auth/**
Destinazione: http://localhost:8081/iam/api/v1/auth/login
```
Il path originale viene preservato (non c'è strip del prefisso). Se volessi rimuovere un prefisso, useresti `StripPrefix` (es. `/iam/api/v1/auth/login` → diventa `/api/v1/auth/login` nel backend).

**Domanda da colloquio**: "Qual è la differenza tra routing statico e dinamico?"
- **Statico**: le route sono scritte nel codice o in YAML all'avvio. Se aggiungi un microservizio, devi ricostruire il gateway.
- **Dinamico**: il gateway si integra con un Service Registry (Eureka, Consul) e scopre automaticamente i servizi. Se aggiungi un microservizio, il gateway lo "vede" senza ricompilare.

### 4.2 Pattern: Gateway Offloading

Il gateway non solo instrada, ma **scarica** (offload) responsabilità dai microservizi. Questo significa che invece di ripetere la stessa logica in ogni servizio, la metti una volta sola nel gateway:

- **Autenticazione JWT** — il gateway valida il token, i microservizi si fidano del gateway
- **Logging** — il gateway logga tutte le richieste
- **Rate Limiting** — il gateway limita quante richieste un client può fare
- **CORS** — il gateway gestisce le policy CORS
- **Header enrichment** — il gateway aggiunge header informativi (es. `X-User-Id`)

**Perché è importante?** Se non usassi il gateway, ogni microservizio dovrebbe:
1. Avere la libreria JWT
2. Decodificare e validare il token
3. Gestire CORS
4. Gestire rate limiting
5. Fare logging

Con l'offloading, tutto questo è centralizzato.

**Domanda da colloquio**: "Quali sono i vantaggi del Gateway Offloading?"
- Riduzione della duplicazione di codice nei microservizi
- Single point of control per security/logging/monitoring
- I microservizi diventano più semplici e leggeri

### 4.3 Reactive vs MVC Gateway

Spring Cloud Gateway si presenta in due versioni:

| Caratteristica | MVC Gateway (Servlet, 5.x) | Reactive Gateway (4.x) |
|---|---|---|
| **Basato su** | Servlet stack (Tomcat) | WebFlux (Netty) |
| **Modello** | Thread-per-request | Event-loop / non bloccante |
| **Quando usarlo** | Team abituati a Spring MVC | Alta concorrenza, I/O intensivo |
| **Compatibilità Spring Boot** | Boot 3.x, Boot 4.x | Boot 3.2.x / 3.4.x |
| **Route in YAML** | Non supportato (v5) | Supportato |
| **API** | `GatewayRouterFunctions` | `RouteLocator` + `Route` DSL |

**Differenza pratica**: il Reactive Gateway è asincrono e non bloccante (ideale per tante connessioni concorrenti), mentre l'MVC Gateway è sincrono (un thread per richiesta).

**Domanda da colloquio**: "Quale gateway useresti per un sistema con migliaia di connessioni simultanee?"
- Reactive Gateway, perché con il modello event-loop gestisce più richieste con meno thread.

**Attenzione**: Spring Boot 4.1.0 NON è compatibile con Reactive Gateway 4.x. L'MVC Gateway (5.x) è l'unica opzione. Vedi il paragrafo [Step 2 — Aggiungere le dipendenze](#52-step-2--aggiungere-le-dipendenze).

### 4.4 CORS (Cross-Origin Resource Sharing)

Il browser blocca le richieste HTTP da un'origine (es. `http://localhost:4200`) verso un'altra origine diversa (es. `http://localhost:8080`) per ragioni di sicurezza. CORS è il meccanismo che dice al browser: "tranquillo, questa origine è autorizzata".

**Configurazione nel gateway**:
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "http://localhost:4200"
            allowedMethods: [GET, POST, PUT, DELETE, PATCH, OPTIONS]
            allowedHeaders: "*"
```

**Perché si configura nel gateway e non nei singoli servizi?** Perché il browser parla solo col gateway (single origin), quindi è l'unico punto dove CORS serve.

### 4.5 JWT e OAuth2 Resource Server

**JWT (JSON Web Token)** è un formato compatto per trasmettere informazioni (claims) tra due parti in modo sicuro. È firmato digitalmente, quindi non può essere manomesso.

**Come funziona nel gateway**:

```
1. Client fa login → auth-service → emette un JWT
2. Client chiama una rotta protetta → allega il JWT nell'header "Authorization: Bearer <token>"
3. Gateway riceve la richiesta → valida il JWT (firma, scadenza, ecc.)
4. Se valido → inoltra la richiesta al microservizio
5. Se non valido → risponde con 401 Unauthorized
```

**Configurazione in SecurityConfig**:
```java
oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
```

**Attenzione**: In Spring Security 7.x (usato da Boot 4.x), `oauth2ResourceServer().jwt()` richiede `Customizer.withDefaults()` — passare direttamente un lambda non funziona.

### 4.6 Stateless vs Stateful

Il gateway è **stateless**: non mantiene informazioni di sessione. Ogni richiesta è indipendente. Questo è fondamentale per:

- **Scalabilità orizzontale** — puoi avere più repliche del gateway perché non c'è stato condiviso
- **Resilienza** — se una replica crasha, un'altra replica gestisce la richiesta senza perdere dati di sessione
- **Kubernetes** — i pod possono essere distrutti e ricreati senza preoccupazioni

**Configurazione**:
```java
.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

### 4.7 Gateway e Service Discovery (K8s / Eureka)

In produzione, i microservizi sono in continuo cambiamento (scaling, crashes, deploy). Il loro indirizzo IP e porta possono cambiare.

Due approcci per risolvere gli indirizzi a runtime:

1. **Kubernetes DNS** — ogni servizio K8s ha un nome DNS interno (es. `auth-service.namespace.svc.cluster.local`). Il gateway risolve il nome DNS.
2. **Service Registry** (Eureka, Consul) — i servizi si registrano all'avvio e il gateway consulta il registry per scoprire dove sono.

**Problema attuale**: nel codice usiamo `localhost:8081` e `localhost:8082`. Questo funziona solo in locale. In Kubernetes andrebbe cambiato con `http://auth-service:8081` (oppure integrato con Service Discovery).

**Domanda da colloquio**: "Come gestisci il routing se i microservizi cambiano indirizzo?"
- Usando un Service Registry (Eureka) o Kubernetes DNS. Non usare indirizzi statici in produzione.

---

## 5. Pratica: come implementare un Gateway (step-by-step)

Questa sezione è una guida generale per implementare un API Gateway in qualsiasi progetto a microservizi con Spring Boot 4 + Spring Cloud 2025.

### 5.1 Step 1 — Creare il progetto Spring Boot

Crea un nuovo progetto Spring Boot con Spring Initializr o manualmente. Scegli:
- **Java 17+**
- **Spring Boot 4.1.0** (l'ultima versione 4.x)

> **Nota per senior/junior**: Spring Boot 4.x è molto recente (2025). Se il tuo progetto usa versioni più stabili (Boot 3.x + Cloud 2024.x), vedi la nota al passo successivo.

### 5.2 Step 2 — Aggiungere le dipendenze

Nel tuo `pom.xml` aggiungi:

```xml
<properties>
    <java.version>17</java.version>
    <spring-cloud.version>2025.1.2</spring-cloud.version>
</properties>

<dependencies>
    <!-- Gateway MVC (Servlet, 5.x) -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
    </dependency>

    <!-- Sicurezza + JWT -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Attenzione alla versione**: 
- Con Spring Boot 4.1.0 → usa Cloud 2025.1.2, gateway MVC 5.x
- Con Spring Boot 3.2.x → usa Cloud 2024.0.x, gateway reactive 4.2.0
- Non mescolare: Boot 4.x con reactive gateway NON funziona (Netty incompatibile)

### 5.3 Step 3 — Configurare le rotte (RouteConfig)

Crea una classe `RouteConfig` con un metodo `@Bean` per ogni microservizio che vuoi esporre.

**Pattern base**:
```java
@Bean
public RouterFunction<ServerResponse> nomeServizioRoute() {
    return route("id-rotta")
            .route(path("/percorso/da/matchare/**"), http())
            .before(uri("http://indirizzo-del-servizio"))
            .build();
}
```

**Dove trovare la classe** → [RouteConfig.java](src/main/java/com/fleetpulse/apigateway/config/RouteConfig.java)

**Regola pratica**: una rotta per ogni microservizio, una per Swagger UI, una per OpenAPI docs. Non mischiare tutto in una.

### 5.4 Step 4 — Configurare la sicurezza (SecurityConfig)

Crea una classe `SecurityConfig` per:
1. **Validare il JWT** — tramite `JwtDecoder` con chiave condivisa HMAC
2. **Definire cosa è pubblico e cosa è protetto** — `requestMatchers(...).permitAll()` per login/register/swagger
3. **Configurare la sessione come stateless**

**Pattern base**:
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/path/pubblico/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
}
```

**Dove trovare la classe** → [SecurityConfig.java](src/main/java/com/fleetpulse/apigateway/config/SecurityConfig.java)

### 5.5 Step 5 — Aggiungere i filtri (es. AuthHeaderFilter)

I filtri nel gateway servono a intercettare una richiesta e modificarla prima di inoltrarla al backend.

**Esempio**: il filtro `AuthHeaderFilter` legge l'utente autenticato dal SecurityContext e aggiunge l'header `X-User-Id` alla richiesta in uscita.

**Pattern base per un filtro**:
```java
@Component
public class MyFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {
    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) {
        // Leggi/modifica la richiesta...
        ServerRequest mutated = ServerRequest.from(request)
            .header("X-Mio-Header", "valore")
            .build();
        return next.handle(mutated);
    }
}
```

**Dove trovare la classe** → [AuthHeaderFilter.java](src/main/java/com/fleetpulse/apigateway/filter/AuthHeaderFilter.java)

**Domanda da colloquio**: "Perché non inserire X-User-Id direttamente nel SecurityConfig?"
- Per separazione delle responsabilità: SecurityConfig si occupa di chi può accedere, AuthHeaderFilter si occupa di arricchire la richiesta. Se dovessi aggiungere un secondo header, modifichi solo il filtro senza toccare la sicurezza.

### 5.6 Step 6 — Configurare application.yaml

```yaml
server:
  port: 8080             # Porta su cui ascolta il gateway

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "http://localhost:4200"
            allowedMethods: [GET, POST, PUT, DELETE, PATCH, OPTIONS]
            allowedHeaders: "*"

jwt:
  secret: ${JWT_SECRET:valore_base64_default}

management:
  endpoints:
    web:
      exposure:
        include: gateway, health, prometheus, metrics
```

**Dove trovare il file** → [application.yaml](src/main/resources/application.yaml)

**Nota**: la proprietà `spring.cloud.gateway.mvc.routes` non esiste in questa versione del gateway. Le route vanno dichiarate in codice Java, non in YAML. Questo è un cambiamento importante rispetto al Reactive Gateway.

### 5.7 Step 7 — Testare localmente con netcat

Prima di avere i microservizi reali, puoi testare se le route matchano con netcat:

```bash
# Terminale 1: simula auth-service
nc -l 8081

# Terminale 2: simula fleet-service
nc -l 8082

# Terminale 3: avvia il gateway
./mvnw spring-boot:run -pl fleet-gateway

# Terminale 4: testa
curl http://localhost:8080/iam/api/v1/auth/login
curl http://localhost:8080/fleet/api/fleet/vehicles
```

Se la rotta matcha, il gateway si connette a netcat e vedi la richiesta HTTP nel terminale 1 o 2.

### 5.8 Step 8 — Containerizzare con Docker

**Dockerfile** (multi-stage build):
```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src /src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Dove trovare il file** → [Dockerfile](Dockerfile)

**Per Kubernetes**: cambia gli URI in RouteConfig.java da `localhost:8081` a `http://auth-service:8081` (dove `auth-service` è il nome del Service Kubernetes del microservizio).

---

## 6. Riferimenti al codice

| Classe / File | Scopo | Percorso |
|---|---|---|
| `ApiGatewayApplication.java` | Entry point Spring Boot | `src/main/java/com/fleetpulse/apigateway/ApiGatewayApplication.java` |
| `RouteConfig.java` | Definisce le rotte verso i microservizi | `src/main/java/com/fleetpulse/apigateway/config/RouteConfig.java` |
| `SecurityConfig.java` | Configura JWT + regole di accesso | `src/main/java/com/fleetpulse/apigateway/config/SecurityConfig.java` |
| `AuthHeaderFilter.java` | Filtro che aggiunge header X-User-Id | `src/main/java/com/fleetpulse/apigateway/filter/AuthHeaderFilter.java` |
| `application.yaml` | Configurazioni (CORS, JWT, management) | `src/main/resources/application.yaml` |
| `pom.xml` | Dipendenze Maven | `pom.xml` |
| `Dockerfile` | Build immagine Docker | `Dockerfile` |

Leggi i **JavaDoc** nelle singole classi per i dettagli implementativi di ogni componente.
