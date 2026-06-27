# Urban Operations Center Specification (v1.0)

## 1. Product Vision
Urban Operations Center will be the central web command platform for Urban Platform. It aims to provide real-time visibility, administrative control, data auditing, and advanced analytics for large-scale field operations. It transforms raw data captured by the Android fleet into actionable intelligence for supervisors and clients.

## 2. Target Users
*   **Administrator**: Manages organizations, projects, licensing, and high-level platform configuration.
*   **Supervisor**: Monitors live operations, audits trip quality, and manages field teams.
*   **Field Coordinator**: Tracks device health (battery, GPS precision) and ensures data synchronization.
*   **Analyst**: Processes exported data and builds operational reports.
*   **Client / Consult**: Read-only access to specific project progress and verified data.

## 3. Core Modules

### A. Live Devices
*   Real-time map showing current location of all active devices.
*   Status indicators: Battery level, GPS accuracy, and current connectivity.
*   Operational context: Active trip ID, assigned operator, and sync queue status.

### B. Trips Explorer
*   Searchable/filterable table of all trips across projects.
*   Filters: Date range, operator, route, status (Active/Closed/Synced), and device.

### C. Trip Detail
*   Full geographical track visualization.
*   Event timeline (Boarding, Alighting, Delays) mapped to coordinates.
*   GPS Quality heatmap (Excellent to Lost).
*   Sync metadata and integrity audit flags.

### D. Export Center
*   Standardized downloads: CSV, Excel, KML, GPX (Garmin compatible).
*   Batch processing: ZIP archives of multiple trips or entire project periods.

### E. Quality & Audit
*   Automatic detection of GPS gaps and jumps.
*   Flags for incomplete trips or suspicious passenger counts.
*   Supervisor notes and "Verified" status marking.

### F. Administration
*   Hierarchical management: Organizations > Projects > Workspaces.
*   User management (RBAC): Roles and granular permissions.
*   Remote Configuration: Dynamic adjustment of heartbeat and sync intervals.
*   Licensing: Management of active seats and expiration dates.

### G. Analytics
*   Productivity trends and project completion percentages.
*   Sync latency monitoring.
*   GPS quality distribution reports.

---

## 4. Firestore Data Contract

The platform uses a standardized path structure to ensure multi-tenancy and data isolation:

| Entity | Firestore Path |
| :--- | :--- |
| **Organization** | `asd_organizations/{organizationId}` |
| **Project** | `asd_organizations/{orgId}/projects/{projectId}` |
| **Installation** | `asd_organizations/{orgId}/projects/{projId}/installations/{id}` |
| **Heartbeat** | `asd_organizations/{orgId}/projects/{projId}/heartbeats/{id}` |
| **Trip** | `asd_organizations/{orgId}/projects/{projId}/trips/{cloudTripId}` |
| **Event** | `.../trips/{cloudTripId}/events/{cloudEventId}` |
| **Track Chunk** | `.../trips/{cloudTripId}/track_chunks/{chunkId}` |
| **Config** | `.../projects/{projId}/configuration/current` |
| **User** | `asd_organizations/{orgId}/users/{userId}` |
| **Role** | `asd_organizations/{orgId}/roles/{roleId}` |
| **Permission** | `asd_organizations/{orgId}/permissions/{permId}` |
| **License** | `asd_organizations/{orgId}/licenses/{licenseId}` |

---

## 5. Minimum Expected Fields

*   **Trip**: `cloudTripId`, `localTripId`, `routeName`, `direction`, `tripNumber`, `startTime`, `endTime`, `status`, `deviceInstallationId`, `updatedAt`.
*   **Event**: `cloudEventId`, `cloudTripId`, `eventType`, `lat`, `lon`, `accuracy`, `menUp`, `womenUp`, `menDown`, `womenDown`, `onboardTotal`, `timestamp`.
*   **Track Chunk**: `chunkId`, `cloudTripId`, `points` (List of `lat,lon,acc,time`), `startTime`, `endTime`.
*   **Heartbeat**: `installationId`, `batteryLevel`, `gpsStatus`, `activeTripId`, `pendingSyncCount`, `lastLat`, `lastLon`, `updatedAt`.
*   **Configuration**: `environment`, `heartbeatIntervalSeconds`, `syncIntervalSeconds`, `featureFlags` (Map), `enabledModules`.

---

## 6. Operational States

*   **Device Status**: `ONLINE`, `WARNING` (low battery/old heartbeat), `OFFLINE`, `GPS_DEGRADED`.
*   **Trip Status**: `ACTIVE`, `CLOSED`, `SYNC_PENDING`, `SYNCED`, `PARTIAL`, `ERROR`.
*   **GPS Quality**: `EXCELLENT` (<10m), `GOOD` (<25m), `USABLE` (<45m), `DEGRADED`, `LOST`.

---

## 7. MVP 1 (Minimum Viable Product)
1.  **Device Monitor**: Dashboard showing basic metrics and last known location.
2.  **Trips Table**: Basic list with filters by Date and Project.
3.  **Basic Detail**: Map view of a single trip with its events.
4.  **CSV Export**: Single trip download.
5.  **Multi-tenancy**: Workspace-based data filtering.

---

## 8. Roadmap
*   **Phase 1**: Cloud Data Contract (Hardening existing Android-to-Cloud sync).
*   **Phase 2**: Operations MVP (Live map and basic trip viewing).
*   **Phase 3**: Export Center (Advanced Excel/KML generation).
*   **Phase 4**: Quality & Audit (Automated data validation).
*   **Phase 5**: Admin & Licensing (Web-based project setup).
*   **Phase 6**: Analytics (Power BI / BigQuery integration).

---

## 9. Architectural Decisions
1.  **Read-Dominant**: Web platform acts primarily as a consumer of Firestore data.
2.  **Validation First**: Critical operational changes (like project closing) must be validated via Cloud Functions, not direct Firestore writes.
3.  **Client-Side Export**: Initial exports will be generated in the browser to reduce backend costs, moving to server-side only for large batches.
4.  **Decoupled Logic**: Export logic must mirror Android implementations (e.g., CSV formatting) but remain independent codebases.

## 10. Risks
*   **Firestore Costs**: High read volume for live maps and large track collections.
*   **Security**: Granular rules required to prevent cross-organization data leaks.
*   **Data Volume**: Managing millions of track points per month.
*   **Sync Latency**: Impact of high sync intervals on "Live" perception.

## 11. Open Questions
*   **Technology Stack**: Next.js/React or similar?
*   **Hosting**: Firebase Hosting + Cloud Functions or Containerized (GCP)?
*   **BI Integration**: Should we stream data to BigQuery for Power BI or use the Firestore connector?
*   **Business Model**: SaaS per organization/project or white-label?
