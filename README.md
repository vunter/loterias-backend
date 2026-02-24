# Loterias Analyzer - Backend

API REST para análise estatística de resultados de loterias brasileiras. Busca automaticamente os resultados do site oficial da Caixa Economica Federal e fornece analises estatisticas, geracao de jogos e simulacao de apostas.

## Tecnologias

- Java 25
- Spring Boot 4.0.2
- Spring Data JPA
- PostgreSQL
- Flyway (migrações de banco)
- Caffeine (cache)
- RestClient (integração com API da Caixa)
- Prometheus / Loki (observabilidade)
- OpenAPI / Swagger (documentação)

## Loterias Suportadas

- Mega-Sena
- Lotofacil
- Quina
- Lotomania
- Timemania
- Dupla Sena
- Dia de Sorte
- Super Sete
- +Milionaria

## Pre-requisitos

- Java 25 ou superior
- Maven 3.9+ (ou use o wrapper incluido)
- PostgreSQL 15+

## Executando

### Desenvolvimento

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

API disponivel em: http://localhost:8081
Swagger UI: http://localhost:8081/docs

### Docker

```bash
docker build -t loterias-backend .
docker run -p 8081:8081 loterias-backend
```

### Producao

Configure as variaveis de ambiente:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/loterias
export DATABASE_USERNAME=seu_usuario
export DATABASE_PASSWORD=sua_senha
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## API REST

### Concursos

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| GET | `/api/concursos/{tipo}` | Lista todos os concursos de uma loteria |
| GET | `/api/concursos/{tipo}/{numero}` | Busca concurso especifico |
| GET | `/api/concursos/{tipo}/ultimo` | Busca ultimo concurso |
| POST | `/api/concursos/{tipo}/sync` | Sincroniza novos concursos |
| POST | `/api/concursos/{tipo}/sync-full` | Carga completa de todos os concursos historicos |
| POST | `/api/concursos/sync-all` | Sincroniza todas as loterias |

### Dashboard

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| GET | `/api/dashboard/{tipo}` | Dashboard com estatisticas agregadas |
| GET | `/api/dashboard/{tipo}/numeros/ranking` | Ranking de numeros |
| GET | `/api/dashboard/{tipo}/conferir` | Conferir aposta |
| GET | `/api/dashboard/{tipo}/acumulado` | Informacoes de acumulado |
| GET | `/api/dashboard/{tipo}/especiais` | Concursos especiais |
| GET | `/api/dashboard/{tipo}/ganhadores` | Ganhadores por UF |
| GET | `/api/dashboard/{tipo}/rateio` | Rateio de premiacoes |

### Estatisticas

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| GET | `/api/estatisticas/{tipo}/frequencia` | Frequencia de todos os numeros |
| GET | `/api/estatisticas/{tipo}/mais-frequentes` | Numeros mais sorteados |
| GET | `/api/estatisticas/{tipo}/menos-frequentes` | Numeros menos sorteados |
| GET | `/api/estatisticas/{tipo}/mais-frequentes-com-ganhadores` | Mais frequentes em concursos com ganhadores |
| GET | `/api/estatisticas/{tipo}/atrasados` | Numeros mais atrasados |
| GET | `/api/estatisticas/{tipo}/pares-impares` | Media de pares/impares |
| GET | `/api/estatisticas/{tipo}/soma-media` | Soma media das dezenas |
| GET | `/api/estatisticas/{tipo}/sequenciais` | Frequencia de numeros consecutivos |
| GET | `/api/estatisticas/{tipo}/faixas` | Distribuicao por faixas |
| GET | `/api/estatisticas/{tipo}/numero/{numero}` | Historico de um numero |
| GET | `/api/estatisticas/{tipo}/correlacao` | Pares de numeros que mais saem juntos |
| GET | `/api/estatisticas/{tipo}/acompanham/{numero}` | Numeros que mais saem com outro |
| GET | `/api/estatisticas/{tipo}/gerar-jogos` | Gera jogos baseados em estatisticas |
| GET | `/api/estatisticas/{tipo}/gerar-jogos-estrategico` | Geracao estrategica de jogos |

### Analise Avancada

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| GET | `/api/avancada/{tipo}/ordem-sorteio` | Analise por ordem de sorteio |
| GET | `/api/avancada/{tipo}/financeiro` | Analise financeira |
| GET | `/api/avancada/{tipo}/tendencias` | Tendencias e previsoes |
| GET | `/api/avancada/{tipo}/historico-mensal` | Historico mensal |
| GET | `/api/avancada/{tipo}/dupla-sena` | Analise especifica Dupla Sena |

### Apostas e Simulacao

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| POST | `/api/apostas/{tipo}/verificar` | Verifica aposta em concursos anteriores |
| POST | `/api/simulador/{tipo}/simular` | Simula apostas e calcula ROI |

### Time do Coracao e Mes da Sorte

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| GET | `/api/analise/timemania/times` | Lista times da Timemania |
| GET | `/api/analise/{tipo}/time-coracao` | Ranking de times |
| GET | `/api/analise/{tipo}/time-coracao/sugestao` | Sugestao de time |

