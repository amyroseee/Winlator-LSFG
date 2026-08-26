<p align="center">
	<img src="logo.png" width="376" height="128" alt="Winlator Logo" />
</p>

# Winlator LSFG

A Winlator fork focused on bringing LSFG-VK frame generation to Android, with a few extra quality-of-life features.

## What's included

- **LSFG-VK integration**
  - Adapted to work with the Winlator glibc environment
  - Uses a user-provided `Lossless.dll`
  - The DLL is not bundled with the app

- **Frame Generation**
  - 2x
  - 3x
  - 4x
  - The current Winlator integration is still experimental and may improve in future releases

- **LSFG presets**
  - Performance
  - Balanced
  - Quality
  - Custom

- **Per-container LSFG settings**
  - Frame generation can be configured separately for each container

- **High Contrast FPS Counter**
  - Simple performance overlay designed for better readability during gameplay

- **Save Backup & Restore**
  - Backup and restore game saves directly from the container menu
  - Supports common Wine save locations

- **Visual changes**
  - Cyan app theme
  - Updated launcher and internal visuals

## Lossless.dll

LSFG requires a compatible `Lossless.dll` provided by the user.

This project does not include or distribute `Lossless.dll`.

## Current Version

**v1.0-preview.1**

This is the first public preview. Compatibility may vary depending on the game, device and GPU driver.

## Credits

Based on Winlator by **brunodev85**.

LSFG-VK and other third-party components belong to their respective authors.

## Disclaimer

This is an unofficial Winlator fork and is not affiliated with the original Winlator project or Lossless Scaling.
