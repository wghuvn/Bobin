# Deserter and hub landing repair — 1.1.5

Replace `bobin_craft-1.1.4.jar` with `bobin_craft-1.1.5.jar` on both client and server. Existing saves work without resetting the world or editing player data.

Two connected bugs caused the missing countdown:

1. Friendly sentries inherit Minecraft's hostile pillager class. The landing check counted them as enemies, rejecting every candidate around the hub spawn. First-join onboarding, respawn and extraction returns could therefore fail.
2. Deserter detection required successful raid onboarding. A player affected by the landing failure remained exempt, even when walking outside the hub in survival mode.

The landing check now ignores friendly hub sentries while continuing to reject actual living enemies. Perimeter detection is independent of raid onboarding, so it works for new and restored players with no raid profile. Successful map deployments remain exempt; returning inside still cancels the countdown.

The next join can complete normal hub onboarding. Leave the wall footprint on foot in survival to test the red ten-second Deserter countdown and bomb strike. Creative and spectator modes remain exempt.

For read-only troubleshooting, run `/bobin hub deserter status`. It reports whether the current server is available, the hub layout, whether the player is inside/exempt/on an authorized map deployment, and any active countdown.

Regression checks reproduce the reported outside position and prove that all 405 candidate positions around the hub spawn previously overlapped friendly sentries. Automated checks cover the fixed classifications, countdown timing, map exemption, cancellation, persisted deadlines and existing raid state. Live Minecraft play verification remains outstanding.
