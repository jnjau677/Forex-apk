# OpenRouter Proxy Reference (Cloudflare Worker)

The OpenRouter API key grants paid access. A key compiled into an APK can be extracted and abused, which means direct client usage is a financial risk. This reference proxy keeps the key server-side, adds a per-IP rate limit, and can be rotated without shipping a new app version.

## Worker implementation

`wrangler.toml`:

```toml
name = "forex-openrouter-proxy"
main = "src/worker.js"
compatibility_date = "2026-10-01"

[vars]
APP_KEY = "set-via-secret-instead"

[kv_namespaces]
binding = "RATE_LIMIT"
id = "<kv-namespace-id>"
```

Set secrets with:

```sh
wrangler secret put OPENROUTER_API_KEY
wrangler secret put APP_KEY
```

`src/worker.js`:

```js
const RATE_LIMIT_PER_MINUTE = 20;

export default {
  async fetch(request, env) {
    if (request.method !== 'POST') {
      return new Response('Method not allowed', { status: 405 });
    }
    const appKey = request.headers.get('X-App-Key');
    if (!appKey || appKey !== env.APP_KEY) {
      return new Response('Unauthorized', { status: 401 });
    }
    const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
    const bucket = Math.floor(Date.now() / 60000);
    const key = 'rate:' + ip + ':' + bucket;
    const current = parseInt((await env.RATE_LIMIT.get(key)) || '0', 10);
    if (current >= RATE_LIMIT_PER_MINUTE) {
      return new Response('Rate limit exceeded', { status: 429 });
    }
    await env.RATE_LIMIT.put(key, String(current + 1), { expirationTtl: 120 });
    const body = await request.text();
    const upstream = await fetch('https://openrouter.ai/api/v1/chat/completions', {
      method: 'POST',
      headers: {
        Authorization: 'Bearer ' + env.OPENROUTER_API_KEY,
        'Content-Type': 'application/json',
      },
      body,
    });
    return new Response(upstream.body, {
      status: upstream.status,
      headers: { 'Content-Type': 'application/json' },
    });
  },
};
```

## Deployment

1. `npm create cloudflare@latest forex-openrouter-proxy` (or add the worker to an existing project) and paste the files above.
2. Create the KV namespace and paste its id into `wrangler.toml`.
3. Set both secrets. Deploy with `wrangler deploy`.
4. Add a monthly budget alert in the Cloudflare dashboard, and set a hard spend cap on the OpenRouter account.

## Client wiring

- Replace the OpenRouter base URL in the app with the worker URL.
- Send the app key in the `X-App-Key` header on every request.
- Keep the request path and body shape identical to the OpenRouter API; the proxy is a transparent pass-through.

## Limitations and hardening

- The `APP_KEY` is still shipped inside the APK, so it is also extractable. It is nevertheless strictly better than shipping the billing key: it can be rotated server-side instantly, it grants no direct OpenRouter access, and the per-IP limit blunts abuse.
- For stronger abuse prevention, add Cloudflare Turnstile before the proxy, or issue per-install tokens once accounts exist server-side.
- Log proxy-side request counts (Workers analytics) and alert on anomalies.
- The worker passes the request body through unmodified. If the app later sends user content to the model, disclose that in the privacy policy.