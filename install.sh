#!/bin/sh
set -eu
cd "$(dirname "$0")"
mvn clean verify
mkdir -p "$HOME/.local/bin" "$HOME/.local/share/hecato"
cp target/hecato-1.0-SNAPSHOT.jar "$HOME/.local/share/hecato/hecato.jar"
cat > "$HOME/.local/bin/hecato" <<'EOF'
#!/bin/sh
exec java -jar "$HOME/.local/share/hecato/hecato.jar" "$@"
EOF
chmod +x "$HOME/.local/bin/hecato"
printf '%s\n' 'Hecato installed. Run hecato --help.'
case ":$PATH:" in
  *":$HOME/.local/bin:"*) ;;
  *) printf '%s\n' 'Add to your shell configuration: export PATH="$HOME/.local/bin:$PATH"' ;;
esac
