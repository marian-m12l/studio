STUdio - Story Teller Unleashed
===============================

[This README in english](README.md)

Un ensemble d'outils pour lire, créer et transférer des packs d'histoires de et vers la Fabrique à Histoires Lunii\*.


PRÉAMBULE
---------

Ce logiciel s'appuie sur mes propres recherches de rétro ingénierie, limitées à la collecte des informations nécessaires à l'intéropérabilité avec la Fabrique à Histoires Lunii\*, et ne distribue aucun contenu protégé.

Ce logiciel est encore à un stade précoce de développement, et n'a donc pas encore été testé minutieusement. En particulier, il n'a été utilisé que sur un nombre très restreint d'appareils, et pourrait endommager votre appareil. EN UTILISANT CE LOGICIEL, VOUS EN ASSUMEZ LE RISQUE.

\* Lunii et "ma fabrique à histoires" sont des marques enregistrées de Lunii SAS. Je ne suis (et ce travail n'est) en aucun cas affilié à Lunii SAS.


UTILISATION
-----------

### Prérequis

Pour exécuter l'application :
* JRE 10+

Pour construire l'application :
* Git
* JDK 10+
* Maven 3+

#### Logiciel Luniistore\*

Cette application nécessite certaines ressources du logiciel officiel Luniistore\*.

* Téléchargez-le et installez-le
* Les ressources nécessaires sont dans un répertoire de l'utilisateur, appelé `$LOCAL_LUNIITHEQUE` dans la suite de ce document. Son chemin dépend de votre plate-forme :
  * Sur Linux, il se trouve au chemin `~/.local/share/Luniitheque`
  * Sur Windows, il se trouve au chemin `%UserProfile%\AppData\Roaming\Luniitheque`
* Les ressources doivent être copiées dans un répertoire nouvellement créé de l'utilisateur (appelé `$DOT_STUDIO` par la suite) afin d'être lues par l'application. Le chemin attendu dépend de votre plate-forme :
  * Sur Linux, `~/.studio`
  * Sur Windows, `%UserProfile%\.studio`

#### Pilote de la Fabrique à Histoire\*

Le transfert de packs d'histoires de et vers la Fabrique à Histoire\* est géré par le pilote Lunii\* officiel. Ce pilote
est distribué avec le logiciel Luniistore\*, et doit y être récupéré:

* Télécharger et installer le logiciel Luniistore\*
* Créer les répertoires `$DOT_STUDIO/lib/` dans votre dossier personnel (e.g. `mkdir -p ~/.studio/lib` sur Linux, `mkdir %UserProfile%\.studio` sur Windows)
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
* Appelez `https://lunii-data-prod.firebaseio.com/packs.json?auth=TOKEN` et enregistrez le résultat dans `$DOT_STUDIO/db/official.json` (e.g. `curl -v -X GET https://lunii-data-prod.firebaseio.com/packs.json?auth=TOKEN > ~/.studio/db/official.json`)

### Construire l'application

Après avoir cloné ce dépôt de sources, exécuter `mvn clean install` pour construire l'application. Ceci créera l'archive de distribution dans `web-ui/target/`.

### Démarrer l'application

Vous devez d'abord contruire l'application ou télécharger une archive de distribution.

Pour démarrer l'application :
* Décompressez l'archive de distribution
* Exécutez le script de lancement : `studio.sh` ou `studio.bat` selon votre plate-forme. Vous devrez probablement rendre ce fichier exécutable d'abord.
Si la commande est exécutée dans un terminale, des logs devraient s'afficher, en se terminant par `INFOS: Succeeded in deploying verticle`.
* Ouvrez un navigateur et saisissez l'url `http://localhost:8080` pour charger l'interface web.

### Utiliser l'application

L'interface web est composée de deux écrans :

* La bibliothèque d'histoires, qui permet de gérer la bibliothèque locale et de transférer de / vers la Fabrique à Histoire\* 
* L'éditeur d'histoire, opur pour créer ou modifier un pack d'histoire

#### Bibliothèque d'histoires

L'écran de la bibliothèque d'histoires affiche toujours votre bibliothèque locale. Il s'agit des packs d'histoires situés dnas le répertoire `$DOT_STUDIO/library`. Ces packs peuvent être au format binaire (le format officiel, supporté par l'appareil) ou au format archive (le format officieux, utilisé pour la création et la modification de packs d'histoires).

Dans l'appareil est branché, un panneau apparaît sur la gauche, affichant les métadaonnées et les packs d'histoires de l'appareil. Glisser et déposer un pack depuis ou vers l'appareil commencera le transfert.

#### Éditeur d'histoire

L'écran de l'éditeur d'histoire affiche l'histoire en cours de modification. Par défaut, un exemple est affiché, dont le but est de proposer un modèle d'utilisation correcte.

Un pack est composé de quelques métadonnées et du diagramme décrivant les différentes étapes de l'histoire :

* Les nœuds de scène permettent d'afficher une image et/ou de jouer un son
* Les nœuds d'action permettent de passer d'une scène à la suivante, et de gérer les options disponibles


MÉMOIRE DE LA FABRIQUE À HISTOIRES\*
------------------------------------

La Fabrique à Histoires\* expose deux mémoires de stockage : la carte SD et une mémoire accessible par SPI (probablement une mémoire flash ?).

Ces deux espaces de stockage sont divisés en secteurs de 512 octets. Les données sont désignées par leur numéro de secteur.

### Carte SD

| Secteurs       | Contenu                                            |
|----------------|----------------------------------------------------|
| 1              | UUID                                               |
| 2              | ???                                                |
| 3              | Erreur, version firmware et taille de la carte SD  |
| 4 - 454        | Image Logo                                         |
| 455 - 905      | Image USB                                          |
| 906 - 1356     | Image batterie faible                              |
| 1357 - 1807    | Image Erreur                                       |
| 1808 - 74999   | ???                                                |
| 75000 - 99999  | Statistiques des packs (quoi que cela signifie ?)  |
| 100000         | Catalogue des packs d'histoires                    |
| 100001 - FIN   | Packs d'histoires                                  |

# SPI

Pas encore analysée.


FORMAT DE FICHIER PACK D'HISTOIRES
----------------------------------

Un pack d'histoires est composé de nœuds. Il y a deux types de nœuds :
* Les nœuds de scène (ou nœuds d'étape, "stage node") affichent une image et jouent un son, les deux étant optionels. En plus de ces ressources, ces nœudd définissent :
  * L'action/transition exécutée lorsque le bouton OK est appuyé
  * L'action/transition exécutée lorsque le bouton MAISON est appuyé
  * Quels contrôles sont disponibles
* Les nœuds d'action sont utilisés comme transition d'une scène à la suivante. Les nœuds d'action peuvent contenir :
  * Une unique scène qui est automatiquement jouée, ou
  * Plusieurs options parmi lesquelles l'utilisateur peut faire un choix, en naviguant entre les options/scènes avec la molette. Chaque fois que l'utilisateur tourne la molette, l'option/scène précédente ou suivante est jouée.


Un pack d'histoires est également divisé en secteurs de 512 octets. Les secteurs incomplets sont complétés par des zéros.

Exemple de structure d'un pack avec 4 nœuds de scène et 2 nœuds d'action :

| Secteurs       | Contenu                |
|----------------|------------------------|
| 1              | Metadonnées            |
| 2 - 5          | Nœuds de scène         |
| 6 - 7          | Nœuds d'action         |
| 8 - 458        | Ressource image 1      |
| 459 - 909      | Ressource image 2      |
| 910 - 9999     | Ressource audio 1      |
| 10000 - 19999  | Ressource audio 2      |
| 20000 - 29999  | Ressource audio 3      |
| 30000 - 39999  | Ressource audio 4      |
| 40000          | Octets de vérification |

### Secteur métadonnées

| Octets         | Donnée                    | Type   | Valeur        |
|----------------|---------------------------|--------|---------------|
| 1 - 2          | Nombre de nœuds de scène  | short  |               |
| 3              | Désactivé                 | byte   | O or 1        |
| 4 - 5          | Version                   | short  | 1 par défaut  |

### Sector nœud de scène

| Octets         | Donnée                           | Type     | Valeur                                            |
|----------------|----------------------------------|----------|---------------------------------------------------|
| 1 - 16         | UUID                             | long * 2 | Bits de poids fort en premier                     |
| 17 - 20        | Secteur de début d'image         | int      | Offset par rapport au premier nœud de scène ou -1 |
| 21 - 24        | Taille de l'image                | int      | Nombre de secteurs                                |
| 25 - 28        | Secteur de début de son          | int      | Offset par rapport au premier nœud de scène ou -1 |
| 29 - 32        | Taille du son                    | int      | Nombre de secteurs                                |
| 33 - 34        | Action si OK appuyé              | short    | Offset par rapport au premier nœud de scène ou -1 |
| 35 - 36        | Options dans la transition       | short    | Nombre d'options disponibles ou -1                |
| 37 - 38        | Option choisie                   | short    | Indice de l'option sélectionnée ou -1             |
| 39 - 40        | Action si MAISON appuyé          | short    | Offset par rapport au premier nœud de scène ou -1 |
| 41 - 42        | Options dans la transition       | short    | Nombre d'options disponibles ou -1                |
| 43 - 44        | Option choisie                   | short    | Indice de l'option sélectionnée ou -1             |
| 45 - 46        | Molette autorisée                | short    | 0 or 1                                            |
| 47 - 48        | OK autorisé                      | short    | 0 or 1                                            |
| 49 - 50        | MAISON autorisé                  | short    | 0 or 1                                            |
| 51 - 52        | PAUSE autorisé                   | short    | 0 or 1                                            |
| 53 - 54        | Avancement auto à la fin du son  | short    | 0 or 1                                            |

Les transitions liées aux boutons OK et MAISON dirigent généralement vers une options donnée dans la liste des options du prochain nœud d'action.
Cependant, des cas particuliers existent :
* Si l'action est définie mais l'indice choisi est `-1`, une option est sélectionnée **aléatoirement** dans la liste.
* Si le bouton MAISON est activé mais **aucune action** n'est définie (i.e. tous les champs de transition à `-1`), l'utilisateur est renvoyé au nœuds de scène principal (choix du pack d'histoires).

### Secteur nœud d'action

Un nœud d'action est simplement une liste d'options disponibles. Chaque option est définie par un champ de type short, représentant l'offset (par rapport au premier nœud de scène) du nœud de scène.

### Secteur ressource image

Les ressources d'image sont des fichiers Windows BMP, 24-bits, de 320x240. Les images peuvent être en couleurs, bien que
certains couleurs ne seront certainement pas affichées fidèlement par l'écran situé derrière le boîtier en plastique.
Si le dernier secteur est incomplet, il est complété par des zéros.

### Secteur ressource audio

Les ressources audio sont des fichiers WAVE, 16-bits signés, 32 000 Hz. Si le dernier secteur est incomplet, il est complété par des zéros.

### Secteur octets de vérification

Le dernier secteur d'un fichier de pack d'histoires doit contenir une séquence prédéfinie de 512 octets.


LICENCE
-------

Ce projet est distribué sous la licence Mozilla Public License 2.0.