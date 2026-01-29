# Loterias Analyzer

Sistema para análise estatística de resultados de loterias brasileiras. Busca automaticamente os resultados do site oficial da Caixa Econômica Federal e fornece análises estatísticas completas.

## Tecnologias

### Backend
- Java 25
- Spring Boot 4.0.1 WebFlux
- Spring Data JPA
- PostgreSQL
- RestClient (integração com API da Caixa)

### Frontend
- Next.js 16 (React 19)
- TypeScript
- TailwindCSS
- Recharts
- Lucide Icons

## Loterias Suportadas

- Mega-Sena
- Lotofácil
- Quina
- Lotomania
- Timemania
- Dupla Sena
- Dia de Sorte
- Super Sete
- +Milionária

## Executando o Projeto

### Pré-requisitos

- Java 25 ou superior
- Maven 3.9+ (ou use o wrapper incluído)

### Backend (Desenvolvimento)

```bash
./mvnw spring-boot:run
```

API disponível em: http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui.html

### Frontend (Desenvolvimento)

```bash
cd frontend
npm install
npm run dev
```

Frontend disponível em: http://localhost:3000

### Docker (Recomendado)

```bash
# Subir toda a stack (app + PostgreSQL)
docker-compose up -d

# Ver logs
docker-compose logs -f app

# Parar
docker-compose down
```

Acesse: http://localhost:8080

### Produção (Manual)

Configure as variáveis de ambiente:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/loterias
export DATABASE_USERNAME=seu_usuario
export DATABASE_PASSWORD=sua_senha
```

Execute com o profile de produção:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Monitoramento

| Endpoint | Descrição |
|----------|-----------|
| `/actuator/health` | Health check |
| `/actuator/info` | Informações da aplicação |
| `/actuator/metrics` | Métricas |
| `/docs` | Swagger UI |
| `/api-docs` | OpenAPI JSON |

## API REST

### Concursos

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/concursos/{tipo}` | Lista todos os concursos de uma loteria |
| GET | `/api/concursos/{tipo}/{numero}` | Busca concurso específico |
| GET | `/api/concursos/{tipo}/ultimo` | Busca último concurso |
| POST | `/api/concursos/{tipo}/sync` | Sincroniza novos concursos de uma loteria |
| POST | `/api/concursos/{tipo}/sync-full` | Carga completa - baixa TODOS os concursos históricos |
| POST | `/api/concursos/sync-all` | Sincroniza todas as loterias |

### Estatísticas

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/estatisticas/{tipo}/frequencia` | Frequência de todos os números |
| GET | `/api/estatisticas/{tipo}/mais-frequentes?quantidade=10` | Números mais sorteados |
| GET | `/api/estatisticas/{tipo}/menos-frequentes?quantidade=10` | Números menos sorteados |
| GET | `/api/estatisticas/{tipo}/mais-frequentes-com-ganhadores?quantidade=10` | Mais frequentes em concursos COM ganhadores |
| GET | `/api/estatisticas/{tipo}/atrasados?quantidade=10` | Números mais atrasados |
| GET | `/api/estatisticas/{tipo}/pares-impares` | Média de pares/ímpares por concurso |
| GET | `/api/estatisticas/{tipo}/soma-media` | Soma média das dezenas |
| GET | `/api/estatisticas/{tipo}/sequenciais` | Frequência de números consecutivos |
| GET | `/api/estatisticas/{tipo}/faixas` | Distribuição por faixas (01-10, 11-20...) |
| GET | `/api/estatisticas/{tipo}/numero/{numero}` | Histórico de um número específico |
| GET | `/api/estatisticas/{tipo}/correlacao?quantidade=20` | Pares de números que mais saem juntos |
| GET | `/api/estatisticas/{tipo}/acompanham/{numero}?quantidade=10` | Números que mais saem junto com um número específico |
| POST | `/api/estatisticas/{tipo}/gerar-jogos` | Gera jogos inteligentes baseados em estatísticas |

### Apostas

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/apostas/{tipo}/verificar` | Verifica se uma aposta ganhou em concursos anteriores |
| POST | `/api/apostas/{tipo}/simular` | Simula apostas em histórico e calcula ROI |

### Export

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/export/{tipo}/concursos.csv` | Exporta todos os concursos em CSV |
| GET | `/api/export/{tipo}/frequencia.csv` | Exporta frequência de números em CSV |
| GET | `/api/export/{tipo}/estatisticas.csv` | Exporta estatísticas completas em CSV |

### Import (Excel da Caixa)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/import/{tipo}/excel` | Importa arquivo Excel enviado via upload |
| POST | `/api/import/{tipo}/download-excel` | Baixa Excel direto da Caixa e importa |
| POST | `/api/import/download-excel-all` | Baixa e importa todas as loterias |

### Tipos de Loteria Válidos

Use um dos seguintes valores para `{tipo}`:
- `MEGA_SENA` ou `mega-sena`
- `LOTOFACIL` ou `lotofacil`
- `QUINA` ou `quina`
- `LOTOMANIA` ou `lotomania`
- `TIMEMANIA` ou `timemania`
- `DUPLA_SENA` ou `dupla-sena`
- `DIA_DE_SORTE` ou `dia-de-sorte`
- `SUPER_SETE` ou `super-sete`
- `MAIS_MILIONARIA` ou `mais-milionaria`

### Exemplos de Uso

