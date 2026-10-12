# L Chat

Clone de Snapchat écrit en **Kotlin** pour Android, avec backend Firebase.

## Fonctionnalités

- **Authentification** — inscription / connexion email + mot de passe
- **Multi-comptes** — switch entre comptes, dernier compte mémorisé
- **Mode sombre** — bascule clair/sombre (Me → Dark mode)
- **Snaps éphémères** — photo compressée en base64 dans la Realtime Database, envoyée uniquement à tes contacts, visible **une seule fois**
- **Chat 1:1 chiffré E2E** — ECDH P-256 + AES-256-GCM, la clé privée ne quitte jamais l'appareil
- **Appels** — sonnerie, décroché, minuteur, raccroché (signalisation temps réel)
- **Groupes** — création (min. 2 membres), admin, badge admin, édition du nom / description / icône, option "les membres peuvent modifier"
- **Contacts** — ajout / suppression depuis Me → Contacts
- **Stories 24h** — publications visibles 24 h avec visionneuse à progression
- **Caméra** — qualité maximale, flash auto/on/off, zoom pinch, mise au point tactile, flip avant/arrière
- **Auto-update** — Me → Check for updates : compare avec la dernière release GitHub, télécharge et installe l'APK

## Releases

Télécharger la dernière version : https://github.com/tear360/L-Chat/releases

## Mise en route

1. Cloner le dépôt
2. Remplacer `app/google-services.json` par celui de votre projet Firebase (package `com.lchat.app`)
3. Activer **Authentication → Email/Mot de passe** et **Realtime Database**
4. Coller les règles de `database.rules.json` dans l'onglet Règles
5. `./gradlew assembleDebug` ou ouvrir dans Android Studio

Pas de Firebase Storage : les images sont compressées (JPEG 720 px) et stockées en base64 dans la
Realtime Database, ce qui évite le plan Blaze payant. Les messages 1:1 sont chiffrés côté client
(la clé privée reste dans les préférences locales, jamais sur le serveur).

## Crédits

Projet entièrement réécrit à partir de [Snap-chat-clone](https://github.com/Developer668/Snap-chat-clone)
de [Developer668](https://github.com/Developer668) (licence MIT) — merci pour la base de départ.

## Licence

MIT — projet pédagogique, non affilié à Snap Inc. ni à Snapchat.