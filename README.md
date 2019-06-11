STUdio - Story Teller Unleashed
===============================

[Ce README en français](README_fr.md)

A set of tools to read, create and transfer story packs from and to the Lunii\* story teller device.


DISCLAIMER
----------

This software relies on my own reverse engineering research, which is limited to gathering the information necessary to ensure interoperability with the Lunii\* story teller device, and does not distribute any protected content.

This software is still in an early stage of development, and as such has not yet been thoroughly tested. In particular, it has only been used on a very small number of devices, and may brick your device. USE AT YOUR OWN RISK.

\* Lunii is a registered trademark of Lunii SAS. I am (and this work is) in no way affiliated with Lunii SAS.


USAGE
-----

### Prerequisite

To run the application:
* JRE 10+

To build the application:
* JDK 10+
* Maven 3+

#### Luniistore\* software

This application requires some assets from the official Luniistore\* software.

* Download and install it
* The assets we need are stored in a user-specific folder, referred to as `$LOCAL_LUNIITHEQUE` in the remainder of this documentation. Its path depends on your platform:
  * On Linux, is is located at `~/.local/share/Luniitheque`
  * On Windows, it is located at `%UserProfile%\AppData\Roaming\Luniitheque`
* The assets must be copied to a newly-created user-specific folder (referred to as `$DOT_STUDIO`) in order to be read by this application. Its expected path depends on your platform:
  * On Linux, `~/.studio`
  * On Windows, `%UserProfile%\.studio`

#### Lunii\* Story Teller driver

Transfer of story pack to and from the Story Teller device is handled by the official Lunii\* driver. This driver
is distributed with the Luniistore\* software, and must be obtained through it:

* Create the folders `$DOT_STUDIO/lib/` in your home directory (e.g. `mkdir -p ~/.studio/lib` on Linux, `mkdir %UserProfile%\.studio` on Windows)
* From `$LOCAL_LUNIITHEQUE/lib`, copy these three JAR files into `$DOT_STUDIO/lib/`:
  * `lunii-java-util.jar`
  * `lunii-device-gateway.jar`
  * `lunii-device-wrapper.jar`

#### Official pack metadata database

In order to display story pack metadata, they must be downloaded and stored on a local database file.

* Start the official Luniistore\* software to get a fresh authentication token (valid for one hour)
* Open `$LOCAL_LUNIITHEQUE/.local.properties` in a text editor, and note the value of the token:
  * If your are logged in on the Luniistore\* software, the token is located on the `tokens` property, `tokens.access_tokens.data.firebase` attribute
  * If your are not logged in on the Luniistore\* software, the token is located on the `token` property, `firebase` attribute
* Query `https://lunii-data-prod.firebaseio.com/packs.json?auth=TOKEN` and save the result as `$DOT_STUDIO/db/official.json` (e.g. `curl -v -X GET https://lunii-data-prod.firebaseio.com/packs.json?auth=TOKEN > ~/.studio/db/official.json`)

### Building the application

Once you have cloned this repository, execute `mvn clean install` to build the application. This will produce the distribution archive in `web-ui/target/`.

### Starting the application

Your must first build the application or download a distribution archive.

To start the application: 
* Unzip the distribution archive
* Run the launcher script: either `studio.sh` or `studio.bat` depending on your platform. You may need to make them executable first.
If run in a terminal, it should display some logs, ending with `INFOS: Succeeded in deploying verticle`.
* Open a browser and type the url `http://localhost:8080` to load the web UI.

### Using the application

The web UI is made of two screens:

* The pack library, to manage your local library and transfer to / from your device
* The pack editor, to create or edit a story pack

#### Pack library

The pack library screen always shows the story packs in your local library. These are the packs located in `$DOT_STUDIO/library`. The packs may be either in binary format (the official format, understood by the device) or archive format (the unofficial format, used for story pack creation and edition).

When the device is plugged, another pane will appear on the left side, showing the device metadata and story packs. Dragging and dropping a pack from or to the device will initiate the transfer.

#### Pack editor

The pack editor screen show the current story pack being edited. By default, it shows a sample story pack intended as a model of correct usage.

A pack is composed of a few metadata and the diagram describing the various steps in the story:

* Stage nodes are used to display an image and/or play a sound
* Action nodes are used to transition from one stage to the next, and to manage the available options


STORY TELLER DEVICE MEMORY 
--------------------------

