#!/usr/bin/env bash
set -euo pipefail

URL=${1:-http://localhost:8080/proxy/score}
echo "Enviando 20 requisicoes em 1s para $URL"
seq 1 20 | xargs -I{} -P20 curl -s "$URL?doc=123{}" -H "x-priority: MEDIUM" | wc -l
echo "Verifique /metrics para taxa e fila"




