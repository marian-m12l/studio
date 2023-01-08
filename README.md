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

[Instructions en français](README_fr.md)

Create and transfer your own story packs to and from the Lunii[^1] story teller device.

DISCLAIMER
----------

This software relies on reverse engineering research, which is limited to gathering the information necessary to ensure interoperability with the Lunii[^1] story teller device, and does not distribute any protected content.

**USE AT YOUR OWN RISK**. Be advised that despite the best efforts to keep this software safe, it comes with
**NO WARRANTY** and may brick your device.

[^1]: Lunii and Luniistore are registered trademarks of Lunii SAS. This work is in no way affiliated with Lunii SAS.

USAGE
-----

### Choose your edition

:rocket: "Native edition" is faster and doesn't need java to be installed.
Few architecture are available (depending on available Github Host Runners), but you may [build your own](#build-native-edition).

:hotsprings: "Java edition" needs java and supports almost any architecture (restricted by [usb4java](http://usb4java.org) )

| CPU/OS  | Linux        | Windows            | MacOS              | 
| :---:   | :---:        | :---:              | :---:              |
| x86_64  | :hotsprings: :rocket: | :hotsprings: :rocket: | :hotsprings: :rocket: |
| x86     | :hotsprings: | :hotsprings:       |                    |
| arm     | :hotsprings: |                    |                    |
| aarch64 | :hotsprings: |                    | :hotsprings:       |

### Prerequisite

* (Java Edition only) Java JRE 11+
* On Windows, this application requires the _libusb_ driver to be installed. The easiest way to achieve this is to have the official Luniistore[^1] software installed (but not running).

### Installation

* **Download** [the latest release](https://github.com/kairoh/studio/releases/latest) (or [build the application](#for-developers)).
* **Unzip** the distribution archive
* **Run the launcher script**: either `studio.sh` or `studio.bat` according to your platform. You may need to make them executable first.
* If it does not open automatically, **open a browser** and type the url `http://localhost:8080` to load the web UI.

Note: avoid running the script as superuser/administrator, as this may create permissions issues.

### Configuration

Studio is portable by default: everything (except JRE for java edition) is relative to current directory.

<details>
  <summary>Customization</summary>

Configuration order is :
1. (if defined) Java System property (ie: `-Dstudio.port=8081`)
2. (if defined) environment variable (ie: `STUDIO_PORT=8081`)
3. default value (inside code)

| Environment variable   | Java System Property   | Default value | Description |
| ---------------------- | --------------------   | ------------- | ----------- |
| `STUDIO_HOME`          | `studio.home`          | `.` (=current dir)                  | Studio home        |
| `STUDIO_HOST`          | `studio.host`          | `localhost`                         | HTTP listen address |
| `STUDIO_PORT`          | `studio.port`          | `8080`                              | HTTP listen port |
| `STUDIO_DB_OFFICIAL`   | `studio.db.official`   | `${studio.home}/db/official.json`   | Official json file database  |
| `STUDIO_DB_UNOFFICIAL` | `studio.db.unofficial` | `${studio.home}/db/unofficial.json` | Unofficial json file database |
| `STUDIO_LIBRARY`       | `studio.library`       | `${studio.home}/library/`           | Library path |
| `STUDIO_TMPDIR`        | `studio.tmpdir`        | `${studio.home}/tmp/`               | Temporary path |
| `STUDIO_MOCK_DEVICE`   | `studio.mock.device`   | `${studio.home}/device/`            | Mock device path |
| `STUDIO_OPEN_BROWSER`  | `studio.open.browser`  | `true`                              | Auto open browser |

Sample to listen on port 8081 (with system property) :
- On Windows: `studio.bat -Dstudio.port=8081`
- On Linux / MacOS: `./studio.sh -Dstudio.port=8081`

</details>

### Using the application

The web UI is made of 2 screens:

* The pack library, to manage your local library and transfer to / from your device
* The pack editor, to create or edit a story pack

#### Local library and transfer to/from the device

The pack library screen always shows the story packs in your local library. These are the packs located on your computer (in studio `library` subfolder). 
**3 file formats** may exist in your library:
* `Raw` is the official format understood by the **older devices** (firmware v1.x -- these devices use a low-level USB protocol)
* `FS` is the official format understood by the **newer devices** (firmware v2.x -- these devices are seen as a removable storage)
* `Archive` is an unofficial format, used by STUdio only in the story pack **editor**

**Conversion** of a story pack will happen automatically when a transfer is initiated, or may be triggered manually.
Variations of a given story pack are grouped together in the UI for better readability.
**The most recent file** (highlighted in the UI) gets transferred to the device.

When the device is plugged, **another pane will appear on the left side**, showing the device metadata and story packs. **Dragging and dropping** a pack from or to the device will initiate the transfer.

#### Pack editor

The pack editor screen shows the current story pack being edited. By default, it shows a sample story pack intended as a model of correct usage.

A pack is composed of a few metadata and the diagram describing the various steps in the story:

* Stage nodes are used to display an image and/or play a sound
* Action nodes are used to transition from one stage to the next, and to manage the available options

The editor supports several file formats for audio and image assets.

##### Images

Image files may use the following formats (formats marked with asterisks are automatically converted when transferring to the device) :
* PNG
* JPEG
* BMP (24-bits)

**Image dimensions must be 320x240**. Images may use colors, even though some colors may not render accurately due to
the screen being behind the plastic cover. Bear in mind that the cover may alter image color.

##### Audio

Audio files may use the following formats (formats marked with asterisks are automatically converted when transferring to the device) :
* MP3
* OGG/Vorbis
* WAVE (signed 16-bits, mono, 32000 Hz)

MP3 and OGG files are expected to be sampled at 44100Hz.

#### Wiki

More information, including an illustrated usage guide courtesy of [@appenzellois](https://github.com/appenzellois),
available [in the project wiki](https://github.com/marian-m12l/studio/wiki/Documentation).

FOR DEVELOPERS
--------------

### Build with gitops

* Fork this repository
* Go to your "Actions" tab
* Check artifacts generated by [Native Workflow](.github/workflows/native.yml)

### Build Java edition

#### Prerequisite

* Maven 3+
* Java JDK 11+

#### Local build

* Clone this repository
* Build the application: `mvn install`
This will produce the **distribution archive** in `web-ui/target/quarkus-app/`.

### Build Native edition

See [Quarkus native guide](https://quarkus.io/guides/building-native-image)

#### Prerequisite

* Maven 3+
* Docker or any GraalVM supporting AWT : [Mandrel 22.3+ for Linux](https://github.com/graalvm/mandrel) or [Liberica NIK 22.3+ for Win and Mac](https://bell-sw.com/pages/downloads/native-image-kit/#/nik-22-17)

#### Local build

* Clone this repository
* Build the application: `mvn install -Pnative`

This will produce the **distribution archive** in `web-ui/target/`.
On Windows, some required DLL will be in `native-image` subfolder.

THIRD-PARTY APPLICATIONS
------------------------

If you liked STUdio, you will also like:
* ~~[mhios (Mes Histoires Interactives Open Stories)](https://www.mhios.com) was an online open library of interactive stories (courtesy of [@sebbelese](https://github.com/sebbelese))~~
* [Moiki](https://moiki.fr/) is an online tool to create interactive stories that can be exported for STUdio (courtesy
of [@kaelhem](https://github.com/kaelhem))

LICENSE
-------

This project is licensed under the terms of the **Mozilla Public License 2.0**. The terms of the license are in the [LICENSE.md](LICENSE.md) file.

The `jvorbis` library, as well as the `VorbisEncoder` class are licensed by the Xiph.org Foundation. The terms of the license can be found in the [LICENSE-jvorbis.md](LICENSE-jvorbis.md) file.

The `com.jhlabs.image` package is licensed by Jerry Huxtable under the terms of the Apache License 2.0. The terms of the license can be found in the [LICENSE-jhlabs.md](LICENSE-jhlabs.md) file.
