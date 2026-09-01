package com.itantra.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== iTantra Color Palette ====================
// Primary: Deep Space Blue — the foundation of the iTantra dark theme
val SpaceBlue900 = Color(0xFF0D1B2A)
val SpaceBlue800 = Color(0xFF1B2A3D)
val SpaceBlue700 = Color(0xFF243447)
val SpaceBlue600 = Color(0xFF2E4060)

// Accent: Signal Orange — action items, active indicators, PTT button
val SignalOrange500 = Color(0xFFFF6B35)
val SignalOrange400 = Color(0xFFFF8C5A)
val SignalOrange300 = Color(0xFFFFAD80)
val SignalOrangeGlow = Color(0x40FF6B35) // 25% opacity for glow effects

// Alert: Distress Red — SOS triggers, alert overlays, emergency states
val DistressRed600 = Color(0xFFD62828)
val DistressRed500 = Color(0xFFE53E3E)
val DistressRed400 = Color(0xFFFC5C5C)
val DistressRedGlow = Color(0x40D62828) // 25% opacity for pulsing effect

// Success: Active Green — connected nodes, successful transcription
val ActiveGreen500 = Color(0xFF06A77D)
val ActiveGreen400 = Color(0xFF12C99B)
val ActiveGreen300 = Color(0xFF4DDBB4)
val ActiveGreenGlow = Color(0x3006A77D)

// Neutral / Surface
val Surface900 = Color(0xFF0A1520)
val Surface800 = Color(0xFF111E2D)
val Surface700 = Color(0xFF18273A)
val SurfaceVariant = Color(0xFF1E3047)
val OnSurface = Color(0xFFE8EDF2)
val OnSurfaceDim = Color(0xFF8FA3B8)
val OnSurfaceDimmer = Color(0xFF4A6078)

// Specific UI colors
val PeerDotWifi = Color(0xFF4AAFFF)      // Wi-Fi Direct node dot
val PeerDotBluetooth = Color(0xFFFFD166) // Bluetooth node dot
val RadarSweep = Color(0x5006A77D)       // Semi-transparent radar sweep
val RadarGrid = Color(0x2006A77D)        // Very subtle radar grid

// Confidence bar gradient
val ConfidenceHigh = ActiveGreen400
val ConfidenceMid = Color(0xFFFFD166)
val ConfidenceLow = DistressRed500
