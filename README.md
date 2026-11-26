# Hecato

## Instalação rápida (Linux)

Requisitos: JDK 21, Maven e Git. Na pasta do Hecato, execute:

```sh
sh install.sh
```

Se necessário, adicione `~/.local/bin` ao PATH:

```sh
export PATH="$HOME/.local/bin:$PATH"
```

Depois, na pasta do projeto que deseja analisar:

```sh
hecato analyze
hecato graph --json
hecato endpoints
hecato hotspot
hecato --help
```

Para atualizar, execute novamente `sh install.sh` após atualizar o repositório. Para desinstalar, remova `~/.local/bin/hecato` e `~/.local/share/hecato/hecato.jar`. Os comandos Java abaixo continuam disponíveis.

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

## Functional policy preview

Run `mvn clean verify` then `java -jar target/hecato-1.0-SNAPSHOT.jar analyze ./service`. No ownership catalog required. `endpoints ./service` lists lexical routes. Optional third argument rules.txt supports `REST-007=OFF`, `category.TRANSACTIONS=OFF`, and INFO/WARNING/ERROR/OFF. Per-rule override wins. Existing review remains separate and must be used within a single known database context.

Rules: REST-001/002/003/007, ARCH-001, TX-001/002/003, PERSIST-001/002/003, MICRO-001, CODE-001. All findings are candidates except unresolved persistence, which is inconclusive. HTTP semantics reference: RFC 9110 sections 9.2.1, 9.3.1, 15.3.5. Resource naming is a convention, not an HTTP requirement.

Limitations: lexical simple annotation/type names, declared fields only for receiver heuristics, no local shadowing resolution, no effective Spring configuration or proxy semantics, incomplete route composition, no semantic call resolution across dependencies, no JPA CRUD-to-table resolution, no multi-datasource mapping, no cross-service graph or cycle checks, no effective timeout/retry analysis. MICRO-001 is informational inventory only. No findings is not proof of compliance. Performance work deferred. Do not use as a blocking CI gate before validating findings.

### Extended analysis

`analyze <directory> [rules.txt] [--json] [--ownership <service> <database-id> <owners.txt>]`.
Ownership is optional. Explicit database scope must be correct for ALL SQL evidence in the directory; do not use this option on mixed-datasource projects. Catalog keys are database-id:table or database-id:schema.table, matching lexical SQL evidence exactly. Example `orders-db:public.orders=orders`. No credentials needed. REST-004 checks declared PathVariable names; REST-005 compares simple unconditional routes (conditional mappings are skipped, application scope must be checked). Class path arrays are expanded. Generic RequestMapping HTTP methods remain unknown. JSON schema version 1. Existing graph and review commands remain. Static analysis limitations still apply; no runtime conclusions.

### JPA evidence
Direct JpaRepository<Entity,ID> declarations with uniquely named source entities and explicit @Entity/@Table mappings produce JPA_READ_CANDIDATE or JPA_WRITE_CANDIDATE for recognized CRUD calls on fields or parameters. Ownership consumes this evidence when explicitly scoped. No implicit naming strategy, indirect generic inheritance, cascade, JPQL, or datasource resolution. Simple-name matching remains lexical. Unqualified field calls in the call graph are considered unless a local declaration shadows the name. No performance optimization.

### HTTP configuration evidence
MICRO-002 flags explicitly declared zero Feign timeouts; MICRO-003 reports unresolved timeout values; MICRO-004 inventories retryer declarations for review. Supports simple application*.properties keys under spring.cloud.openfeign.client.config. Does not resolve profiles, YAML, environment, Java beans or effective runtime settings. Values are not emitted. Missing declarations are not violations.
