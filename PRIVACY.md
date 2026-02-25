# Privacy Policy

**RoadTripRadar**
Last updated: February 25, 2026

## Overview

RoadTripRadar is a free and open-source Android application. This privacy policy
describes what data the app accesses, how it is used, and what is shared with
third parties.

**RoadTripRadar does not collect, store, or transmit any personal data.** There
are no user accounts, no analytics, no crash reporting, no advertising, and no
tracking of any kind.

## Location Data

The app requests access to your device's location (GPS) to center the map on
your current position and to compute distance and bearing to points of interest.

Your precise location is **never sent to any external service**. Only the map's
visible viewport coordinates (camera center and bounding box) are used as
parameters when searching for places or points of interest.

## Network Requests

The app communicates with the following third-party services:

| Service | Purpose | Data Sent |
|---------|---------|-----------|
| [RainViewer](https://www.rainviewer.com/) | Weather radar tile metadata | None (read-only request) |
| [Photon (Komoot)](https://photon.komoot.io/) | Place name search | Search query text, map viewport coordinates |
| [Nominatim (OpenStreetMap)](https://nominatim.openstreetmap.org/) | Point of interest search | POI category, map viewport bounding box |

No API keys, authentication tokens, or user identifiers are sent with any of
these requests. Map tiles are fetched directly by the map rendering engine from
tile servers based on the visible map area.

## Local Storage

The app stores your preferences (map style, units, radar opacity, saved POI,
etc.) locally on your device using Android SharedPreferences. This data never
leaves your device.

No database, cloud storage, or remote synchronization is used.

## Third-Party Services

The third-party services listed above have their own privacy policies:

- RainViewer: https://www.rainviewer.com/privacy
- Komoot (Photon): https://www.komoot.com/privacy
- OpenStreetMap (Nominatim): https://wiki.osmfoundation.org/wiki/Privacy_Policy

## Children's Privacy

The app does not knowingly collect any data from anyone, including children
under 13.

## Changes to This Policy

Any changes to this privacy policy will be reflected in this file in the
project's source repository.

## Contact

If you have questions about this privacy policy, you can reach the developer at
voiditswarranty@digiex.ca.
