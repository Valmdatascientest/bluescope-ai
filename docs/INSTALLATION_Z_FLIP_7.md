# Installation sur Samsung Galaxy Z Flip 7

1. Construire l’APK avec `./gradlew assembleDebug` ou récupérer l’artefact CI `bluescope-debug-apk`.
2. Activer les options développeur sur le téléphone puis le débogage USB.
3. Relier le téléphone, accepter l’empreinte RSA et vérifier `adb devices`.
4. Installer sans effacer les données :

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

5. Ouvrir BlueScope AI et accorder « Appareils à proximité » au premier scan.

Pour une installation manuelle, transférer l’APK puis autoriser ponctuellement la source d’installation dans les réglages Samsung. Révoquer cette autorisation ensuite.

## Validation Flip 7

Tester le mode ouvert et les transitions de pliage, thèmes clair/sombre, rotation, économie d’énergie, Bluetooth désactivé pendant le scan et reprise après retour au premier plan. One UI peut limiter les tâches en arrière-plan ; BlueScope ne dépend volontairement d’aucun scan en arrière-plan.

Android 2026 peut exiger l’enregistrement du développeur pour certaines distributions sur appareils certifiés. L’APK debug via ADB reste destiné au développement ; une diffusion utilisateur exige une signature release et le canal de distribution approprié.
