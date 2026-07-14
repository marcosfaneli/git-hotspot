# git-hotspot

Goals of that project is index files more updated in some git project, that way identify possible risk when you need make a new chance in the project, showing what file normaly are changed.

## Technology
Java 17

## Como executar

```shell
mvn package
java -jar target/git-hotspot-1.0-SNAPSHOT.jar /caminho/repositorio-1 /caminho/repositorio-2
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
java -jar target/git-hotspot-1.0-SNAPSHOT.jar --output relatorios/hotspots /caminho/repositorio-1 /caminho/repositorio-2
```

O `index.html` apresenta o resumo geral e uma lista de repositórios. Cada item
abre sua página na subpasta `repositories`, com os detalhes e os arquivos mais
alterados.
