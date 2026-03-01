# Architecture Documentation

## 1. Architecture Choice and Rationale

SurveySyncEngine adopts **Clean Architecture with MVVM pattern**, organized into four modules:
`app`, `presentation`, `domain`, and `data`. This architecture was chosen for its clear separation
of concerns, testability, and scalability—critical for an offline-first field survey application.

**Why Clean Architecture?** Field surveys often occur in remote agricultural regions with unreliable
connectivity. Clean Architecture enables:

- **Independent testability**: Domain logic can be tested without Android dependencies
- **Offline-first design**: Data layer abstracts local (Room) and remote (Retrofit) sources behind a
  single repository interface
- **Device-aware optimization**: Use cases encapsulate business rules for battery, storage, and
  network-conscious sync decisions

**Alternatives considered:**

- **Single-module MVC**: Rejected due to poor testability and tight coupling between UI and business
  logic
- **MVP**: Rejected because presenter-view coupling makes UI testing difficult with Jetpack Compose
- **MVI**: Rejected as overkill; the unidirectional data flow adds complexity without significant
  benefit for this use case

The `domain` layer contains pure Kotlin business logic (use cases, models, repository interfaces),
while `data` handles implementation details (Room DAOs, Retrofit APIs, device managers). The
`presentation` layer uses Jetpack Compose with ViewModels exposing StateFlow for reactive UI
updates. Background sync is handled by WorkManager with circuit breaker patterns to prevent battery
drain from repeated failed attempts.

---

## 2. Media Compression Extension Strategy

Currently, `ImageCompressionUtil.kt` compresses photos to 1920x1080 resolution at 80% JPEG quality
with EXIF preservation, reducing 4-12 MB images to 200-500 KB (80-95% reduction). To extend this
with adaptive compression based on network conditions:

**Proposed pipeline:**

1. **Pre-upload compression check** in `UploadMediaAttachmentsUseCase`:
    - **WiFi connection**: Compress to 1080p at 80% quality (current behavior)
    - **Cellular connection**: Compress more aggressively to 720p at 60% quality to reduce data
      usage
    - **Weak network**: Queue for later WiFi sync or compress to 480p at 50% quality

2. **Implementation approach:**
    - Add `CompressionProfile` enum (HIGH, MEDIUM, LOW) with dimension/quality presets
    - Inject `ConnectivityManager` into compression utility to detect network type
    - Execute compression on IO dispatcher (already implemented) to avoid blocking sync
    - Preserve EXIF GPS coordinates, timestamps, and device metadata regardless of compression level
    - Log compression metrics (original size, final size, time taken) for monitoring

This adaptive strategy balances image quality with network constraints, extending device battery
life by 8-20x for cellular uploads.

---

## 3. Network Detection Edge Cases and Mitigation

**Scenario: Captive Portal WiFi (False Positive)**
In hotels, airports, or public spaces, Android's `ConnectivityManager` may report WiFi as "
connected" with `NET_CAPABILITY_INTERNET`, but actual HTTP requests fail due to captive portal
redirects requiring authentication. This causes the app to attempt WiFi sync, fail repeatedly, drain
battery, and block cellular fallback.

**Current detection limitation:**
`ConnectivityManager` checks `linkDownstreamBandwidthKbps` for weak connections (< 14.4 Kbps
threshold) but doesn't validate actual internet reachability. The circuit breaker in
`NetworkHealthTracker` only opens after 3 consecutive failures, wasting retry attempts.

**Mitigation strategy:**

1. **Connectivity probe**: Before batch sync, ping a lightweight test endpoint (e.g.,
   `GET /api/health`) with 5-second timeout
2. **Early circuit breaking**: If probe fails on WiFi, immediately switch to cellular (if available)
   or skip sync
3. **Validation caching**: Cache successful probe results for 5 minutes to avoid redundant checks
4. **User notification**: Show "WiFi requires sign-in" warning and offer manual cellular sync option

This approach prevents wasted sync attempts while maintaining the benefits of automatic network
detection.

---

## 4. Remote Troubleshooting Strategy

Mobile field applications face unique debugging challenges: battery constraints prevent heavy APM (
Application Performance Monitoring) solutions like Datadog or Sentry, and unstable connectivity
makes real-time session replay unreliable. Our strategy prioritizes **battery-efficient local
logging** with **backend collaboration**.

**What to log locally:**

- Sync attempt metadata: timestamp, survey IDs, batch size, device resources (battery %, storage
  available)
- Error details: error code, HTTP status, network type, retry count, circuit breaker state
- Critical events: compression results, upload durations, cleanup operations

**Data exposure approach:**

