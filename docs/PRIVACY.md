# Politique de confidentialité locale

BlueScope AI traite par défaut toutes les données sur l’appareil. Aucun compte, aucune télémétrie et aucun transfert distant ne sont intégrés.

Les observations peuvent contenir un nom annoncé, une adresse Bluetooth, des UUID, un niveau de signal et des données publicitaires bornées. Elles servent uniquement à afficher, identifier et retrouver les périphériques. Les adresses persistantes sont chiffrées avec une clé non exportable Android Keystore et les sauvegardes système de la base sont exclues.

L’utilisateur peut exporter un JSON. L’export standard masque les adresses et exclut les payloads bruts. Le fichier exporté sort alors du stockage privé de l’application et doit être protégé par l’utilisateur. L’import est borné à 5 Mo et 10 000 périphériques.

Toutes les données peuvent être supprimées depuis Paramètres. La désinstallation efface le stockage privé et les clés Keystore associées.

Une éventuelle IA distante future nécessitera un fournisseur configuré, une prévisualisation des données et un consentement explicite par requête. Cette version ne contient aucun fournisseur distant.
