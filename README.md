# L Chat

Clone de Snapchat écrit en **Kotlin** pour Android, avec backend **Firebase**.
Le projet est un remaniement complet de [Snap-chat-clone](https://github.com/Developer668/Snap-chat-clone) :
même concept, mais code, architecture et UI entièrement refaits.

## Fonctionnalités

- **Authentification** — inscription / connexion email + mot de passe (Firebase Authentication)
- **Caméra temps réel** — preview CameraX, face avant / arrière, capture photo, import galerie
- **Snaps éphémères** — photo + légende, envoi à un ou plusieurs contacts, visionnables **une seule fois** puis supprimés automatiquement
- **Chat 1:1 temps réel** — messages texte instantanés (Firebase Realtime Database), indicateur "New Chat"
- **Stories 24h** — publication d'images visibles par tout le monde pendant 24h, visionneuse avec barre de progression (5 s par snap, tap gauche/droite)
- **Contacts** — liste de tous les utilisateurs avec recherche
- **Profil** — photo de profil, pseudo modifiable, déconnexion
- **UI style Snapchat** — jaune `#FFFC00`, Material 3, logo fantôme, navigation basse (Chat / Camera / Stories / Me)

## Stack technique

| | |
|---|---|
| Langage | Kotlin 2.4 |
| Build | AGP 8.13.2, Gradle 8.14.4 |
| UI | Material 3, ViewBinding, RecyclerView |
| Caméra | CameraX 1.6 |
| Images | Coil 2.7 (aperçus), encodage JPEG/base64 maison |
| Backend | Firebase BOM 34 (Auth, Realtime Database) |
| SDK | minSdk 26, targetSdk 35, compileSdk 36 |

## Mise en route

### 1. Cloner

```bash
git clone <url-de-ce-repo>.git
cd L-Chat
```

### 2. Configurer Firebase

1. Créez un projet sur [console.firebase.google.com](https://console.firebase.google.com/)
2. Ajoutez une application Android avec le package **`com.lchat.app`**
3. Activez dans la console :
   - **Authentication** → méthode *Email/Mot de passe*
   - **Realtime Database**
4. Téléchargez votre `google-services.json` et **remplacez-en** le fichier `app/google-services.json`
5. Collez les règles de sécurité depuis `database.rules.json`
   (ou `firebase deploy --only database` avec le CLI Firebase)

> **Pas de Firebase Storage** : les images sont compressées (JPEG 720 px) et stockées en base64
> dans la Realtime Database — gratuit, et ça évite le plan Blaze (carte bancaire) que Storage
> impose désormais aux nouveaux projets.

> Les règles fournies exigent juste d'être connecté (`auth != null`). C'est volontaire pour la
> démo — pour un usage réel, restreignez l'écriture au nœud de l'utilisateur connecté.

### 3. Compiler

**Android Studio** : ouvrir le projet, laisser Gradle synchroniser, puis Run ▶.

**En CLI** (nécessite un JDK 17 ou 21 sur `JAVA_HOME`) :

```bash
./gradlew :app:assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

## Structure des données (Realtime Database)

```
users/
  {uid}/
    username, email, photoData, createdAt
    snaps/            # snaps REÇUS (supprimés après visionnage)
      {pushId}: fromUid, fromName, imageData, caption, createdAt
    conversations/
      {otherUid}: name, lastMessage, lastMessageAt, unread

conversations/
  {uidA_uidB}/        # clé = uid triés, séparés par _
    messages/
      {pushId}: fromUid, text, createdAt

stories/
  {pushId}: fromUid, fromName, imageData, caption, createdAt   # 24h (filtre + nettoyage client)

imageData / photoData : base64 d'une image JPEG compressée (720 px, qualité 65 ;
photo de profil 512 px, qualité 80). Limite du plan gratuit : 100 Mo.
```

## Structure du projet

```
app/src/main/java/com/lchat/app/
 ├── auth/        # écran de connexion / inscription
 ├── ui/          # fragments : Camera, Chat, Stories, Profile
 ├── snap/        # création, envoi et visionnage de snaps
 ├── chat/        # conversation 1:1
 ├── story/       # visionneuse de stories
 ├── adapter/     # RecyclerView adapters
 ├── model/       # data classes
 └── util/        # formatage temps, couleurs d'avatar...
```

## Crédits

Le concept et le code de départ proviennent du projet
[Snap-chat-clone](https://github.com/Developer668/Snap-chat-clone) de
[Developer668](https://github.com/Developer668) (licence MIT) — merci pour la base de départ.
Ce dépôt est une refonte complète : le code, l'architecture et l'UI ont été réécrits.

## Licence

MIT

## Disclaimer

Projet pédagogique, non affilié à Snap Inc. ni à Snapchat.
