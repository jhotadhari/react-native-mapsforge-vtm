# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/)
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]
This release contains love for elevation data <3

### Added
- All new *Clear Asymmetry* shading algorithms are implemented as options for `LayerHillshading` (thank you @Sublimis to create them and implement them to `mapsforge`, so I can use theme here):
    - [`AdaptiveClasyHillShading`](https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/AdaptiveClasyHillShading.java)
  	- [`StandardClasyHillShading`](https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/StandardClasyHillShading.java)
  	- [`SimpleClasyHillShading`](https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/SimpleClasyHillShading.java)
  	- [`HalfResClasyHillShading`](https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HalfResClasyHillShading.java)
  	- [`HiResClasyHillShading`](https://github.com/mapsforge/mapsforge/blob/master/mapsforge-map/src/main/java/org/mapsforge/map/layer/hills/HiResClasyHillShading.java)

### Changed
- Update dependencies; Update dependency `com.github.mapsforge.vtm` to version `0.25.0`.
- The java side of the `LayerHillshading` component is entirely new. Now the component is using the new [`vtm-hillshading module`](https://github.com/mapsforge/vtm/pull/1189), implemented in `com.github.mapsforge.vtm` version `0.25.0`. Thank you @devemux86! It's much faster, stable and the generated tiles are more accurate.
- Change the cache filename for `LayerHillshading`: Remove dots and dashes. That makes previous caches obsolete.
- Add props to `MapContainer` component:
  	- `hgtInterpolation` whether to interpolate elevation or not.
  	- `hgtFileInfoPurgeThreshold` how many neighboring DEMs should be kept in memory.
- Change `HgtReader` class (to retrieve altitude at certain coordinates):
  	- Better performance. It's multithreaded now and loading new DEM (hgt) files into memory doesn't block the ui thread anymore.
  	- Added support for interpolation. The code for interpolation is copied from [JOSM Elevation Plugin by Harald Hetzner](https://github.com/hhtznr/JOSM-Elevation-Plugin/tree/main), thank you @hhtznr!.
  	- For now the `HgtReader` only supports DEMs with a resolution of 3 arc seconds. Instead of fixing this issue, I will wait that this feature will be implemented properly into mapsforge, see [#1621
  ](https://github.com/mapsforge/mapsforge/issues/1621).

### Removed
- Remove built in themes `MOTORIDER_DARK` and `OSMAGRAY`. Because they are not existing anymore in latest version of `com.github.mapsforge.vtm`.

## [0.6.0] - 2025-03-26
### Added
- New props for `LayerMapsforge`: `hasBuildings` and `hasLabels`.
- New prop for `LayerBitmapTile`: `alpha` to control opacity.
- `CanvasAdapterModule` to control `lineScale`, `textScale` and `symbolScale` of all mapsforge layers of all `MapContainer`. The methods have to be called before the first `MapContainer` is initialized.

### Changed
- Individual cache dirs for `LayerHillshading`. Added props:
    - `cacheDirBase` defaults to `/`. If `/`, java will fallback to app internal cache dir.
    - `cacheDirChild` defaults to ``. If ``, will fallback to cache dbname.
- Individual cache dirs for `LayerBitmapTile`. Added props:
    - `cacheDirBase` defaults to `/`. If `/`, java will fallback to app internal cache dir.
    - `cacheDirChild` defaults to ``. If ``, will fallback to slugify url.

## [0.5.3] - 2025-03-19
### Added
- Add prop `hgtReadFileRate` to `MapContainerModule`. To control `HgtReader` read file throttle rate.

### Changed
- `HgtReader`: Purge hgt file data if not neighbors.

### Fixed
- `LayerHillshading`: Should update on `magnitude` or `cacheSize` changes.
- `LayerBitmapTile`: property `cacheSize` was not working.
- Missing export `useMapLayersCreated`.

## [0.5.2] - 2024-12-29
### Added
- prop `onHardwareKeyUp` to `MapContainer` component. Function that gets called when certain hardware keys are pressed.
- prop `emitsHardwareKeyUp` to `MapContainer` component. Defines which hardware key events are consumed (keycodes: `KEYCODE_VOLUME_UP` or `KEYCODE_VOLUME_DOWN`). If they are consumed, these events don't bubble, `onHardwareKeyUp` js event is triggers and `MapContainer:onHardwareKeyUp` function is called.

### Changed
- Make all `MapEventResponse` properties optional.

### Fixed
- `HardwareKeyListener` consumes all key events and prevents bubbling, breaks all key events. Now it only consumes the event if prop `emitsHardwareKeyUp` contains event keycode (`KEYCODE_VOLUME_UP` or `KEYCODE_VOLUME_DOWN`).
- Missing type exports: `XmlRenderTheme`, `RenderStyleOptionsCollection`.

## [0.5.1] - 2024-11-16
### Fixed
- `LayerMBTilesBitmap` `NullPointerException`. `dataSource.getBounds()` might be null.

## [0.5.0] - 2024-11-14
### Changed
- Changed export of nativeModules. They are exported directly, not bundled as a `nativeModules` object anymore.

### Fixed
- Types export

## [0.4.0] - 2024-11-14
### Added
- Added prop `onMapEvent` to `MapContainer` component. It listens to the same event like the `useMapEvents` hook.
- Added props `enabledZoomMin` and `enabledZoomMax` to all base layer components (`LayerBitmapTile`, `LayerHillshading`, `LayerMBTilesBitmap` and `LayerMapsforge`).

### Changed
- Added prop `emitsMapEvents` to `MapContainer` component. If `false`, the map won't emit any mapEvents and the `onMapEvent` or `useMapEvents` are useless. If `undefined`|`null` it will be `true` if `onMapEvent` prop is set. **If you want to use the `useMapEvents` hook, you have to set `emitsMapEvents` to `true`!**
- Renamed type `mapEvent` to `MapEventResponse`.
- Renamed `minZoom`|`MaxZoom` to `zoomMin`|`zoomMax` and `setMinZoom`|`setMaxZoom` to `setZoomMin`|`setZoomMax`. It was not consistent, both naming were used by different components.

## [0.3.0] - 2024-11-06
Path improvements

### Added
- Gesture events for both Path layers. Added props: `onPress`, `onLongPress` and `onDoubleTap`.
- Trigger event at any point at the map, for both Path layers. Added prop: `onTrigger`. Added method `triggerEvent` to path modules.
- Possibility to simplify paths. Added prop: `simplificationTolerance`.

### Changed
- Both path layer modules. Now they work the same way and `MapLayerPathSlopeGradientModule` extends `MapLayerPathModule`.
- Removed `strokeWidth` prop from `LayerPathSlopeGradient`. Now it uses the same `style` prop like `PathLayer`. Just that the color will be overwritten by the gradient color.

### Fixed
- The zickzacky appearance of `PathLayer`. Now it uses the same way of rendering like the `LayerPathSlopeGradient` component.

## [0.2.0] - 2024-11-06
Markers, hurrah.

### Added
- `LayerMarker` to hold `Marker` components.
    - Markers support raster image or svg symbols. Or symbols fallback to a customizable circle.
    - Markers support `press` and `longPress` events. Furthermore events can be triggered at any position on the map.

### Changed
- Responds types extend the `ResponseBase` interface.

### Fixed
- Example `PickerModalControl`, if options are empty. Conditions have to be boolean, otherwise react wants to render them as text, without `Text` component.

## [0.1.3] - 2024-10-30
Just updated README.md

## [0.1.2] - 2024-10-30
### Fixed
- Layer props: make reactTreeIndex optional. It is required but the MapContainer will mix it in

## [0.1.1] - 2024-10-30
### Fixed
- Publish script changelog

## [0.1.0] - 2024-10-30
### Added
- Better example app.
- Catch blocks to all native module methods. And `onError` function prop to all components and hooks.
- New `MapContainer` prop: `responseInclude`. We don't need to send everything always through the bridge bottleneck.
- New `MapContainer` prop: `mapEventRate`, the update rate of map events.
- `MapContainerModule` method: `setToBounds`.
- Some components respond their `bounds` and more meta data.
- New `LayerPath` prop: `style`. To parse almost all options from js to java `org.oscim.layers.vector.geometries.Style` (`texture` not supported yet).

### Changed
- Migrated all js to typeScript.
- Huge refactor.
- Some variable names and props have changed.

## [0.0.1] - 2024-10-25
First bumpy version

[Unreleased]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.5.3...v0.6.0
[0.5.3]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.5.2...v0.5.3
[0.5.2]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/jhotadhari/react-native-mapsforge-vtm/releases/tag/v0.0.1
