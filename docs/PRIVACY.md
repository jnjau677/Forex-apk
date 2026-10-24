# Privacy Policy (Draft)

> Template for the Play Store listing and Data safety form. Review with counsel before publishing, and fill in the placeholders marked with brackets.

Last updated: [date]

## What the app does

This app fetches foreign exchange market data from third-party providers (FCS API, Alpha Vantage), optionally summarizes it with an AI provider (OpenRouter), and displays price updates and simple technical-analysis signals.

## Data the app handles

| Data | Purpose | Stored on device | Leaves the device |
| ---- | ------- | ---------------- | ----------------- |
| Email address (optional local account) | Local sign-in identity | Yes (Room DB) | No |
| Password | Local sign-in | Only as a PBKDF2 salted hash | No |
| Watchlist symbols, settings, scan history | App features | Yes (Room DB) | No |
| Cached market prices | Offline display | Yes (Room DB) | No |
| Device IP address | Required by network protocols | No | Yes, visible to market-data and AI providers as part of API requests |

The app has no backend server. There are no server-side accounts and no server-side analytics.

## Permissions

- `INTERNET`: fetch market data.
- `POST_NOTIFICATIONS`: show signal alerts produced by the background scanner.

## Third parties

API requests are sent directly from the device to FCS API, Alpha Vantage, and (if enabled) OpenRouter. Those providers see the request and the originating IP address under their own privacy policies. The app does not sell or share user data with anyone.

## Data deletion

All user data lives on the device. Uninstalling the app (or signing out and clearing app storage) deletes it permanently.

## Children

The app is not directed at children under 13 and is not intended for their use.

## Play Console Data safety mapping (draft)

- Data collected: none transmitted to developer. Device IP is accessible to third-party API providers as part of normal requests.
- Data encrypted in transit: yes (HTTPS for all provider calls).
- Users can request data deletion: yes, by uninstalling (all data is local).

## Contact

[contact email]