### Import e Export

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| POST | `/api/import/{tipo}/excel` | Importa Excel via upload |
| POST | `/api/import/{tipo}/download-excel` | Baixa Excel da Caixa e importa |
| POST | `/api/import/download-excel-all` | Importa todas as loterias |
| GET | `/api/export/{tipo}/concursos.csv` | Exporta concursos em CSV |
| GET | `/api/export/{tipo}/frequencia.csv` | Exporta frequencia em CSV |
| GET | `/api/export/{tipo}/estatisticas.csv` | Exporta estatisticas em CSV |

### Admin

| Metodo | Endpoint | Descricao |
|--------|----------|-----------|
| DELETE | `/api/admin/caches` | Limpa caches da aplicacao |

### Tipos de Loteria Validos

Use um dos seguintes valores para `{tipo}`:
- `mega-sena`
- `lotofacil`
- `quina`
- `lotomania`
- `timemania`
- `dupla-sena`
- `dia-de-sorte`
- `super-sete`
- `mais-milionaria`

### Exemplos

```bash
# Carga completa da Mega-Sena
curl -X POST http://localhost:8081/api/concursos/mega-sena/sync-full

# Sincronizar novos concursos
curl -X POST http://localhost:8081/api/concursos/mega-sena/sync

# Dashboard
curl http://localhost:8081/api/dashboard/mega-sena

# 10 numeros mais frequentes
curl http://localhost:8081/api/estatisticas/mega-sena/mais-frequentes

# Numeros mais atrasados
curl http://localhost:8081/api/estatisticas/lotofacil/atrasados?quantidade=15

# Pares que mais saem juntos
curl http://localhost:8081/api/estatisticas/mega-sena/correlacao?quantidade=10

# Gerar jogos estrategicos
curl "http://localhost:8081/api/estatisticas/mega-sena/gerar-jogos-estrategico?estrategia=NUMEROS_QUENTES&quantidade=3"

# Verificar aposta
curl -X POST http://localhost:8081/api/apostas/mega-sena/verificar \
  -H "Content-Type: application/json" \
  -d '{"numeros":[4,8,15,16,23,42]}'

# Exportar CSV
curl -O http://localhost:8081/api/export/mega-sena/concursos.csv
```

## Sincronizacao Automatica

O sistema sincroniza novos concursos diariamente as 22:00 (configuravel).

```yaml
# application.yml
loterias:
  sync:
    enabled: true
    cron: "0 0 22 * * *"
```

## Monitoramento

| Endpoint | Descricao |
|----------|-----------|
| `/actuator/health` | Health check |
| `/actuator/info` | Informacoes da aplicacao |
| `/actuator/metrics` | Metricas Prometheus |
| `/docs` | Swagger UI |
| `/api-docs` | OpenAPI JSON |

## Build

```bash
./mvnw clean package
```

O JAR executavel sera gerado em `target/loterias-analyzer-0.0.1-SNAPSHOT.jar`.

## Estrutura do Projeto

```
src/main/java/br/com/loterias/
├── LoteriasApplication.java
├── config/
│   ├── AccessLogConfig.java
│   ├── CacheConfig.java
│   ├── CorsConfig.java
│   ├── DatabaseInitializer.java
│   ├── LocalNetworkRestrictionConfig.java
│   ├── LoteriaProperties.java
│   ├── OpenApiConfig.java
│   ├── RateLimitConfig.java
│   └── RestClientConfig.java
├── controller/
│   ├── AdminController.java
│   ├── AnaliseAvancadaController.java
│   ├── ApostasController.java
│   ├── ConcursoController.java
│   ├── DashboardController.java
│   ├── EstatisticaController.java
│   ├── ExportController.java
│   ├── GlobalExceptionHandler.java
│   ├── HomeController.java
│   ├── ImportController.java
│   ├── SimuladorController.java
│   ├── TimeCoracaoController.java
│   └── TipoLoteriaParser.java
├── domain/
│   ├── dto/
│   ├── entity/
│   └── repository/
├── service/
│   ├── AnaliseNumeroService.java
│   ├── ApiSyncService.java
│   ├── AtualizarGanhadoresService.java
│   ├── CaixaApiClient.java
│   ├── ConcursoBatchService.java
│   ├── ConcursoMapper.java
│   ├── ConcursosEspeciaisService.java
│   ├── ConcursoSyncService.java
│   ├── ConferirApostaService.java
│   ├── DashboardService.java
│   ├── DuplaSenaService.java
│   ├── EstatisticaService.java
│   ├── ExcelImportService.java
│   ├── ExportService.java
│   ├── FinanceiroService.java
│   ├── GeradorEstrategicoService.java
│   ├── GeradorJogosService.java
│   ├── OrdemSorteioService.java
│   ├── SimuladorApostasService.java
│   ├── SyncRateLimitService.java
│   ├── TendenciaAnaliseService.java
│   ├── TimeCoracaoMesSorteService.java
│   └── VerificadorApostasService.java
└── scheduler/
    └── SyncScheduler.java
```

## Fonte de Dados

Os dados sao obtidos da API oficial da Caixa Economica Federal:
- Base URL: `https://servicebus2.caixa.gov.br/portaldeloterias/api/`

## Licenca

Este projeto e para fins educacionais. Os dados das loterias pertencem a Caixa Economica Federal.
