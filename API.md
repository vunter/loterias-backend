# Loterias Analyzer - Documentação da API

API REST para análise de loterias brasileiras da Caixa Econômica Federal.

**Base URL:** `http://localhost:8080`

**Swagger UI:** [`http://localhost:8080/docs`](http://localhost:8080/docs) ou [`/swagger-ui.html`](http://localhost:8080/swagger-ui.html)

**API Docs (JSON):** [`http://localhost:8080/api-docs`](http://localhost:8080/api-docs)

**Info:** [`http://localhost:8080/`](http://localhost:8080/)

> 📘 **Nota:** O Swagger UI mostra todos os endpoints documentados em Português BR com exemplos interativos para testar a API.

---

## Tipos de Loteria Suportados

| Tipo | Endpoint |
|------|----------|
| Mega-Sena | `mega_sena` |
| Lotofácil | `lotofacil` |
| Quina | `quina` |
| Lotomania | `lotomania` |
| Timemania | `timemania` |
| Dupla Sena | `dupla_sena` |
| Dia de Sorte | `dia_de_sorte` |
| Super Sete | `super_sete` |
| +Milionária | `mais_milionaria` |

---

## Endpoints

### 📊 Concursos

#### Obter último concurso
```http
GET /api/concursos/{tipo}/ultimo
```

**Exemplo:**
```bash
curl http://localhost:8080/api/concursos/mega_sena/ultimo
```

**Resposta:**
```json
{
  "id": 13921,
  "tipoLoteria": "MEGA_SENA",
  "numero": 2966,
  "dataApuracao": "2026-01-29",
  "dezenasSorteadas": [6, 7, 9, 43, 44, 53],
  "acumulado": true,
  "valorArrecadado": 86209686.00,
  "valorAcumuladoProximoConcurso": 101637878.98,
  "faixasPremiacao": [...]
}
```

#### Obter concurso específico
```http
GET /api/concursos/{tipo}/{numero}
```

**Exemplo:**
```bash
curl http://localhost:8080/api/concursos/lotofacil/3500
```

---

### 📈 Estatísticas

#### Frequência das dezenas
```http
GET /api/estatisticas/{tipo}/frequencia
```

**Exemplo:**
```bash
curl http://localhost:8080/api/estatisticas/mega_sena/frequencia
```

**Resposta:**
```json
{
  "1": 295,
  "2": 298,
  "3": 287,
  ...
}
```

#### Dezenas mais frequentes
```http
GET /api/estatisticas/{tipo}/mais-frequentes
```

#### Dezenas menos frequentes
```http
GET /api/estatisticas/{tipo}/menos-frequentes
```

#### Dezenas mais frequentes em concursos com ganhadores
```http
GET /api/estatisticas/{tipo}/mais-frequentes-com-ganhadores
```

#### Dezenas atrasadas (não sorteadas há mais tempo)
```http
GET /api/estatisticas/{tipo}/atrasados
```

**Exemplo:**
```bash
curl http://localhost:8080/api/estatisticas/quina/atrasados
```

**Resposta:**
```json
{
  "30": 91,
  "61": 60,
  "37": 52,
  ...
}
```

#### Correlação entre dezenas (pares mais frequentes)
```http
GET /api/estatisticas/{tipo}/correlacao
```

**Exemplo:**
```bash
curl http://localhost:8080/api/estatisticas/mega_sena/correlacao
```

**Resposta:**
```json
{
  "38-53": 41,
  "05-27": 41,
  "10-33": 40,
  ...
}
```

#### Distribuição pares/ímpares
```http
GET /api/estatisticas/{tipo}/pares-impares
```

#### Soma média das dezenas
```http
GET /api/estatisticas/{tipo}/soma-media
```

#### Dezenas sequenciais
```http
GET /api/estatisticas/{tipo}/sequenciais
```

#### Dezenas que acompanham uma dezena específica
```http
GET /api/estatisticas/{tipo}/acompanham/{numero}
```

**Exemplo:**
```bash
curl http://localhost:8080/api/estatisticas/mega_sena/acompanham/10
```

---

### 🎲 Gerador de Jogos

#### Gerar jogos inteligentes
```http
POST /api/estatisticas/{tipo}/gerar-jogos
Content-Type: application/json

{
  "quantidade": 5
}
```

**Exemplo:**
```bash
curl -X POST http://localhost:8080/api/estatisticas/mega_sena/gerar-jogos \
  -H "Content-Type: application/json" \
  -d '{"quantidade": 5}'
```

**Resposta:**
```json
{
  "tipo": "MEGA_SENA",
  "jogos": [
    [1, 24, 25, 39, 46, 49],
    [5, 10, 23, 33, 42, 53],
    ...
  ],
  "estrategia": "Aleatório",
  "geradoEm": "2026-01-30T19:20:35.972825235"
}
```

---

### ✅ Verificador de Apostas

#### Verificar aposta em concursos
```http
POST /api/apostas/{tipo}/verificar
Content-Type: application/json

{
  "numeros": [6, 7, 9, 43, 44, 53],
  "concursoInicio": 2956,
  "concursoFim": 2966
}
```

**Exemplo:**
```bash
curl -X POST http://localhost:8080/api/apostas/mega_sena/verificar \
  -H "Content-Type: application/json" \
  -d '{"numeros": [6,7,9,43,44,53], "concursoInicio": 2956, "concursoFim": 2966}'
```

**Resposta:**
```json
{
  "numerosApostados": [6, 7, 9, 43, 44, 53],
  "resultados": [
    {
      "concurso": 2966,
      "data": "2026-01-29",
      "dezenasSorteadas": [6, 7, 9, 43, 44, 53],
      "acertos": [6, 7, 9, 43, 44, 53],
      "quantidadeAcertos": 6,
      "faixaPremiacao": "6 acertos",
      "valorPremio": 0.00
    },
    ...
  ],
  "resumo": {
    "totalConcursosVerificados": 11,
    "totalAcertos4mais": 1,
    "totalAcertos5mais": 1,
    "totalPremiacoes": 1,
    "valorTotalPremios": 0.00
  }
}
```

---

### 🎰 Simulador de Apostas

#### Simular apostas em histórico completo
```http
POST /api/apostas/{tipo}/simular
Content-Type: application/json

{
  "jogos": [[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]],
  "concursoInicio": 1,
  "concursoFim": 3600,
  "valorAposta": 3.00
}
```

**Exemplo:**
```bash
curl -X POST http://localhost:8080/api/apostas/lotofacil/simular \
  -H "Content-Type: application/json" \
  -d '{"jogos": [[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]]}'
```

**Resposta:**
```json
{
  "premiacoes": [
    {
      "concurso": 2060,
      "data": "2020-10-19",
      "jogoApostado": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15],
      "dezenasSorteadas": [3, 5, 9, 13, 14, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25],
      "acertos": 5,
      "faixa": "15 acertos",
      "premio": 1019961.91
    },
    ...
  ],
  "distribuicaoAcertos": {
    "15 acertos": 4,
    "14 acertos": 0,
    "13 acertos": 5,
    "12 acertos": 54,
    "11 acertos": 324,
    ...
  }
}
```

---

### 📥 Importação de Dados

#### Baixar e importar todos os Excels da Caixa
```http
POST /api/import/download-excel
```

**Exemplo:**
```bash
curl -X POST http://localhost:8080/api/import/download-excel
```

**Resposta:**
```json
{
  "resultados": {
    "Mega-Sena": {"importados": 0, "ignorados": 2966, "tempoMs": 354},
    "Lotofácil": {"importados": 0, "ignorados": 3600, "tempoMs": 845},
    ...
  },
  "totalImportados": 0,
  "totalIgnorados": 23939,
  "erros": 0,
  "tempoMs": 845
}
```

#### Importar Excel local de uma loteria
```http
POST /api/import/{tipo}/local-excel
```

#### Upload de Excel
```http
POST /api/import/{tipo}/excel
Content-Type: multipart/form-data

file: <arquivo.xlsx>
```

#### Status dos arquivos Excel
```http
GET /api/import/status
```

---

### 📤 Exportação (CSV)

#### Exportar concursos
```http
GET /api/export/{tipo}/concursos.csv
```

**Exemplo:**
```bash
curl http://localhost:8080/api/export/mega_sena/concursos.csv -o megasena.csv
```

#### Exportar frequência das dezenas
```http
GET /api/export/{tipo}/frequencia.csv
```

#### Exportar estatísticas completas
```http
GET /api/export/{tipo}/estatisticas.csv
```

---

## Códigos de Resposta

| Código | Descrição |
|--------|-----------|
| 200 | Sucesso |
| 400 | Requisição inválida (tipo de loteria inválido, parâmetros incorretos) |
| 404 | Recurso não encontrado |
| 500 | Erro interno do servidor |

---

## Exemplos de Uso

### Análise completa de uma loteria

```bash
# 1. Ver último resultado
curl http://localhost:8080/api/concursos/mega_sena/ultimo

# 2. Ver dezenas mais frequentes
curl http://localhost:8080/api/estatisticas/mega_sena/mais-frequentes

# 3. Ver dezenas atrasadas
curl http://localhost:8080/api/estatisticas/mega_sena/atrasados

# 4. Gerar jogos baseados em estatísticas
curl -X POST http://localhost:8080/api/estatisticas/mega_sena/gerar-jogos \
  -H "Content-Type: application/json" -d '{"quantidade": 3}'

# 5. Verificar se um jogo teria ganhado nos últimos 100 concursos
curl -X POST http://localhost:8080/api/apostas/mega_sena/verificar \
  -H "Content-Type: application/json" \
  -d '{"numeros": [5,10,17,33,42,53], "concursoInicio": 2866, "concursoFim": 2966}'
```

### Simular estratégia de apostas

```bash
# Simular apostando sempre nos números 1-15 na Lotofácil
curl -X POST http://localhost:8080/api/apostas/lotofacil/simular \
  -H "Content-Type: application/json" \
  -d '{"jogos": [[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]]}'
```

---

## Tecnologias

- Java 25
- Spring Boot 4.0.1
- Spring WebFlux (Netty)
- PostgreSQL
- Apache POI (Excel)
- SpringDoc OpenAPI (Swagger)
