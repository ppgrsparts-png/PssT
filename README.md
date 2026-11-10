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
- Activez **Bluetooth** et **Localisation**, acceptez les autorisations (et les notifications).
- Les personnes proches apparaissent dans l'onglet *Autour*.
- Touchez **Pssst** : l'autre reçoit la demande et peut **Accepter** ou **Refuser**.
- Après acceptation : profil complet dans *Amis* (appeler, WhatsApp, ouvrir les réseaux).
- Onglet *Messages* : discussions privées et **groupes** (bouton « Nouveau groupe », « Quitter »).

## Nouveautés de la version 1.1
- **Fonctionne application fermée** : une notification « Pssst est actif » indique que vous restez
  visible (bouton « Mettre en pause » dans la notification, ou interrupteur *Visible*).
- **Notifications** pour les pssst reçus, les nouveaux amis, les nouveaux groupes et les messages.
- **Chiffrement de bout en bout** (ECDH P-256 + AES-256-GCM) des messages privés et de groupe :
  les téléphones relais ne peuvent plus les lire. Un 🔒 l'indique.
- **Messages en attente conservés** même après redémarrage, et renvoyés automatiquement dès que
  l'ami revient à portée (pendant 7 jours).
- **Accusés de réception** : ⏳ en attente · ✓ envoyé · ✓✓ reçu.
- Les messages de groupe manqués (3 derniers jours) sont rattrapés quand on croise un membre.
- Confirmation avant de retirer un ami, quitter un groupe ou tout effacer.

## Limites
- La distance exacte n'est pas affichée (Nearby ne la fournit pas) : « À proximité ».
- Portée : de quelques mètres à quelques dizaines de mètres selon les téléphones et les murs.
- Certains fabricants (Xiaomi, Oppo, Huawei…) arrêtent les applis en arrière-plan : si Pssst
  s'arrête, désactivez l'« optimisation de la batterie » pour Pssst dans les réglages.
- Les amis ajoutés avec la version 1.0 passent au chiffrement dès que les deux téléphones ont la 1.1
  et se sont croisés une fois.
- Nécessite les services Google Play (absents sur les Huawei récents).
