# Experimental trusted HTTPS proxy transport

This is a programmatic-only opt-in, not a deployed service or Render-ready configuration. The CLI still requires direct TLS for production.

`createSafeBridgeServer({approvedOrigin:'https://your-approved-host.example', durableStore, trustedProxy:{addresses:['127.0.0.1']}})` creates a plain HTTP backend listener. Use it only behind a separately configured HTTPS terminator. Do not combine it with development or TLS options. All production prepared-grant and durable-state checks still apply.

Operators must bind the returned server to a private interface and enforce firewall/private ingress so only the named proxy can reach it. The proxy must overwrite (not append or preserve client input) `X-Forwarded-Proto` with exactly `https` only for HTTPS requests, preserve the approved Host, disable request/body/access logging containing coordinates or capabilities, and apply appropriate upstream limits. This module cannot prove those deployment controls. Plaintext backend traffic needs an appropriately isolated trusted network; use direct TLS otherwise.

The allowlist has 1–32 exact socket-address IP strings: no CIDRs, wildcard, hostname, zone IDs, or automatic IPv4-mapped IPv6 normalization. Explicitly configure the actual `remoteAddress` representation. Missing, repeated, chained, or non-HTTPS protocol headers fail closed. The approved Host and browser Origin checks remain in force. `Forwarded`, `X-Forwarded-Host`, and `X-Forwarded-For` never establish trust or identity. Rate limits use the socket peer, so clients behind one proxy share IP quotas. Do not solve this by blindly trusting forwarded client IPs. First valid authenticated Stop retains its existing rate-limit bypass.

This adds no proxy discovery, deployment credentials, DNS/TLS provisioning, provider configuration or external requests. Real hosting ingress, revocation/backup policy and device/browser testing remain separate work.