1. **Batch upload diagnostics**: After successful sync, upload aggregated error logs to backend (
   minimal battery impact)
2. **Backend APM tracing**: Use server-side APM to trace API failures—more reliable than mobile
   monitoring
3. **In-app diagnostics screen**: Display last sync timestamp, pending survey count, recent error
   summary, network health status
4. **Export functionality**: Allow field agents to export sync logs as JSON/CSV via email or file
   sharing for offline support analysis

**Support workflow:**
When an agent reports sync failures, support team checks backend APM for server-side issues first,
then requests exported logs from the agent's device. This hybrid approach balances diagnostic depth
with battery preservation—critical for all-day field operations.

---

## 5. GPS Challenges for Field Boundary Capture

Adding GPS-based field boundary mapping to SurveySyncEngine introduces significant technical
challenges in rural agricultural environments:

**LocationRequest configuration recommendations:**

- **Precision requirement**: Use `PRIORITY_HIGH_ACCURACY` with `smallestDisplacement = 5-10m` for
  boundary mapping (sub-10m accuracy needed for land parcels)
- **Battery consideration**: For continuous tracking while walking field perimeters, use
  `PRIORITY_BALANCED_POWER_ACCURACY` with `interval = 2-5 seconds` (walking speed ~1.5 m/s)
- **Timeout handling**: Set `maxWaitTime = 30 seconds` to handle cold start delays when first
  acquiring satellite lock

**Technical challenges:**

1. **Poor satellite coverage**: Tree canopy in orchards/forests and valleys block GPS signals,
   degrading accuracy from 3-5m to 50-100m. Rural areas may have fewer ground-based GNSS correction
   stations.
2. **GPS drift**: Stationary points "wander" due to changing satellite geometry, creating polygon
   closure errors in field boundaries.
3. **Device hardware variation**: Low-end Android devices (common in agriculture) have cheaper GPS
   chipsets with worse accuracy than flagship models.
4. **Cold start delay**: First GPS fix can take 30-60 seconds in rural areas, frustrating field
   agents.
5. **Battery drain**: Continuous high-accuracy GPS can consume 5-10% battery per hour—problematic
   for all-day field surveys.

**Accuracy validation strategy:**

- **Satellite requirements**: Only accept readings with ≥4 satellites and HDOP (Horizontal Dilution
  of Precision) < 2.0
- **Cross-validation**: Compare GPS with cell tower triangulation; large discrepancies indicate poor
  GPS quality
- **Visual feedback**: Display accuracy circle (radius = `location.accuracy`) on map; warn if
  accuracy > 20m
- **Multi-sample averaging**: Capture 3-5 readings per boundary point and average coordinates;
  reject outliers using median filtering
- **Kalman filtering**: Apply Kalman filter to smooth GPS tracks and reduce drift noise
- **Ground truth calibration**: Allow agents to manually correct coordinates using satellite imagery
  overlay for known landmarks (field corners, buildings)

This multi-layered validation ensures reliable field boundary data despite challenging rural GPS
conditions.

---

## 6. Future Improvements with More Time

**Battery Optimization Enhancements:**

- **Survey Mode**: Implement a "Field Survey Mode" that temporarily restricts background app
  activity, limits location refresh rates, and pauses non-critical syncs—similar to power-saving
  modes but optimized for survey workflows
- **Smarter scheduling**: Replace frequent WorkManager periodic sync (4 hours) with JobScheduler for
  adaptive scheduling based on device state (charge level, WiFi availability)
- **Network batching**: Upload multiple surveys in a single HTTP connection instead of individual
  requests, reducing radio wake-ups and TCP handshake overhead

**Image Preprocessing Intelligence:**

- **Smart cropping**: Implement edge detection to auto-crop photos, removing unnecessary borders,
  sky, or ground—reduces file size without compression artifacts
- **Quality filtering**: Use ML-based blur/darkness detection to warn users before capturing
  unusable photos (e.g., TensorFlow Lite model for image quality scoring)
- **Selective capture**: Guide users to capture only essential evidence (e.g., "capture 2-3 photos
  per field") rather than exhaustive documentation

**Dynamic Question Management:**

- **Schema versioning**: Store question definitions in Room database with version numbers; sync
  updated schemas from backend without requiring app updates
- **Conditional logic**: Support skip patterns and branching (e.g., "If crop type = rice, ask
  irrigation questions; else skip")
- **Offline-first updates**: Download new survey templates while online, cache locally, and allow
  offline survey submission with any version
- **A/B testing**: Support multiple question variants for field testing survey designs without
  re-deploying the app

These improvements would extend battery life by 20-30%, reduce data usage by 40-50%, and enable
rapid iteration on survey designs—critical for scaling agricultural field operations.
