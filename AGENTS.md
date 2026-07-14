# Repository guide

## Purpose

`git-hotspot` is a small Java 17 CLI that counts how many commits changed each
file in one or more local Git repositories and writes a dark, self-contained
HTML report. It has no runtime libraries beyond the JDK, but requires the `git`
executable to be available on `PATH`.

## Source map

- `pom.xml`: Java 17 Maven JAR; manifest entry point is `com.mfaneli.Main`.
- `src/main/java/com/mfaneli/Main.java`: CLI parsing and orchestration.
- `GitHotspotAnalyzer.java`: repository validation, Git subprocess execution,
  and per-file change counting.
- `GitHotspotResult.java`: immutable commit count and file-count map.
- `RepositoryHotspotResult.java`: associates a repository with its result.
- `HtmlReportWriter.java`: writes the index and one detail page per repository;
  CSS is embedded in each HTML file.
- `src/main/resources/archetype-resources/` and
  `src/main/resources/META-INF/maven/archetype.xml`: legacy Maven Archetype
  scaffold. It is unrelated to the CLI flow; leave it alone unless the task is
  explicitly about removing or maintaining the archetype.

## Runtime flow and invariants

1. `Main` accepts `[-o|--output <directory>] <repo> [repo ...]`; the default
   report directory is `git-hotspot-report` in the current directory.
2. Paths are converted to absolute paths. At least one repository is required.
3. The analyzer considers committed history reachable from `HEAD` only. It runs
   `git rev-list HEAD`, then for every commit runs
   `git diff-tree --no-commit-id --name-only -r <commit>`.
4. Each nonblank output line counts as one change for that file path.
5. The writer creates `<output>/index.html` and one detail page per repository
   at `<output>/repositories/repository-N.html`.
6. Detail rows sort by descending change count, then ascending file name.
7. Keep all dynamic HTML values escaped through `escapeHtml`.

User-visible CLI/report text is currently Portuguese (without accents in some
terminal messages); Java identifiers are English. Follow the local style and do
not introduce a framework for this small dependency-free codebase without a
clear requirement.

## Build, run, and validation

```sh
mvn test
mvn package
java -jar target/git-hotspot-1.0-SNAPSHOT.jar --output /tmp/git-hotspot .
```

There are currently no actual test sources, so `mvn test` only verifies
compilation. For analyzer or report changes, add focused tests when practical
and also run the packaged JAR against a small Git repository. Check that the
index and its repository detail pages are created and linked correctly.

Do not commit generated `target/` content, reports, or IDE metadata. Preserve
unrelated working-tree changes.

## Known limitations (do not silently assume these are fixed)

- The root commit is included in `commitCount`, but files introduced by it are
  absent from `fileChanges` because `git diff-tree` is called without `--root`.
- Repository validation checks only that the path is a directory; invalid Git
  repositories fail later through the Git command.
- Analysis launches one Git process per commit and is sequential, so large
  histories can be slow.
- CLI errors are printed to stderr, but `Main` does not set a nonzero process
  exit code.
- Rename history is not followed; counts use the paths emitted by Git for each
  commit.

Treat these as behavioral context, not as instructions to change scope. If a
task addresses one of them, update this guide with the implementation.