The Story Teller device exposes two memory storages: the SD card and an SPI memory (probably a flash chip?).

Both storages are divided in 512-bytes sectors. Data are referenced by their sector number.

### SD Card

| Sectors        | Content                                  |
|----------------|------------------------------------------|
| 1              | UUID                                     |
| 2              | ???                                      |
| 3              | Error, firmware version and SD card size |
| 4 - 454        | Logo image                               |
| 455 - 905      | USB image                                |
| 906 - 1356     | Low battery image                        |
| 1357 - 1807    | Error image                              |
| 1808 - 74999   | ???                                      |
| 75000 - 99999  | Pack stats (whatever this means?)        |
| 100000         | Story packs index                        |
| 100001 - END   | Story packs                              |

# SPI

Not yet analyzed.


STORY PACK FILE FORMAT
----------------------

A story pack is composed of nodes. There are two kinds of nodes:
* Stage nodes display an image and play a sound, both of which are optional. On top of these assets, stage nodes define:
  * The action/transition that happens when the OK button is pressed
  * The action/transition that happens when the HOME button is pressed
  * Which controls are available
* Action nodes are used to transition from one stage to the next. Action nodes may contain:
  * A single stage node that is automatically played, or
  * Multiple options for the user to choose from, by navigating the options/stages with the wheel. Every time the user turns the wheel, the previous or next option/stage is played.


A pack file is also divided in 512-bytes sectors. Incomplete sectors are padded with zeros.

For instance, a pack with 4 stage nodes and 2 action nodes has the following structure:

| Sectors        | Content        |
|----------------|----------------|
| 1              | Metadata       |
| 2 - 5          | Stage nodes    |
| 6 - 7          | Action nodes   |
| 8 - 458        | Image asset 1  |
| 459 - 909      | Image asset 2  |
| 910 - 9999     | Sound asset 1  |
| 10000 - 19999  | Sound asset 2  |
| 20000 - 29999  | Sound asset 3  |
| 30000 - 39999  | Sound asset 4  |
| 40000          | Check bytes    |

### Metadata sector

| Bytes          | Data                      | Type   | Value         |
|----------------|---------------------------|--------|---------------|
| 1 - 2          | Number of stage nodes     | short  |               |
| 3              | Factory disabled          | byte   | O or 1        |
| 4 - 5          | Version                   | short  | Defaults to 1 |

### Stage node sector

| Bytes          | Data                      | Type     | Value                               |
|----------------|---------------------------|----------|-------------------------------------|
| 1 - 16         | UUID                      | long * 2 | Most significant bits first         |
| 17 - 20        | Image start sector        | int      | Offset from first stage node or -1  |
| 21 - 24        | Image size                | int      | Number of sectors                   |
| 25 - 28        | Audio start sector        | int      | Offset from first stage node or -1  |
| 29 - 32        | Audio size                | int      | Number of sectors                   |
| 33 - 34        | Action when OK pressed    | short    | Offset from first stage node or -1  |
| 35 - 36        | Options in transition     | short    | Number of available options         |
| 37 - 38        | Chosen option             | short    | Index of the selected option        |
| 39 - 40        | Action when HOME pressed  | short    | Offset from first stage node or -1  |
| 41 - 42        | Options in transition     | short    | Number of available options         |
| 43 - 44        | Chosen option             | short    | Index of the selected option        |
| 45 - 46        | Wheel enabled             | short    | 0 or 1                              |
| 47 - 48        | OK enabled                | short    | 0 or 1                              |
| 49 - 50        | HOME enabled              | short    | 0 or 1                              |
| 51 - 52        | PAUSE enabled             | short    | 0 or 1                              |
| 53 - 54        | Auto jump when audio ends | short    | 0 or 1                              |

### Action node sector

An action node is just a list of available options. Each option takes a short, representing the offset (from first stage node) to a stage node.

### Image asset sectors

Image assets are 24-bits, 320x240, Windows BMP files. Images may use colors, even though some colors may not render
accurately due to the screen being behind the plastic cover.
If the last sector is incomplete, it is padded with zeros.

### Audio asset sectors

Audio assets are signed 16-bits, mono, 32000 Hz, WAVE files. If the last sector is incomplete, it is padded with zeros.

### Check bytes sector

The last sector of a pack file must contain a predefined sequence of 512 bytes.


LICENSE
-------

This project is licensed under the terms of the Mozilla Public License 2.0.