```bash
# Carga completa - baixar TODOS os concursos históricos da Mega-Sena (demora ~30min)
curl -X POST http://localhost:8080/api/concursos/mega-sena/sync-full

# Sincronizar apenas novos concursos da Mega-Sena (rápido)
curl -X POST http://localhost:8080/api/concursos/mega-sena/sync

# Ver os 10 números mais frequentes da Mega-Sena
curl http://localhost:8080/api/estatisticas/mega-sena/mais-frequentes

# Ver números que mais saíram em concursos com ganhadores
curl http://localhost:8080/api/estatisticas/mega-sena/mais-frequentes-com-ganhadores

# Ver números mais atrasados
curl http://localhost:8080/api/estatisticas/lotofacil/atrasados?quantidade=15

# Histórico do número 10 na Lotofácil
curl http://localhost:8080/api/estatisticas/lotofacil/numero/10

# Pares de números que mais saem juntos
curl http://localhost:8080/api/estatisticas/mega-sena/correlacao?quantidade=10

# Números que mais acompanham o número 10
curl http://localhost:8080/api/estatisticas/mega-sena/acompanham/10

# Gerar 3 jogos inteligentes com números quentes e balanceamento par/ímpar
curl -X POST http://localhost:8080/api/estatisticas/mega-sena/gerar-jogos \
  -H "Content-Type: application/json" \
  -d '{"quantidadeJogos":3,"usarNumerosQuentes":true,"balancearParesImpares":true}'

# Verificar se uma aposta teria ganho nos últimos 100 concursos
curl -X POST http://localhost:8080/api/apostas/mega-sena/verificar \
  -H "Content-Type: application/json" \
  -d '{"numeros":[4,8,15,16,23,42]}'

# Simular apostas e calcular ROI
curl -X POST http://localhost:8080/api/apostas/mega-sena/simular \
  -H "Content-Type: application/json" \
  -d '{"jogos":[[4,8,15,16,23,42],[1,2,3,4,5,6]],"valorAposta":5.00}'

# Exportar concursos em CSV
curl -O http://localhost:8080/api/export/mega-sena/concursos.csv

# Importar Excel local
curl -X POST http://localhost:8080/api/import/mega-sena/excel \
  -F "file=@mega-sena.xlsx"

# Baixar Excel da Caixa e importar automaticamente
curl -X POST http://localhost:8080/api/import/mega-sena/download-excel

# Baixar e importar todas as loterias de uma vez
curl -X POST http://localhost:8080/api/import/download-excel-all
```

## Sincronização Automática

O sistema sincroniza automaticamente novos concursos diariamente às 22:00 (configurável).

### Configuração

Em `application.yml`:

```yaml
loterias:
  sync:
    enabled: true  # Habilita/desabilita sincronização automática
    cron: "0 0 22 * * *"  # Horário da sincronização (22:00 todos os dias)
```

Para testes, você pode alterar para execução a cada hora:
```yaml
cron: "0 0 * * * *"
```

## Carga Inicial

Ao iniciar o sistema pela primeira vez, use o endpoint de sincronização para carregar todos os concursos históricos:

```bash
# Carregar TODOS os concursos históricos de uma loteria (recomendado)
# A Mega-Sena tem ~2970 concursos, demora aproximadamente 30 minutos
curl -X POST http://localhost:8080/api/concursos/mega-sena/sync-full

# Carregar todas as loterias (apenas novos concursos - use após a carga inicial)
curl -X POST http://localhost:8080/api/concursos/sync-all
```

**Loterias suportadas para sync-full:**
- `mega-sena` (~2970 concursos)
- `lotofacil` (~3600 concursos)  
- `quina` (~6940 concursos)
- `lotomania` (~2880 concursos)
- `timemania` (~2350 concursos)
- `dupla-sena` (~2920 concursos)
- `dia-de-sorte` (~1170 concursos)
- `super-sete` (~800 concursos)
- `mais-milionaria` (~325 concursos)

## Build

```bash
./mvnw clean package
```

O JAR executável será gerado em `target/loterias-analyzer-0.0.1-SNAPSHOT.jar`.

## Estrutura do Projeto

```
src/main/java/br/com/loterias/
├── LoteriasApplication.java          # Classe principal
├── controller/
│   ├── ConcursoController.java       # Endpoints de concursos
│   ├── EstatisticaController.java    # Endpoints de estatísticas
│   └── GlobalExceptionHandler.java   # Tratamento de erros
├── domain/
│   ├── dto/                          # Data Transfer Objects
│   ├── entity/                       # Entidades JPA
│   └── repository/                   # Repositórios Spring Data
├── service/
│   ├── CaixaApiClient.java           # Cliente REST para API da Caixa
│   ├── ConcursoMapper.java           # Mapeamento de DTOs para entidades
│   ├── ConcursoSyncService.java      # Serviço de sincronização
│   ├── EstatisticaService.java       # Serviço de estatísticas
│   ├── GeradorJogosService.java      # Gerador de jogos inteligentes
│   ├── VerificadorApostasService.java # Verificador de apostas
│   ├── SimuladorApostasService.java  # Simulador de ROI
│   └── ExportService.java            # Exportação CSV
└── scheduler/
    └── SyncScheduler.java            # Agendamento de sincronização
```

## Fonte de Dados

Os dados são obtidos da API oficial da Caixa Econômica Federal:
- Base URL: `https://servicebus2.caixa.gov.br/portaldeloterias/api/`

## Licença

Este projeto é para fins educacionais. Os dados das loterias pertencem à Caixa Econômica Federal.
