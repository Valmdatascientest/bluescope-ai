# Limites connues et améliorations

## Limites des API Android

- Classic ne découvre que les appareils actuellement découvrables. Les appareils appairés ne sont pas une preuve de proximité.
- Android ne fournit pas d’explorateur universel des fonctions propriétaires Classic. BlueScope n’ouvre aucun socket RFCOMM arbitraire.
- Un Company ID désigne l’organisation du champ publicitaire, pas nécessairement la marque commerciale du produit.
- RSSI décrit une tendance radio et ne mesure pas une distance fiable.
- Les adresses privées BLE peuvent tourner ; elles ne sont pas fusionnées automatiquement.
- `neverForLocation` peut filtrer certains beacons.
- Un UUID annoncé ne garantit pas qu’un service soit accessible. Un service découvert ne garantit pas qu’une commande soit autorisée.

## État fonctionnel

- Le catalogue SIG embarqué couvre les identifiants courants avec une architecture de mise à jour. L’intégration automatisée de la totalité du dépôt YAML officiel, avec validation juridique de redistribution, reste à industrialiser.
- L’export v1 contient la bibliothèque et les métadonnées utilisateur ; un export d’archive complet des observations/GATT/analyses est prévu en v2.
- La fusion manuelle d’alias et la résolution experte Dual Mode sont à ajouter.
- Aucun fournisseur LLM distant n’est livré ; le port et le modèle de consentement sont prêts.
- La couverture chiffrée Kover et les seuils par module restent à ajouter à la CI.

## Validations matérielles requises

Les points suivants ne peuvent pas être certifiés par un émulateur : Galaxy Z Flip 7/One UI, BLE legacy et extended advertising, adresse privée rotative, Dual Mode, Classic découvrable/non découvrable, GATT public/protégé/défaillant, sortie de portée, pairing système, écran éteint et économie batterie.
