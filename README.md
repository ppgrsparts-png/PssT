# Pssst – application Android (APK)

Voir les personnes autour de vous qui ont Pssst, leur envoyer un « pssst »,
échanger numéro et réseaux sociaux après acceptation, discuter en privé
et en groupe, **sans Internet** (Bluetooth + Wi-Fi direct via Google Nearby Connections).

## Obtenir l'APK sans rien installer (GitHub Actions)
1. Sur GitHub, ouvrez votre dépôt, onglet **Code** > **Add file** > **Upload files**.
2. Glissez le **contenu** du dossier `pssst-android` (le dossier `app`, et les fichiers
   `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `README.md`), puis **Commit changes**.
3. Onglet **Actions** > **set up a workflow yourself**, nommez le fichier `build-apk.yml`,
   collez le contenu de `.github/workflows/build-apk.yml`, puis **Commit changes**.
4. La compilation démarre (environ 5 à 8 minutes). Ouvrez-la, puis en bas de la page,
   section **Artifacts**, téléchargez **pssst-apk** (un zip qui contient `app-debug.apk`).
5. Envoyez l'APK sur votre téléphone et installez-le (autorisez « sources inconnues »).

## Ou avec Android Studio
Ouvrez le dossier, attendez la synchronisation, puis **Build > Build APK(s)**.

## Utilisation entre amis
- Chaque ami installe l'APK, crée son profil (photo, prénom, bio, numéro, réseaux).
- Activez **Bluetooth** et **Localisation**, acceptez les autorisations.
- Laissez l'application **ouverte** : les personnes proches apparaissent dans l'onglet *Autour*.
- Touchez **Pssst** : l'autre reçoit la demande et peut **Accepter** ou **Refuser**.
- Après acceptation : profil complet dans *Amis* (appeler, WhatsApp, ouvrir les réseaux).
- Onglet *Messages* : discussions privées et **groupes** (bouton « Nouveau groupe »).

## Limites de cette première version
- L'application doit être ouverte à l'écran pour être visible.
- La distance exacte n'est pas affichée (Nearby ne la fournit pas) : « À proximité ».
- Portée : de quelques mètres à quelques dizaines de mètres selon les téléphones et les murs.
- Les messages relayés passent par les téléphones Pssst proches ; ils ne sont pas encore
  chiffrés de bout en bout dans cette version.
- Nécessite les services Google Play (absents sur les Huawei récents).
