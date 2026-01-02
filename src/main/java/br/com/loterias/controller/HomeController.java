package br.com.loterias.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Hidden
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    @GetMapping("/")
    public Mono<Map<String, Object>> home() {
        log.info("Request home/info endpoint");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", "Loterias Analyzer API");
        info.put("version", "1.0.0");
        info.put("documentation", "/docs");
        info.put("apiDocs", "/api-docs");
        info.put("endpoints", Map.of(
                "concursos", "/api/concursos/{tipo}/ultimo",
                "estatisticas", "/api/estatisticas/{tipo}/frequencia",
                "apostas", "/api/apostas/{tipo}/verificar",
                "import", "/api/import/download-excel",
                "export", "/api/export/{tipo}/concursos.csv"
        ));
        return Mono.just(info);
    }

    @GetMapping("/docs")
    public Mono<Void> redirectToSwagger(ServerHttpResponse response) {
        log.debug("Redirecting to Swagger UI");
        response.setStatusCode(HttpStatus.PERMANENT_REDIRECT);
        response.getHeaders().setLocation(URI.create("/swagger-ui.html"));
        return response.setComplete();
    }
}
