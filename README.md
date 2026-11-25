# Hecato

Goals of that project is index files more updated in some git project, that way identify possible risk when you need make a new chance in the project, showing what file normaly are changed.

## Technology
Java 21

## Como executar

```shell
mvn package
java -jar target/hecato-1.0-SNAPSHOT.jar /caminho/repositorio-1 /caminho/repositorio-2
```

O resultado será salvo na pasta `git-hotspot-report` no diretório atual, com a
seguinte estrutura:

```text
git-hotspot-report/
├── index.html
└── repositories/
    ├── repository-1.html
    └── repository-2.html
```

Para escolher outra pasta, use a opção `--output` (ou `-o`):

```shell
java -jar target/hecato-1.0-SNAPSHOT.jar --output relatorios/hotspots /caminho/repositorio-1 /caminho/repositorio-2
```

O `index.html` apresenta o resumo geral e uma lista de repositórios. Cada item
abre sua página na subpasta `repositories`, com os detalhes e os arquivos mais
alterados.

## Descoberta arquitetural (experimental)

```sh
java -jar target/hecato-1.0-SNAPSHOT.jar scan /caminho/repositorio
```

Requer JDK 21. Inventaria tipos, métodos e candidatos a mappings HTTP sem compilar ou executar o projeto analisado. Não resolve endpoints completos nem dependências ainda. Consulte [roadmap](docs/hecato.md).

## Grafo estrutural experimental

`java -jar target/hecato-1.0-SNAPSHOT.jar graph ./repo --json` exporta JSON v1; omita `--json` para TXT. POMs são lidos offline, sem resolver herança, perfis ou propriedades. Dependências são declarações, não dependências efetivas. Identificadores de código incluem linha e ainda não são estáveis entre revisões; não há resolução de chamadas nem ownership.

### Chamadas (experimental)

O grafo inclui JAVA_TYPE, JAVA_METHOD, CALL_SITE, POSSIBLE_CALL e UNRESOLVED_CALL. IDs de métodos usam arquivo, nome qualificado e assinatura textual, independentes de linha. POSSIBLE_CALL é hipótese lexical por receptor e aridade, não resolução semântica nem execução comprovada. Chamadas ambíguas ficam sem resolução; tipos locais/anônimos, imports, herança, despacho dinâmico e referências de método não são resolvidos. O inventário anterior permanece disponível; o grafo atualmente faz duas passagens AST.

## Rules preview

Create owners.txt with one `SCHEMA.TABLE=service-id` per line (or unqualified TABLE for unqualified SQL). Run `java -jar target/hecato-1.0-SNAPSHOT.jar review ./service orders owners.txt`. Findings are candidates, not proven violations. Review one service and database context at a time. `graph` includes mapping, endpoint, transaction and SQL evidence. `trace ./service <from-id> <to-id>` follows possible calls only. SQL support is deliberately narrow: simple literal SELECT/INSERT/UPDATE/DELETE, no quoted identifiers, subqueries, CTEs or dynamic SQL; receiver types are not verified. JPA mappings do not imply access; JPQL and repository CRUD resolution are not implemented. No full Spring route composition, semantic call resolution, cross-service graph, cycle rules or stable finding IDs yet. No findings does not mean compliance.

## REST policy preview

`analyze <directory> [rules.txt]` runs default REST rules without ownership. Optional UTF-8 rules file uses `REST-007=OFF` or `REST-002=ERROR` (INFO/WARNING/ERROR/OFF). `endpoints <directory>` lists lexical endpoint candidates. Supports literal single class prefix and method path arrays, composed verb annotations, request body and method-level NO_CONTENT. Generic RequestMapping verb is UNKNOWN; class path arrays, composed custom annotations, inherited mappings, runtime properties and semantic type resolution are not supported. Findings are candidates. Existing review command remains separate. YAML and category switches deferred.
