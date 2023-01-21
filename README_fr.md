[![Release](https://img.shields.io/github/v/release/kairoh/studio)](https://github.com/kairoh/studio/releases/latest)
[![Sonar Build](https://github.com/kairoh/studio/actions/workflows/sonarcloud.yml/badge.svg)](https://github.com/kairoh/studio/actions/workflows/sonarcloud.yml)
[![Native Build](https://github.com/kairoh/studio/actions/workflows/native.yml/badge.svg)](https://github.com/kairoh/studio/actions/workflows/native.yml)

[![Quality Scale](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=kairoh_studio)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=reliability_rating)](https://sonarcloud.io/dashboard?id=kairoh_studio)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=security_rating)](https://sonarcloud.io/dashboard?id=kairoh_studio)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=coverage)](https://sonarcloud.io/dashboard?id=kairoh_studio)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=kairoh_studio)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=kairoh_studio&metric=bugs)](https://sonarcloud.io/dashboard?id=kairoh_studio)

STUdio - Story Teller Unleashed
===============================

[Instructions in english](README.md)

Créez et transférez vos propres packs d'histoires de et vers la Fabrique à Histoires Lunii[^1].

PRÉAMBULE
---------

Ce logiciel s'appuie sur des recherches de rétro-ingénierie, limitées à la collecte des informations nécessaires à l'interopérabilité avec la Fabrique à Histoires Lunii[^1], et ne distribue aucun contenu protégé.

**EN UTILISANT CE LOGICIEL, VOUS EN ASSUMEZ LE RISQUE**. Malgré les efforts pour que l'utilisation de ce logiciel soit sûre, il est distribué **SANS AUCUNE GARANTIE** et pourrait endommager votre appareil.

[^1]: Lunii, Luniistore et "ma fabrique à histoires" sont des marques enregistrées de Lunii SAS. Ce travail n'est en aucun cas affilié à Lunii SAS.

UTILISATION
-----------

### Choisir son édition

:rocket: L'édition "Native" est plus rapide et n'a pas besoin d'avoir Java installé.
Peu d'architecture sont disponibles (dépend des hôtes Github), mais vous pouvez [compiler la vôtre](#compiler-l%C3%A9dition-native).

:hotsprings: L'édition "Java" a besoin d'avoir Java 11+ installé. 
Elle supporte presque toutes les architectures (selon la compatibilité de [usb4java](http://usb4java.org]) ).

| CPU/OS  | Linux        | Windows            | MacOS              | 
| :---:   | :---:        | :---:              | :---:              |
| x86_64, amd64  | :hotsprings: :rocket: | :hotsprings: :rocket: | :hotsprings: :rocket: |
| x86, i386      | :hotsprings: | :hotsprings:       |                    |
| armhf, armv7l, aarch32 | :hotsprings: |                    |                    |
| arm64, aarch64 | :hotsprings: |                    | :hotsprings:       |

De nos jours la plupart des PC et Mac sont compatibles amd64, sauf les Apple M1, M2, M3 qui sont arm64. 

### Prérequis

* (Edition Java uniquement) Java JRE 11+
* Sur Windows, cette application nécessite que le pilote _libusb_ soit installé. Le moyen le plus simple pour cela est d'installer le logiciel officiel Luniistore\* (mais il ne doit pas être exécuté en même temps que STUdio).

### Installation

* **Téléchargez** [la dernière release](https://github.com/kairoh/studio/releases/latest) (ou [construisez l'application](#pour-les-développeurs)).
* **Décompressez** l'archive de distribution
* **Exécutez le script de lancement** : `studio.sh` ou `studio.bat` selon votre plate-forme. Vous devrez probablement rendre ce fichier exécutable d'abord.
* S'il ne s'ouvre pas automatiquement, **ouvrez un navigateur** et saisissez l'url `http://localhost:8080` pour charger l'interface web.

Note: Évitez d'exécuter le script en tant que superutilisateur/administrateur, ce qui pourrait créer des problèmes de permissions.

### Configuration

Studio est portable par défaut: tout (si ce n'est la JRE pour l'édition Java) est dans le répertoire courant.

<details>
  <summary>Customization</summary>

Plusieurs personnalisations sont possibles.

L'ordre de configuration est le suivant 
1. (si présente) variable système Java (ex: `-Dstudio.port=8081`)
2. (si présente) variable d'environnement (ex: `STUDIO_PORT=8081`)
3. valeur par défaut (dans le code)

| Variable d'environnement | Variable système Java | Défaut | Description |
| ------------------------ | -------------------- | ------ | ----------- |
| `STUDIO_HOME`          | `studio.home`          | `.` (=current dir)             | Répertoire de Studio    |
| `STUDIO_HOST`          | `studio.host`          | `localhost`                    | Adresse d'écoute HTTP |
| `STUDIO_PORT`          | `studio.port`          | `8080`                         | Port d'écoute HTTP |
| `STUDIO_DB_OFFICIAL`   | `studio.db.official`   | `~/.studio/db/official.json`   | Fichier BDD json officiel  |
| `STUDIO_DB_UNOFFICIAL` | `studio.db.unofficial` | `~/.studio/db/unofficial.json` | Fichier BDD json non-officiel |
| `STUDIO_LIBRARY`       | `studio.library`       | `~/.studio/library/`           | Répertoire de la bibliothèque |
| `STUDIO_TMPDIR`        | `studio.tmpdir`        | `~/.studio/tmp/`               | Répertoire temporaire |
| `STUDIO_OPEN_BROWSER`  | `studio.open.browser`  | `true`                         | Ouverture auto du navigateur |
| `STUDIO_MOCK_DEVICE`   | `studio.mock.device`   | `~/.studio/device/`            | Répertoire de l'appareil bouchon |

Ex pour écouter sur le port 8081 (via la variable systeme) :
- sous Windows: `studio.bat -Dstudio.port=8081`
- sous Linux / MacOS: `./studio.sh -Dstudio.port=8081`

</details>

### Utiliser l'application

L'interface web est composée de 2 écrans:

* La bibliothèque d'histoires, qui permet de gérer la bibliothèque locale et de transférer de / vers la Fabrique à Histoire[^1]
* L'éditeur d'histoire, pour créer ou modifier un pack d'histoire

#### Bibliothèque locale d'histoires et transfert de/vers l'appareil

L'écran de la bibliothèque d'histoires affiche toujours votre bibliothèque locale. Il s'agit des packs d'histoires situés sur votre ordinateur (dans le répertoire `library` de studio). 

**3 formats de fichier** peuvent être présents dans votre bibliothèque :
* `Brut` est le format officiel supporté par les **appareils plus anciens** (firmware v1.x -- ces appareils utilisent un protocole USB bas-niveau)
* `FS` est le format officiel supporté par les **nouveaux appareils** (firmware v2.x -- ces appareils apparaîssent comme un stockage amovible)
* `Archive` est un format officieux, utilisé uniquement par STUdio dans l'**éditeur** d'histoires

La **conversion** d'un pack d'histoires est automatique lors d'un transfert, ou peut être invoquée manuellement.
Les variantes d'un pack d'histoires donné sont regroupées dans l'interface pour une meilleure lisibilité. 
**Le fichier le plus récent** (mis en avant par l'interface) est transféré vers l'appareil.

Quand l'appareil est branché, **un panneau apparaît sur la gauche**, affichant les métadonnées et les packs d'histoires de l'appareil. **Glisser et déposer** un pack depuis ou vers l'appareil commencera le transfert.

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
certainement pas affichées fidèlement par l'écran situé derrière le boîtier en plastique. Gardez à l'esprit que le boîtier peut altérer la couleur des images.

##### Audio

Les fichiers audio peuvent utiliser les formats suivants (les formats marqués d'astérisques sont automatiquement
convertis lors du transfert vers l'appareil) :
* MP3
* OGG/Vorbis 
* WAVE (16-bits signés, mono, 32000 Hz)

Les fichiers MP3 et OGG doivent, eux, être échantillonnés à 44100Hz.

#### Wiki

Pour plus d'informations, y compris un guide d'utilisation illustré (merci à [@appenzellois](https://github.com/appenzellois)), consultez [le wiki du projet](https://github.com/marian-m12l/studio/wiki/Documentation).

POUR LES DÉVELOPPEURS
---------------------

### Compiler avec gitops

* Forker ce dépot Github
* Aller dans votre onglet "Actions"
* Vérifier les artifacts générés par le workflow [native.yml](.github/workflows/native.yml)

### Compiler l'édition "Java"

#### Prérequis

* Maven 3+
* Java JDK 11+

#### Compiler localement

* Cloner ce dépot Github
* Compiler l'application: `mvn install`
La **distribution** sera créée dans `web-ui/target/quarkus-app/`.

### Compiler l'édition "Native"

Voir le [guide Quarkus](https://quarkus.io/guides/building-native-image).

#### Prérequis

* Maven 3+
* Docker ou un GraalVM supportant AWT : [Mandrel 22.3+ pour Linux](https://github.com/graalvm/mandrel) ou [Liberica NIK 22.3+ pour Win et Mac](https://bell-sw.com/pages/downloads/native-image-kit/#/nik-22-17)

#### Compiler localement

* Cloner ce dépôt Github
* Compiler l'application: `mvn install -Pnative`

La **distribution** sera crée dans `web-ui/target/`.
Sous Windows, les DLL nécessaires seront dans un sous-répertoire `native-image`.

APPLICATIONS TIERCES
--------------------

Si vous avez aimé STUdio, vous aimerez aussi :
* ~~[mhios (Mes Histoires Interactives Open Stories)](https://www.mhios.com) est une bibliothèque ouverte, en ligne,
d'histoires interactives (développé par [@sebbelese](https://github.com/sebbelese))~~
* [Moiki](https://moiki.fr/) est un outil en ligne de création d'histoires interactives, qui peuvent être exportées
vers STUdio (développé par [@kaelhem](https://github.com/kaelhem))

LICENCE
-------

Ce projet est distribué sous la licence **Mozilla Public License 2.0**. Les termes de la licence sont dans le
fichier [LICENSE.md](LICENSE.md).

La bibliothèque `jvorbis`, ainsi que la classe `VorbisEncoder` sont distribuées par Xiph.org Foundation. Les termes
de la licence se trouvent dans le fichier [LICENSE-jvorbis.md](LICENSE-jvorbis.md).

Le package `com.jhlabs.image` est distribué par Jerry Huxtable sous la licence Apache License 2.0. Les termes
de la licence se trouvent dans le fichier [LICENSE-jhlabs.md](LICENSE-jhlabs.md).
