# Third-Party Notices

## FFmpeg (via media3 FFmpeg decoder extension)

flintrTV bundles native FFmpeg libraries for software audio decoding (AC-3, E-AC-3, DTS, TrueHD and others) on devices without a working hardware decoder.

- **Artifact:** `org.jellyfin.media3:media3-ffmpeg-decoder:1.3.1+2` (Maven Central)
- **Build source:** <https://github.com/jellyfin/jellyfin-androidx-media>, built from source by the Jellyfin project's public GitHub Actions. It contains the AndroidX Media3 `decoder_ffmpeg` extension (Apache License 2.0) and FFmpeg.
- **License as declared in the artifact's POM:** GPL-3.0 - this is the license the Jellyfin wrapper *repository* is published under, not necessarily the license that governs the bundled FFmpeg binary itself.
- **FFmpeg build configuration:** the wrapper repo builds FFmpeg with the official media3 `build_ffmpeg.sh` script, `--disable-everything` plus only the decoders this extension needs enabled, and **without** `--enable-gpl` or `--enable-version3`. Standard (non-`--enable-gpl`) FFmpeg is licensed GNU Lesser General Public License, version 2.1 or later (LGPL-2.1-or-later).
- **Linking:** FFmpeg is linked **statically** (`--enable-static`) into a single `libffmpegJNI.so` per ABI - there is no separate `libavcodec.so`/`libavformat.so` etc. This is **not** a dynamically-linked, drop-in-replaceable shared library as a previous version of this notice claimed. Complying with the LGPL's relink requirement means rebuilding `libffmpegJNI.so` from source (see below), not swapping a `.so` file.
- **FFmpeg source code:** <https://ffmpeg.org/download.html> for upstream FFmpeg; the exact source revision and build scripts for the bundled binaries are in the Jellyfin repository linked above, tag/version `1.3.1+2`.
- **License text:** <https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html>
- **Distribution:** flintrTV is currently distributed privately and not through the Play Store. The POM's GPL-3.0 declaration should be revisited and the actual license terms reconfirmed with the Jellyfin project before any public distribution.

FFmpeg is a trademark of Fabrice Bellard, originator of the FFmpeg project.
