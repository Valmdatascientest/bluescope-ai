# Architecture

## Vue d’ensemble

BlueScope suit Clean Architecture avec flux unidirectionnel et MVVM. Le domaine ne dépend d’Android ni des implémentations.

```text
Compose / ViewModels (app)
          |
          v
     core:domain <-----------------------------+
          ^                                    |
          |                                    |
     core:data --------------------------------+
       /   |   \
      v    v    v
bluetooth database ai       designsystem -> app
```

- `domain` définit les invariants : confiance 0..1, horodatages cohérents, preuve directe obligatoire pour un fait confirmé, consentements typés.
- `bluetooth` encapsule les callbacks Android dans des `Flow`, borne chaque session et ne déduit aucune identité inter-session hasardeuse.
- `data` orchestre Room, DataStore, chiffrement et mapping. C’est la seule couche qui assemble les sources.
- `ai` est déterministe, local et remplaçable via `IdentificationEngine`. `RemoteInferenceProvider` existe comme port mais aucune implémentation distante n’est fournie.
- `app` expose un `StateFlow` par ViewModel et transforme les intentions UI en cas d’usage.

## Flux de scan

```text
Consentement permission
  -> session Room
  -> BLE (fenêtre bornée)
  -> Classic (fenêtre bornée)
  -> normalisation/dédup session
  -> observation chiffrée
  -> analyse locale
  -> UI temps réel + cache Room
```

BLE et Classic ne tournent jamais simultanément. Une génération de session et les instances exactes des callbacks empêchent qu’un callback tardif contamine une nouvelle recherche.

## Identité et déduplication

- L’identité primaire est un UUID interne.
- Une adresse appairée/stable peut rapprocher une observation d’un appareil connu.
- Une adresse non prouvée stable n’est jamais utilisée pour fusionner silencieusement deux sessions.
- Le nom, le RSSI ou le Company ID ne suffisent jamais seuls à une fusion.
- Les adresses sont chiffrées AES-GCM avec clé Android Keystore ; un HMAC local non réversible permet la recherche.

## GATT

`GattExplorer` exige un `ExplorationConsent`. Il arrête le scan, se connecte avec `autoConnect=false`, découvre les services, sérialise au maximum 64 lectures autorisées et ferme toujours `disconnect()` puis `close()`. Les capacités `WRITE`, `NOTIFY` et `INDICATE` sont décrites mais jamais activées.

## Persistance

Room v1 contient : `devices`, `device_aliases`, `scan_sessions`, `observations`, `gatt_services`, `gatt_characteristics`, `gatt_descriptors`, `analyses`, `claims`, `evidence`, `user_metadata`. Les clés étrangères suppriment en cascade les données d’un périphérique. Le schéma JSON exporté est versionné dans `core/database/schemas`.

Chaque future version doit fournir une `Migration` explicite et un test avec `MigrationTestHelper`. Aucun fallback destructif n’est activé.

## Erreurs et résilience

Les couches exposent des erreurs typées (`BluetoothError`, `GattError`). Les `SecurityException`, timeouts, statuts GATT, arrêts radio et données publicitaires absentes sont convertis en états français actionnables. Les payloads sont copiés défensivement et bornés avant de franchir la frontière domaine.

## Extension IA distante

Une implémentation future doit :

1. rester désactivée par défaut ;
2. afficher la projection exacte envoyée ;
3. obtenir un `AiPrivacyConsent` ponctuel et révocable ;
4. retirer adresse et nom sauf opt-in séparé ;
5. conserver les preuves techniques et marquer les conclusions comme inférées.
