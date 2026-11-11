# Rapport de tests — 11 novembre 2026

## Validé localement

Environnement : macOS arm64, Android Studio 2026.1, JBR 25.0.3, SDK API 37, Build Tools 36.0.0, Gradle 9.6.0.

| Commande | Résultat |
|---|---|
| `./gradlew :app:assembleDebug` | Succès, APK debug généré |
| `./gradlew testDebugUnitTest :core:domain:test :core:ai:test` | Succès |
| `./gradlew :app:lintDebug` | Succès, aucune erreur lint |
| `./gradlew :app:assembleRelease` | Succès, R8 et réduction des ressources |
| `./gradlew :app:assembleDebugAndroidTest` | Succès, APK de tests instrumentés compilé |

Suites JVM présentes : 41 tests dans 11 suites, 0 échec et 0 ignoré. Elles couvrent les invariants domaine, l’égalité défensive des octets, la séparation confirmé/inféré, le catalogue et moteur IA, les permissions API 30/31+, le parser advertising malformé, le lissage RSSI, la déduplication de session et le mapping GATT.

Tests instrumentés présents : DAO Room/cascade et rendu/accessibilité d’une carte Compose. Le schéma Room v1 est exporté.

## Non exécuté localement

Aucun appareil ni émulateur utilisable n’était connecté. `connectedDebugAndroidTest` est donc configuré en CI API 35 mais non déclaré validé dans ce rapport. Les validations Bluetooth physiques listées dans [LIMITATIONS.md](LIMITATIONS.md) restent obligatoires avant une diffusion production.

## Couverture

Aucun pourcentage n’est annoncé sans mesure. Kover n’est pas encore configuré ; les rapports JUnit XML sont produits par Gradle et archivés par la CI.
