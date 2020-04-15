[![Release](https://img.shields.io/github/v/release/marian-m12l/studio)](https://github.com/marian-m12l/studio/releases/latest)
[![Gitter](https://badges.gitter.im/STUdio-Story-Teller-Unleashed/general.svg)](https://gitter.im/STUdio-Story-Teller-Unleashed/general?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

STUdio - Story Teller Unleashed
===============================

[This README in english](README.md)

Un ensemble d'outils pour lire, créer et transférer des packs d'histoires de et vers la Fabrique à Histoires Lunii\*.


PRÉAMBULE
---------

Ce logiciel s'appuie sur mes propres recherches de rétro ingénierie, limitées à la collecte des informations nécessaires à l'interopérabilité avec la Fabrique à Histoires Lunii\*, et ne distribue aucun contenu protégé.

Ce logiciel est encore à un stade précoce de développement, et n'a donc pas encore été testé minutieusement. En particulier, il n'a été utilisé que sur un nombre très restreint d'appareils, et pourrait endommager votre appareil. EN UTILISANT CE LOGICIEL, VOUS EN ASSUMEZ LE RISQUE.

\* Lunii et "ma fabrique à histoires" sont des marques enregistrées de Lunii SAS. Je ne suis (et ce travail n'est) en aucun cas affilié à Lunii SAS.


UTILISATION
-----------

### Prérequis

Pour exécuter l'application :
* JRE 11+

Pour construire l'application :
* Git
* JDK 11+
* Maven 3+

#### Logiciel Luniistore\*

Cette application nécessite certaines ressources du logiciel officiel Luniistore\*.

* Téléchargez-le et installez-le
* Les ressources nécessaires sont dans un répertoire de l'utilisateur, appelé `$LOCAL_LUNIITHEQUE` dans la suite de ce document. Son chemin dépend de votre plate-forme :
  * Sur Linux, il se trouve au chemin `~/.local/share/Luniitheque`
  * Sur macOS, il se trouve au chemin `~/Library/Application\ Support/Luniitheque`
  * Sur Windows, il se trouve au chemin `%UserProfile%\AppData\Roaming\Luniitheque`
* Les ressources doivent être copiées dans un répertoire nouvellement créé de l'utilisateur (appelé `$DOT_STUDIO` par la suite) afin d'être lues par l'application. Le chemin attendu dépend de votre plate-forme :
  * Sur Linux et macOS, `~/.studio`
  * Sur Windows, `%UserProfile%\.studio`

#### Pilote de la Fabrique à Histoire\*

Le transfert de packs d'histoires de et vers la Fabrique à Histoire\* est géré par le pilote Lunii\* officiel. Ce pilote
est distribué avec le logiciel Luniistore\*, et doit y être récupéré:

* Télécharger et installer le logiciel Luniistore\*
* Créer les répertoires `$DOT_STUDIO/lib/` dans votre dossier personnel (p. ex. `mkdir -p ~/.studio/lib` sur Linux ou macOS, `mkdir %UserProfile%\.studio` sur Windows)
* Depuis `$LOCAL_LUNIITHEQUE/lib`, copiez ces trois fichiers JAR vers `$DOT_STUDIO/lib/` :
  * `lunii-java-util.jar`
  * `lunii-device-gateway.jar`
  * `lunii-device-wrapper.jar`

#### Base de données officielle des métadonnées de packs d'histoires

Afin d'afficher les métadonnées des packs d'histoires, celles-ci doivent être téléchargées et stockées dans un fichier local.

* Lancer le logiciel Luniistore\* afin d'obtenir un jeton d'authentification (valable pendant une heure)
* Ouvrez `$LOCAL_LUNIITHEQUE/.local.properties` dans une éditeur de texte, et notez la valeur du jeton :
  * Si vous êtes connecté sur le logiciel Luniistore\*, le jeton se trouve dans la propriété `tokens`, attribut `tokens.access_tokens.data.firebase`
  * Si vous n'êtes pas connecté sur le logiciel Luniistore\*, le jeton se trouve dans la propriété `token`, attribut `firebase`
* Appelez `https://lunii-data-prod.firebaseio.com/packs.json?auth=TOKEN` et enregistrez le résultat dans `$DOT_STUDIO/db/official.json` (p. ex. `curl -v -X GET https://lunii-data-prod.firebaseio.com/packs.json?auth=TOKEN > ~/.studio/db/official.json`)

### Construire l'application

Après avoir cloné ce dépôt de sources, exécuter `mvn clean install` pour construire l'application. Ceci créera l'archive de distribution dans `web-ui/target/`.

### Démarrer l'application

Vous devez d'abord construire l'application ou télécharger une archive de distribution ([dans la dernière release](https://github.com/marian-m12l/studio/releases/latest)).

Pour démarrer l'application :
* Décompressez l'archive de distribution
* Exécutez le script de lancement : `studio-linux.sh`, `studio-macos.sh` ou `studio-windows.bat` selon votre plate-forme. Vous devrez probablement rendre ce fichier exécutable d'abord.
Si la commande est exécutée dans un terminale, des logs devraient s'afficher, en se terminant par `INFOS: Succeeded in deploying verticle`.
* Ouvrez un navigateur et saisissez l'url `http://localhost:8080` pour charger l'interface web.

Note: Évitez d'exécuter le script en tant que superutilisateur/administrateur, ce qui pourrait créer des problèmes de permissions.

### Utiliser l'application

L'interface web est composée de deux écrans :

* La bibliothèque d'histoires, qui permet de gérer la bibliothèque locale et de transférer de / vers la Fabrique à Histoire\* 
* L'éditeur d'histoire, pour créer ou modifier un pack d'histoire

#### Bibliothèque d'histoires

L'écran de la bibliothèque d'histoires affiche toujours votre bibliothèque locale. Il s'agit des packs d'histoires situés dans le répertoire `$DOT_STUDIO/library`. Ces packs peuvent être au format binaire (le format officiel, supporté par l'appareil) ou au format archive (le format officieux, utilisé pour la création et la modification de packs d'histoires).

Quand l'appareil est branché, un panneau apparaît sur la gauche, affichant les métadonnées et les packs d'histoires de l'appareil. Glisser et déposer un pack depuis ou vers l'appareil commencera le transfert.

#### Éditeur d'histoire

L'écran de l'éditeur d'histoire affiche l'histoire en cours de modification. Par défaut, un exemple est affiché, dont le but est de proposer un modèle d'utilisation correcte.

Un pack est composé de quelques métadonnées et du diagramme décrivant les différentes étapes de l'histoire :

* Les nœuds de scène permettent d'afficher une image et/ou de jouer un son
* Les nœuds d'action permettent de passer d'une scène à la suivante, et de gérer les options disponibles

L'éditeur supporte plusieurs formats de fichiers pour l'audio et les images.

##### Images

Les fichiers image peuvent utiliser les formats suivants (les formats marqués d'astérisques sont automatiquement
convertis lors du transfert vers l'appareil) :
* PNG\*\*
* JPEG\*\*
* BMP (24-bits)

Les dimensions doivent être 320x240. Les images peuvent être en couleurs, bien que certaines couleurs ne seront
certainement pas affichées fidèlement par l'écran situé derrière le boîtier en plastique. Gardez à l'esprit que la
couleur du boîtier peut changer, comme c'est le cas pour la récente édition de Noël.

##### Audio

Les fichiers audio peuvent utiliser les formats suivants (les formats marqués d'astérisques sont automatiquement
convertis lors du transfert vers l'appareil) :
* MP3\*\*
* OGG/Vorbis \*\*
* WAVE (16-bits signés, mono, 32000 Hz)

Les fichiers MP3 et OGG doivent, eux, être échantillonnés à 44100Hz.

### [Expérimental] Voir les métadonnées non-officielles dans le Luniistore\* / Charger la base de données officielle des métadonnées depuis le Luniistore\*

Ces fonctionnalités **expérimentales** permettent :
  * d'afficher des métadonnées (partiellement) correctes pour les packs d'histoires non-officiels (stockés dans l'appareil) dans l'application officielle Luniistore\*.
  * de charger / rafraîchir automatiquement la base de données officielle des métadonnées lorsque le Luniistore\* est exécuté.

Pour activer ces fonctionnalités, localisez le fichier de configuration `Luniistore.cfg` :
  * Sur Linux, dans le dossier `/opt/Luniistore/app`
  * Sur macOS, dans le dossier `/Applications/Luniistore.app/Contents/Java`
  * Sur Windows, dans le dossier `%ProgramFiles%\Luniistore\app`
  
Puis ajoutez la ligne suivante dans la section `[JVMOptions]` (remplacez `$DOT_STUDIO` par le chemin correspondant)
(utilisez des séparateurs "slash" même sur Windows) :

```
-javaagent:$DOT_STUDIO/agent/studio-agent.jar
```


LICENCE
-------

Ce projet est distribué sous la licence **Mozilla Public License 2.0**. Les termes de la licence sont dans le
fichier `LICENSE`.

La bibliothèque `vorbis-java`, ainsi que la classe `VorbisEncoder` sont distribuées par Xiph.org Foundation. Les termes
de la licence se trouvent dans le fichier `LICENSE.vorbis-java`.
