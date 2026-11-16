# Read-only provider/hosting investigation (2026-11-16)

No account, purchase, deployment or real-location request was performed.

## Practical private pilot, not emergency production

Render paid Node service + persistent disk fits the existing single-process SQLite design more directly than edge functions. Published compute starts at $7/month and disk is $0.25/GB/month: illustrative $7.25/month for compute + 1 GB disk, excluding other usage/taxes. Disk services are single-instance and do not support zero-downtime deployment. Backup restore can lose later revocations and does NOT solve the lab's coherent old-database rollback blocker.
https://render.com/pricing
https://render.com/docs/disks

Recommendation: local/regional OSM POI dataset served by the operator avoids sending every victim search coordinate to a separate POI API. Free regional Geofabrik extracts exist, but conversion/update/licensing/coverage and hosting are additional work. Region must be chosen; Colorado is only a possible pilot, not an assumed final scope. Basemap tiles need a separately approved hosted provider or own tile service.
https://www.geofabrik.de/data/download.html

Geoapify: free 3,000 credits/day, 5 requests/sec; API10 $59/month for 10,000 credits/day. Documentation supports police/hospital and internet conditions; general public-payphone coverage not established. Successful API requests generally retained up to 24 hours, including bodies/headers/IP/time, a consequential privacy tradeoff requiring explicit provider selection/disclosure.
https://www.geoapify.com/pricing/
https://www.geoapify.com/privacy-policy/
https://apidocs.geoapify.com/docs/places/

Google: Android Maps SDK pricing shows unlimited free usage; Nearby Search Pro 5,000 monthly free events, then $32/1,000 in first paid tier, with field masks affecting SKU. Billing account/API keys, terms/privacy attribution and storage rules apply. Google Places plotted on a map must use Google map, not OSM. This would require a different map integration, not plugging results into our OSM path.
https://developers.google.com/maps/billing-and-pricing/pricing
https://developers.google.com/maps/documentation/places/web-service/policies

Hosted Overpass-compatible Overspan lists $19/month, 50,000 requests, hard caps; provider's retrieved terms appeared future-dated November 17 relative to this context and describe beta/no SLA. Not a production recommendation without clarification. Public volunteer Overpass is not an appropriate app backend; own/provider capacity required.
https://overspan.dev/
https://overspan.dev/terms
https://dev.overpass-api.de/overpass-doc/en/preface/commons.html

## Decisions/authorization still required

Operator-selected hosting account, recurring total spending cap/payment method, explicit deployment authorization; provider and data-retention choice; server-only secrets; HTTPS domain/TLS/operations ownership; prepared-slot distribution and abuse policy; backup/revocation recovery. No reviewed provider guarantees accessible working Wi-Fi/public phones or complete safe-place coverage. Costs are indicative current published rates, not a quote or spending authorization.
