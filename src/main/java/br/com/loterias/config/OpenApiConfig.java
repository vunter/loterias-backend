package br.com.loterias.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI loteriasOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Loterias Analyzer API")
                        .description("""
                                API para análise de loterias brasileiras da Caixa Econômica Federal.
                                
                                ## Funcionalidades
                                
                                - **Estatísticas**: Frequência, dezenas atrasadas, correlações
                                - **Gerador de Jogos**: Geração inteligente baseada em estatísticas
                                - **Verificador**: Confira seus jogos contra resultados históricos
                                - **Simulador**: Simule apostas em todo o histórico
                                - **Importação**: Baixe dados diretamente da Caixa
                                - **Exportação**: Exporte dados em CSV
                                
                                ## Loterias Suportadas
                                
                                | Código | Nome |
                                |--------|------|
                                | `mega_sena` | Mega-Sena |
                                | `lotofacil` | Lotofácil |
                                | `quina` | Quina |
                                | `lotomania` | Lotomania |
                                | `timemania` | Timemania |
                                | `dupla_sena` | Dupla Sena |
                                | `dia_de_sorte` | Dia de Sorte |
                                | `super_sete` | Super Sete |
                                | `mais_milionaria` | +Milionária |
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Loterias Analyzer")
                                .url("https://github.com/loterias-analyzer"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of())
                .tags(List.of(
                        new Tag().name("Concursos").description("Consulta e sincronização de resultados de concursos"),
                        new Tag().name("Estatísticas").description("Análises estatísticas: frequência, correlação, padrões"),
                        new Tag().name("Apostas").description("Verificação de apostas contra resultados"),
                        new Tag().name("Simulador").description("Simulação de apostas em histórico completo"),
                        new Tag().name("Importação").description("Download e importação de dados da Caixa"),
                        new Tag().name("Exportação").description("Exportação de dados em formato CSV")
                ));
    }

    @Bean
    public OpenApiCustomizer tipoLoteriaEnumCustomizer() {
        List<String> tiposLoteria = List.of(
                "mega_sena", "lotofacil", "quina", "lotomania", "timemania",
                "dupla_sena", "dia_de_sorte", "super_sete", "mais_milionaria"
        );

        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (pathItem.getGet() != null && pathItem.getGet().getParameters() != null) {
                pathItem.getGet().getParameters().forEach(param -> {
                    if ("tipo".equals(param.getName()) && "path".equals(param.getIn())) {
                        param.setSchema(new StringSchema()._enum(tiposLoteria));
                    }
                });
            }
            if (pathItem.getPost() != null && pathItem.getPost().getParameters() != null) {
                pathItem.getPost().getParameters().forEach(param -> {
                    if ("tipo".equals(param.getName()) && "path".equals(param.getIn())) {
                        param.setSchema(new StringSchema()._enum(tiposLoteria));
                    }
                });
            }
            if (pathItem.getDelete() != null && pathItem.getDelete().getParameters() != null) {
                pathItem.getDelete().getParameters().forEach(param -> {
                    if ("tipo".equals(param.getName()) && "path".equals(param.getIn())) {
                        param.setSchema(new StringSchema()._enum(tiposLoteria));
                    }
                });
            }
        });
    }
}
