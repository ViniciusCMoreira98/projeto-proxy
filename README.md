# Proxy de Rate Limit com Backpressure (Spring Boot)

Serviço proxy para a API pública `https://score.hsborges.dev/docs` que:
- Mantém 1 req/s para o upstream com controle adaptativo de penalidade (+2s).
- Faz backpressure com fila interna com prioridade e TTL.
- Cacheia respostas recentes para reduzir chamadas.
- Exponibiliza métricas Prometheus e health checks.

## Como rodar

Pré-requisitos: Java 21, Maven wrapper incluído.

Variáveis principais:
- `CLIENT_ID`: Client ID do provedor (obrigatório para chamadas reais).
  - **IMPORTANTE**: O Client ID deve ser válido e ativo no serviço upstream.
  - O valor padrão `1` é válido para testes, mas pode ser sobrescrito.

Comando:
```bash
CLIENT_ID=seu_id_valido ./mvnw spring-boot:run
```

Porta padrão: `8080`.

## Endpoints

- `GET /proxy/score?param=valor` — encaminha a chamada via fila e scheduler (resposta síncrona; 504 se TTL expirar).
  - Headers opcionais:
    - `x-priority: HIGH|MEDIUM|LOW` — prioridade na fila.
- `GET /metrics` — métricas Prometheus.
- `GET /health` — liveness/readiness via Actuator.
- Swagger (opcional): `GET /swagger-ui.html`.

## Métricas relevantes

- `proxy.queue.enqueued`, `proxy.queue.drop{reason=full|ttl|timeout}`
- `proxy.cache.hit`, `proxy.cache.miss`
- `proxy.upstream.success`, `proxy.upstream.errors`
- `proxy.upstream.latency` (Timer)
- `proxy.scheduler.interval.ms` (gauge)

## Configurações (application.properties)

```properties
proxy.upstream-base-url=https://score.hsborges.dev
proxy.client-id=${CLIENT_ID:changeme}
proxy.queue-max-size=200
proxy.queue-offer-timeout-ms=25
proxy.request-ttl-ms=10000
proxy.scheduler-base-interval-ms=1000
proxy.penalty-extra-delay-ms=2000
proxy.cache-max-size=1000
proxy.cache-ttl-ms=30000
```

Pode ser sobrescrito via variáveis de ambiente (`PROXY_*` ou `CLIENT_ID`).

## Design e Padrões

- Proxy Pattern: `ProxyController` mantém interface similar ao upstream e abstrai `CLIENT_ID`.
- Backpressure: `RequestQueue` (PriorityBlockingQueue + Semaphore) limita capacidade e ordem.
- Scheduler: `RateLimitedScheduler` emite no máximo 1 req/s; adapta intervalo quando detecta latência ~base+penalidade.
- Cache: Caffeine popula após sucesso e responde hits imediatamente.
- Resiliência: Resilience4j (CircuitBreaker + TimeLimiter) no `UpstreamClient`.

## Testes de Aceitação (script)

Exemplo de rajada controlada (20 req/1s):
```bash
seq 1 20 | xargs -I{} -P20 curl -s "http://localhost:8080/proxy/score?doc=123{}" -H "x-priority: MEDIUM" | wc -l
```
Observar em `/metrics` ~1 req/s no upstream e crescimento controlado da fila.

Penalidade proposital (sem proxy): faça 2 chamadas paralelas diretamente ao provedor e observe +2s; com o proxy ativo, a mesma carga mantém 1 req/s.

Timeout/Circuit Breaker: simule lentidão alterando `proxy.upstream-base-url` para um endpoint lento ou usando um proxy local que atrase >3s e observe o fallback e o estado do circuito nas métricas.

Política de Fila: envie `x-priority: HIGH` para priorização; requests com TTL expirado são descartados com métrica `drop{reason=ttl}`.

## Trade-offs

- O ajuste de cadência é heurístico simples baseado na latência observada.
- Controller retorna 202->sincrono: aqui optamos por bloquear até TTL para simplicidade do cliente; poderia ser assíncrono com polling.
- Cache por chave de query simples; pode ser estendido para headers relevantes.




