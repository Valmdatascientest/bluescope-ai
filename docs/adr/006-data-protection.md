# ADR-006 — Minimisation et chiffrement

Statut : accepté.

Les adresses persistées sont chiffrées AES-GCM avec Android Keystore et indexées par HMAC local. Les sauvegardes sont exclues, les exports sont masqués par défaut et l’application ne journalise pas les identifiants ou payloads bruts.
