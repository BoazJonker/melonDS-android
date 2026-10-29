# Pokémon Unbound

A GBA app for playing Pokémon Unbound, styled after Spirit Tracks HD. It is built on the
[mGBA](https://mgba.io/) core (version 0.10.5, included as a submodule in `app/src/main/cpp/mgba`).

- Animated 3D on-screen buttons: A is a Poké Ball and B is a Great Ball. Buttons sink when pressed, send out a
  gold ring and spring back. The D-pad tilts toward the pressed direction.
- An animated Borrius night sky behind the game and on the start screen, with stars, aurora, a moon, mountains
  and drifting wisps of light.
- Continue right where you left off (quick resume), with automatic quick saves while playing.
- A save state slot, fast-forward (2x to 8x), and three screen filters (Sharp, Smooth, Pixel perfect).
- Game controller support.
- Applies the Unbound `.bps` patch to a FireRed ROM when the game starts, so either a patched ROM or
  FireRed plus the patch works.
- Export and import `.sav` files to move saves to and from other emulators.

No game files are included. On first launch the app asks for your game file.

## Building

The `Build Pokemon Unbound APK` workflow builds `Pokemon-Unbound.apk` on every push that changes this folder,
and publishes it on the `unbound-latest` release.

Locally, with the Android SDK, NDK 28.0.13004108 and CMake 3.22.1 installed:

```
git submodule update --init unbound/app/src/main/cpp/mgba
./gradlew -p unbound :app:assembleDebug
```
