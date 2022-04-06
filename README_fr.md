[![Build](https://github.com/kairoh/studio/actions/workflows/maven.yml/badge.svg)](https://github.com/kairoh/studio/actions/workflows/maven.yml)
[![Release](https://img.shields.io/github/v/release/kairoh/studio)](https://github.com/kairoh/studio/releases/latest)
<!-- [![Gitter](https://badges.gitter.im/STUdio-Story-Teller-Unleashed/general.svg)](https://gitter.im/STUdio-Story-Teller-Unleashed/general?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge) -->

[![Quality Scale](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=kairoh_studio)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=reliability_rating)](https://sonarcloud.io/dashboard?id=kairoh_studio)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=security_rating)](https://sonarcloud.io/dashboard?id=kairoh_studio)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=coverage)](https://sonarcloud.io/dashboard?id=kairoh_studio)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=kairoh_studio)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=bugs)](https://sonarcloud.io/dashboard?id=kairoh_studio)

STUdio - Story Teller Unleashed
===============================

[Instructions in english](README.md)

Créez et transférez vos propres packs d'histoires de et vers la Fabrique à Histoires Lunii\*.


PRÉAMBULE
---------

Ce logiciel s'appuie sur mes propres recherches de rétro ingénierie, limitées à la collecte des informations nécessaires
à l'interopérabilité avec la Fabrique à Histoires Lunii\*, et ne distribue aucun contenu protégé.

**EN UTILISANT CE LOGICIEL, VOUS EN ASSUMEZ LE RISQUE**. Malgré mes efforts pour que l'utilisation de ce logiciel soit
sûre, il est distribué **SANS AUCUNE GARANTIE** et pourrait endommager votre appareil.

\* Lunii et "ma fabrique à histoires" sont des marques enregistrées de Lunii SAS. Je ne suis (et ce travail n'est) en aucun cas affilié à Lunii SAS.


UTILISATION
-----------

### Prérequis

* Java JRE 11+
* Sur Windows, cette application nécessite que le pilote _libusb_ soit installé. Le moyen le plus simple pour cela est
  d'installer le logiciel officiel Luniistore\* (mais il ne doit pas être exécuté en même temps que STUdio).

### Installation

* **Téléchargez** [la dernière release](https://github.com/marian-m12l/studio/releases/latest) (ou
[construisez l'application](#pour-les-développeurs)).
* **Décompressez** l'archive de distribution
* **Exécutez le script de lancement** : `studio-linux.sh`, `studio-macos.sh` ou `studio-windows.bat` selon votre
plate-forme. Vous devrez probablement rendre ce fichier exécutable d'abord.
* S'il ne s'ouvre pas automatiquement, **ouvrez un navigateur** et saisissez l'url `http://localhost:8080` pour charger
l'interface web.

Note: Évitez d'exécuter le script en tant que superutilisateur/administrateur, ce qui pourrait créer des problèmes de permissions.

### Configuration

L'ordre de configuration est le suivant 
1. (si présente) variable système Java (ex: `-Dstudio.port=8081` )
2. (si présente) variable d'environnement (ex: `STUDIO_PORT=8081` )
3. valeur par défaut (dans le code)

| Variable d'environnement | Variable système Java | Défaut | Description |
| ------------------------ | -------------------- | ------ | ----------- |
| `STUDIO_HOST`          | `studio.host`          | `localhost`                    | Adresse d'écoute HTTP |
| `STUDIO_PORT`          | `studio.port`          | `8080`                         | Port d'écoute HTTP |
| `STUDIO_DB_OFFICIAL`   | `studio.db.official`   | `~/.studio/db/official.json`   | Fichier BDD json officiel  |
| `STUDIO_DB_UNOFFICIAL` | `studio.db.unofficial` | `~/.studio/db/unofficial.json` | Fichier BDD json non-officiel |
| `STUDIO_LIBRARY`       | `studio.library`       | `~/.studio/library/`           | Répertoire de la bibliothèque |
| `STUDIO_TMPDIR`        | `studio.tmpdir`        | `~/.studio/tmp/`               | Répertoire temporaire |
| `STUDIO_OPEN_BROWSER`  | `studio.open.browser`  | `true`                         | Ouverture auto du navigateur |
| `STUDIO_DEV_MODE`      | `studio.dev.mode`      | `prod`                         | Si `dev`, active le mode bouchon |
| `STUDIO_MOCK_DEVICE`   | `studio.mock.device`   | `~/.studio/device/`            | Répertoire de l'appareil bouchon |

Ex pour ne pas lancer de navigateur (via la variable d'environnement) et écouter sur le port 8081 (via la variable systeme) :
- sous Windows
```
set STUDIO_OPEN_BROWSER=false

java -Dstudio.port=8081 \
 -Dfile.encoding=UTF-8 -Dvertx.disableDnsResolver=true \
 -cp $STUDIO_PATH/${project.build.finalName}.jar:$STUDIO_PATH/lib/*:. \
 io.vertx.core.Launcher run ${vertx.main.verticle}
```

- sous Linux / MacOS
```
export STUDIO_OPEN_BROWSER=false

java -Dstudio.port=8081 \
 -Dfile.encoding=UTF-8 -Dvertx.disableDnsResolver=true \
 -cp $STUDIO_PATH/${project.build.finalName}.jar:$STUDIO_PATH/lib/*:. \
 io.vertx.core.Launcher run ${vertx.main.verticle}` |
```

### Utiliser l'application

L'interface web est composée de deux écrans :

* La bibliothèque d'histoires, qui permet de gérer la bibliothèque locale et de transférer de / vers la Fabrique à Histoire\* 
* L'éditeur d'histoire, pour créer ou modifier un pack d'histoire

#### Bibliothèque locale d'histoires et transfert de/vers l'appareil

L'écran de la bibliothèque d'histoires affiche toujours votre bibliothèque locale. Il s'agit des packs d'histoires situés
sur votre ordinateur (dans un répertoire `.studio` spécifique à chaque utilisateur). **Trois formats de fichier** peuvent
être présents dans votre bibliothèque :
* `Brut` est le format officiel supporté par les **appareils plus anciens** (firmware v1.x -- ces appareils utilisent un protocole USB bas-niveau)
* `FS` est le format officiel supporté par les **nouveaux appareils** (firmware v2.x -- ces appareils apparaîssent comme un stockage amovible)
* `Archive` est un format officieux, utilisé uniquement par STUdio dans l'**éditeur** d'histoires

La **conversion** d'un pack d'histoires est automatique lors d'un transfert, ou peut être invoquée manuellement.
Les variantes d'un pack d'histoires donné sont regroupées dans l'interface pour une meilleure lisibilité. **Le fichier
le plus récent** (mis en avant par l'interface) est transféré vers l'appareil.

Quand l'appareil est branché, un panneau apparaît sur la gauche, affichant les métadonnées et les packs d'histoires de
l'appareil. Glisser et déposer un pack depuis ou vers l'appareil commencera le transfert.

#### Éditeur d'histoire

L'écran de l'éditeur d'histoire affiche l'histoire en cours de modification. Par défaut, un exemple est affiché, dont le but est de proposer un modèle d'utilisation correcte.

Un pack est composé de quelques métadonnées et du diagramme décrivant les différentes étapes de l'histoire :

* Les nœuds de scène permettent d'afficher une image et/ou de jouer un son
* Les nœuds d'action permettent de passer d'une scène à la suivante, et de gérer les options disponibles

L'éditeur supporte plusieurs formats de fichiers pour l'audio et les images.

##### Images

Les fichiers image peuvent utiliser les formats suivants (les formats marqués d'astérisques sont automatiquement
convertis lors du transfert vers l'appareil) :
* PNG
* JPEG
* BMP (24-bits)

**Les dimensions doivent être 320x240**. Les images peuvent être en couleurs, bien que certaines couleurs ne seront
certainement pas affichées fidèlement par l'écran situé derrière le boîtier en plastique. Gardez à l'esprit que la
couleur du boîtier peut changer.

##### Audio

Les fichiers audio peuvent utiliser les formats suivants (les formats marqués d'astérisques sont automatiquement
convertis lors du transfert vers l'appareil) :
* MP3
* OGG/Vorbis 
* WAVE (16-bits signés, mono, 32000 Hz)

Les fichiers MP3 et OGG doivent, eux, être échantillonnés à 44100Hz.

#### Wiki

Pour plus d'informations, y compris un guide d'utilisation illustré (merci à
[@appenzellois](https://github.com/appenzellois)), consultez
[le wiki du projet](https://github.com/marian-m12l/studio/wiki/Documentation).


POUR LES DÉVELOPPEURS
---------------------

### Prérequis

* Java JDK 11+
* Maven 3+

### Building the application

* Cloner ce dépôt : `git clone https://github.com/marian-m12l/studio.git`
* Construire l'application : `mvn clean install`

Ceci créera **l'archive de distribution** dans `web-ui/target/`.


APPLICATIONS TIERCES
--------------------

Si vous avez aimé STUdio, vous aimerez aussi :
* [mhios (Mes Histoires Interactives Open Stories)](https://www.mhios.com) est une bibliothèque ouverte, en ligne,
d'histoires interactives (développé par [@sebbelese](https://github.com/sebbelese))
* [Moiki](https://moiki.fr/) est un outil en ligne de création d'histoires interactives, qui peuvent être exportées
vers STUdio (développé par [@kaelhem](https://github.com/kaelhem))


LICENCE
-------

Ce projet est distribué sous la licence **Mozilla Public License 2.0**. Les termes de la licence sont dans le
fichier `LICENSE`.

La bibliothèque `vorbis-java`, ainsi que la classe `VorbisEncoder` sont distribuées par Xiph.org Foundation. Les termes
de la licence se trouvent dans le fichier `LICENSE.vorbis-java`.

Le package `com.jhlabs.image` est distribué par Jerry Huxtable sous la licence Apache License 2.0. Les termes
de la licence se trouvent dans le fichier `LICENSE.jhlabs`.
