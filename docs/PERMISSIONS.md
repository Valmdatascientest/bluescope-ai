# Permissions Android

| Version | Permission runtime | Usage |
|---|---|---|
| Android 12 / API 31+ | `BLUETOOTH_SCAN` | Découverte BLE et Classic |
| Android 12 / API 31+ | `BLUETOOTH_CONNECT` | Nom, appairage, classe et GATT |
| Android 11 / API 30- | `ACCESS_FINE_LOCATION` | Exigence historique du scan Bluetooth |
| Android 11 / API 30- | `BLUETOOTH`, `BLUETOOTH_ADMIN` | Permissions normales historiques |

`BLUETOOTH_ADVERTISE`, la localisation en arrière-plan et les permissions réseau ne sont pas déclarées.

`neverForLocation` est déclaré sur `BLUETOOTH_SCAN` car BlueScope ne déduit ni ne collecte la position. Android peut alors filtrer certains beacons ; cette limite est volontaire et documentée.

Le scan est déclenché par l’utilisateur, limité à 5–60 secondes, arrêté quand demandé et n’est pas exécuté par un service d’arrière-plan. En cas de refus, la bibliothèque hors ligne reste disponible. Le Bluetooth n’est jamais activé silencieusement : Android doit présenter son dialogue système.

L’exploration GATT demande un consentement distinct. L’option de lecture ne concerne que les caractéristiques publiant `READ`; aucun contournement d’autorisation n’est tenté.
