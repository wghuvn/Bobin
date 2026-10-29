# Drone video and payload references for 1.7.0

These public sources were consulted on 22 October 2026. They inform the visual
and broad gameplay distinctions; the mod's damage, radii, fragment budgets and
link attenuation are fictional Minecraft balance values.

- [DJI: Aircraft Live View Flickering or Getting Stuck](https://repair.dji.com/help/content?customId=01700006476&documentType=&lang=en&paperDocType=ARTICLE&re=US&spaceId=17)
  describes mosaic, ghosting and stuttering, including transmission interference.
  The game adapts this into bounded corrupt blocks and actual held video frames.
- [DJI: Remote Controller Signal Error During Flight](https://repair.dji.com/help/content?customId=01700006475&documentType=&lang=en&paperDocType=ARTICLE&re=US&spaceId=17)
  describes weak/disconnected links, frozen/lost images and obstacles affecting
  reception. The game uses distance and successive Minecraft obstruction cells,
  with a short recovery period before ending control.
- [DJI: How Can I Deal with Image Transmission Issues?](https://repair.dji.com/help/content?customId=en-us03400006477&lang=en&paperDocType=paper&re=US&spaceId=34)
  describes loss of the image signal and a black screen. Temporary blackouts are
  used at very low in-game signal quality.
- [CADDX: Walksnail Avatar HD Pro Camera](https://en.caddxfpv.com.cn/support/hd-pro-kit1.html)
  documents wide-angle FPV optics and low-latency digital video. Camera field of
  view specifications are not directly interchangeable with Minecraft's vertical
  FOV; the game deliberately uses 110 degrees for FPV and 70 for the stabilized
  bomber view. Simulated frame delay grows from 35 to 255 ms with reception loss.
- [DJI Mini 4 Pro specifications](https://www.dji.com/mini-4-pro/specs)
  provide a consumer-camera-drone reference for the narrower, stabilized camera
  presentation. The mod does not replicate this product or its operational range.
- [Australian War Memorial: No 36 Mk I Grenade](https://www.awm.gov.au/collection/C222109)
  provides a museum description of fragmentation as an effect of the casing.
  The game's fragmentation variants emphasize invisible fragment intersections
  and injuries; high-explosive variants emphasize the pressure-wave component.

No real ammunition construction, guidance, explosive recipes or real-world
lethality parameters are reproduced. The light-cover breacher is a bounded game
mechanic for a small tag of wooden Minecraft blocks, rather than a physical
model of an actual specialized warhead.
