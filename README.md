[![Release](https://img.shields.io/github/v/release/marian-m12l/studio)](https://github.com/marian-m12l/studio/releases/latest)
[![Gitter](https://badges.gitter.im/STUdio-Story-Teller-Unleashed/general.svg)](https://gitter.im/STUdio-Story-Teller-Unleashed/general?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

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

### Prerequisite

To run the application:
* JDK 11+

To build the application:
* Maven 3+

#### Luniistore\* software

On Windows, this application requires the _libusb_ driver to be installed. The easiest way to achieve this is to have
the official Luniistore\* software installed.

#### Official pack metadata database

In order to display story pack metadata, this application requires some assets from the official Luniistore\* software,
which must be downloaded and stored on a local database file.

* The assets we need are stored in a user-specific folder, referred to as `$LOCAL_LUNIITHEQUE` in the remainder of this documentation. Its path depends on your platform:
  * On Linux, it is located at `~/.local/share/Luniitheque`
  * On macOS, it is located at `~/Library/Application\ Support/Luniitheque`
  * On Windows, it is located at `%UserProfile%\AppData\Roaming\Luniitheque`
* The assets must be copied to a newly-created user-specific folder (referred to as `$DOT_STUDIO`) in order to be read by this application. Its expected path depends on your platform:
  * On Linux and macOS, `~/.studio`
  * On Windows, `%UserProfile%\.studio`

To fetch the story pack metadata:

* Start the official Luniistore\* software to get a fresh authentication token (valid for one hour)
* Open `$LOCAL_LUNIITHEQUE/.local.properties` in a text editor, and note the value of the token:
  * If your are logged in on the Luniistore\* software, the token is located on the `tokens` property, `tokens.access_tokens.data.firebase` attribute
  * If your are not logged in on the Luniistore\* software, the token is located on the `token` property, `firebase` attribute
* Query `https://lunii-data-prod.firebaseio.com/packs.json?auth=TOKEN` and save the result as `$DOT_STUDIO/db/official.json` (e.g. `curl -v -X GET https://lunii-data-prod.firebaseio.com/packs.json?auth=TOKEN > ~/.studio/db/official.json`)

### [Optional] Building the application

Once you have cloned this repository, execute `mvn clean install` to build the application. This will produce the **distribution archive** in `web-ui/target/`.

### Starting the application

Your must first build the application or download a distribution archive ([check the latest release](https://github.com/marian-m12l/studio/releases/latest)).

To start the application: 
* Unzip the distribution archive
* Run the launcher script: either `studio-linux.sh`, `studio-macos.sh` or `studio-windows.bat` depending on your platform. You may need to make them executable first.
If run in a terminal, it should display some logs, ending with `INFOS: Succeeded in deploying verticle`.
* Open a browser and type the url `http://localhost:8080` to load the web UI.

Note: You should avoid running the script as superuser/administrator, as this may create permissions issues.

### Using the application

The web UI is made of two screens:

* The pack library, to manage your local library and transfer to / from your device
* The pack editor, to create or edit a story pack

#### Pack library

The pack library screen always shows the story packs in your local library. These are the packs located in `$DOT_STUDIO/library`. The packs may be either in binary format (the official format, understood by the device) or archive format (the unofficial format, used for story pack creation and edition).

When the device is plugged, another pane will appear on the left side, showing the device metadata and story packs. Dragging and dropping a pack from or to the device will initiate the transfer.

#### Pack editor

The pack editor screen show the current story pack being edited. By default, it shows a sample story pack intended as a model of correct usage.

A pack is composed of a few metadata and the diagram describing the various steps in the story:

* Stage nodes are used to display an image and/or play a sound
* Action nodes are used to transition from one stage to the next, and to manage the available options

The editor supports several file formats for audio and image assets.

##### Images

Image files may use the following formats (formats marked with asterisks are automatically converted when transferring
to the device) :
* PNG\*\*
* JPEG\*\*
* BMP (24-bits)

Image dimensions must be 320x240. Images may use colors, even though some colors may not render accurately due to
the screen being behind the plastic cover. Bear in mind that the color of the cover may change, as seen with the
recently released Christmas edition.

##### Audio

Audio files may use the following formats (formats marked with asterisks are automatically converted when transferring
to the device) :
* MP3\*\*
* OGG/Vorbis \*\*
* WAVE (signed 16-bits, mono, 32000 Hz)

MP3 and OGG files are expected to be sampled at 44100Hz.

### [Experimental] Seeing unofficial metadata in Luniistore\* application / Loading official database from Luniistore\* application

These **experimental** features allow:
* to display correct(-ish) metadata for unofficial story packs (stored on the device) in the official Luniistore\* application.
* to automatically load / refresh the official metadata database when running the official Luniistore\* application.

To enable these features, locate the configuration file `Luniistore.cfg`:
  * On Linux, in `/opt/Luniistore/app`
  * On macOS, in `/Applications/Luniistore.app/Contents/Java`
  * On Windows, in `%ProgramFiles%\Luniistore\app`

Then add this line under the `[JVMOptions]` section (replace `$DOT_STUDIO` with the actual path)(use forward slashes
even on Windows):

```
-javaagent:$DOT_STUDIO/agent/studio-agent.jar
```


LICENSE
-------

This project is licensed under the terms of the **Mozilla Public License 2.0**. The terms of the license are in
the `LICENSE` file.

The `vorbis-java` library, as well as the `VorbisEncoder` class are licensed by the Xiph.org Foundation. The terms of
the license can found in the `LICENSE.vorbis-java` file.
