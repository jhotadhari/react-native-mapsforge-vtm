# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/)
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]
### Fixed
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
- Added prop `onMapEvent` to `mapContainer` component. It listens to the same event like the `useMapEvents` hook.
- Added props `enabledZoomMin` and `enabledZoomMax` to all base layer components (`LayerBitmapTile`, `LayerHillshading`, `LayerMBTilesBitmap` and `LayerMapsforge`).

### Changed
- Added prop `emitsMapEvents` to `mapContainer` component. If `false`, the map won't emit any mapEvents and the `onMapEvent` or `useMapEvents` are useless. If `undefined`|`null` it will be `true` if `onMapEvent` prop is set. **If you want to use the `useMapEvents` hook, you have to set `emitsMapEvents` to `true`!**
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

[Unreleased]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.5.1...HEAD
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
