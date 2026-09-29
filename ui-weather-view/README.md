# ui-weather-view

Animated weather backgrounds, copied from
[Breezy Weather](https://github.com/breezy-weather/breezy-weather)
(`ui-weather-view` module), which is licensed under the
**GNU Lesser General Public License v3.0**. The full licence text is in
`LICENSE` next to this file.

## Why it is a separate module

The LGPL allows an application to use the library as long as the library
itself stays replaceable and its source is available. Keeping it in its own
Gradle module, unmodified apart from the notes below, and shipping the licence
and this file is the simplest way to honour that.

## Changes made

* `minSdk` lowered from 24 to 21. The module only uses `Canvas`, `Paint`,
  `ObjectAnimator` and `SensorManager`, all available since API 1-14, so no
  code changes were needed.
* Build script rewritten to plain AGP, because the original depends on
  Breezy's `buildSrc` convention plugins.

No source file in `src/` has been modified.

## Upstream

https://github.com/breezy-weather/breezy-weather
