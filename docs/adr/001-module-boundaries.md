# ADR-001 — Frontières de modules

Statut : accepté.

Le domaine Kotlin pur porte modèles et ports. Les modules Android Bluetooth, Room, data et UI dépendent vers le domaine, jamais l’inverse. Cette séparation permet les tests JVM, le remplacement du moteur IA et l’évolution des features sans exposer les API Android aux règles métier.
