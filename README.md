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

[Instructions en français](README_fr.md)

Create and transfer your own story packs to and from the Lunii\* story teller device.


DISCLAIMER
----------

This software relies on my own reverse engineering research, which is limited to gathering the information necessary to
ensure interoperability with the Lunii\* story teller device, and does not distribute any protected content.

**USE AT YOUR OWN RISK**. Be advised that despite my best efforts to keep this software safe, it comes with
**NO WARRANTY** and may brick your device.

\* Lunii is a registered trademark of Lunii SAS. I am (and this work is) in no way affiliated with Lunii SAS.


USAGE
-----

### Prerequisite

* Java JRE 11+
* On Windows, this application requires the _libusb_ driver to be installed. The easiest way to achieve this is to have
  the official Luniistore\* software installed (but not running).

### Installation

* **Download** [the latest release](https://github.com/marian-m12l/studio/releases/latest) (alternatively,
you can [build the application](#for-developers)).
* **Unzip** the distribution archive
* **Run the launcher script**: either `studio-linux.sh`, `studio-macos.sh` or `studio-windows.bat` depending on your
platform. You may need to make them executable first.
* If it does not open automatically, **open a browser** and type the url `http://localhost:8080` to load the web UI.

Note: avoid running the script as superuser/administrator, as this may create permissions issues.

### Configuration

Configuration order is :
1. (if defined) Java System property (ie: `-Dstudio.port=8081` )
2. (if defined) environment variable (ie: `STUDIO_PORT=8081` )
3. default value (inside code)

| Environment variable | Java System property | Default value | Description |
| ------------------------ | -------------------- | ------ | ----------- |
| `STUDIO_HOST`          | `studio.host`          | `localhost`                    | HTTP listen address |
| `STUDIO_PORT`          | `studio.port`          | `8080`                         | HTTP listen port |
| `STUDIO_DB_OFFICIAL`   | `studio.db.official`   | `~/.studio/db/official.json`   | Official json file database  |
| `STUDIO_DB_UNOFFICIAL` | `studio.db.unofficial` | `~/.studio/db/unofficial.json` | Unofficial json file database |
| `STUDIO_LIBRARY`       | `studio.library`       | `~/.studio/library/`           | Library path |
| `STUDIO_TMPDIR`        | `studio.tmpdir`        | `~/.studio/tmp/`               | Temporary path |
| `STUDIO_OPEN_BROWSER`  | `studio.open.browser`  | `true`                         | Auto open browser |
| `STUDIO_DEV_MODE`      | `studio.dev.mode`      | `prod`                         | if `dev`, enable mock mode |
| `STUDIO_MOCK_DEVICE`   | `studio.mock.device`   | `~/.studio/device/`            | Mock device path |

Sample to disable browser launching (with env var) and listen on port 8081 (with system property) :
- On Windows
```
set STUDIO_OPEN_BROWSER=false

java -Dstudio.port=8081 \
 -Dfile.encoding=UTF-8 -Dvertx.disableDnsResolver=true \
 -cp $STUDIO_PATH/${project.build.finalName}.jar:$STUDIO_PATH/lib/*:. \
 io.vertx.core.Launcher run ${vertx.main.verticle}
```

- On Linux / MacOS
```
export STUDIO_OPEN_BROWSER=false

java -Dstudio.port=8081 \
 -Dfile.encoding=UTF-8 -Dvertx.disableDnsResolver=true \
 -cp $STUDIO_PATH/${project.build.finalName}.jar:$STUDIO_PATH/lib/*:. \
 io.vertx.core.Launcher run ${vertx.main.verticle}` |
```

### Using the application

The web UI is made of two screens:

* The pack library, to manage your local library and transfer to / from your device
* The pack editor, to create or edit a story pack

#### Local library and transfer to/from the device

The pack library screen always shows the story packs in your local library. These are the packs located on your computer
(in a per-user `.studio` folder). **Three file formats** may exist in your library:
* `Raw` is the official format understood by the **older devices** (firmware v1.x -- these devices use a low-level USB protocol)
* `FS` is the official format understood by the **newer devices** (firmware v2.x -- these devices are seen as a removable storage)
* `Archive` is an unofficial format, used by STUdio only in the story pack **editor**

**Conversion** of a story pack will happen automatically when a transfer is initiated, or may be triggered manually.
Variations of a given story pack are grouped together in the UI for better readability. **The most recent file**
(highlighted in the UI) gets transferred to the device.

When the device is plugged, **another pane will appear on the left side**, showing the device metadata and story packs.
**Dragging and dropping** a pack from or to the device will initiate the transfer.

#### Pack editor

The pack editor screen shows the current story pack being edited. By default, it shows a sample story pack intended as
a model of correct usage.

A pack is composed of a few metadata and the diagram describing the various steps in the story:

* Stage nodes are used to display an image and/or play a sound
* Action nodes are used to transition from one stage to the next, and to manage the available options

The editor supports several file formats for audio and image assets.

##### Images

Image files may use the following formats (formats marked with asterisks are automatically converted when transferring
to the device) :
* PNG
* JPEG
* BMP (24-bits)

**Image dimensions must be 320x240**. Images may use colors, even though some colors may not render accurately due to
the screen being behind the plastic cover. Bear in mind that the color of the cover may change.

##### Audio

Audio files may use the following formats (formats marked with asterisks are automatically converted when transferring
to the device) :
* MP3
* OGG/Vorbis
* WAVE (signed 16-bits, mono, 32000 Hz)

MP3 and OGG files are expected to be sampled at 44100Hz.

#### Wiki

More information, including an illustrated usage guide courtesy of [@appenzellois](https://github.com/appenzellois),
available [in the project wiki](https://github.com/marian-m12l/studio/wiki/Documentation).


FOR DEVELOPERS
--------------

### Prerequisite

* Java JDK 11+
* Maven 3+

### Building the application

* Clone this repository: `git clone https://github.com/marian-m12l/studio.git`
* Build the application: `mvn clean install`

This will produce the **distribution archive** in `web-ui/target/`.


THIRD-PARTY APPLICATIONS
------------------------

If you liked STUdio, you will also like:
* [mhios (Mes Histoires Interactives Open Stories)](https://www.mhios.com) is an online open library of interactive
stories (courtesy of [@sebbelese](https://github.com/sebbelese))
* [Moiki](https://moiki.fr/) is an online tool to create interactive stories that can be exported for STUdio (courtesy
of [@kaelhem](https://github.com/kaelhem))


LICENSE
-------

This project is licensed under the terms of the **Mozilla Public License 2.0**. The terms of the license are in
the `LICENSE` file.

The `vorbis-java` library, as well as the `VorbisEncoder` class are licensed by the Xiph.org Foundation. The terms of
the license can be found in the `LICENSE.vorbis-java` file.

The `com.jhlabs.image` package is licensed by Jerry Huxtable under the terms of the Apache License 2.0. The terms of
the license can be found in the `LICENSE.jhlabs` file.
