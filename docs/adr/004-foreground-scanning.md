# ADR-004 — Scan de premier plan borné

Statut : accepté.

BLE et Classic s’exécutent séquentiellement dans une session utilisateur de 5 à 60 secondes. Cette décision réduit les conflits radio, la consommation et les permissions de fond, tout en donnant un état explicite à l’interface.
