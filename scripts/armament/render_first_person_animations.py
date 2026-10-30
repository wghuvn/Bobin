"""Render the real authored Blender clips to labelled MP4s, without changing assets.

blender -b -t 4 --python scripts/armament/render_first_person_animations.py -- \
    --variant default --output-dir art/armament/animation-rework/after
Requires ffmpeg; output is Blender authoring evidence, never Minecraft footage.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import sys

import bpy

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/armament'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--variant', choices=('default', 'slim'), default='default')
parser.add_argument('--source-dir', type=Path, default=ART)
parser.add_argument('--output-dir', type=Path, required=True)
parser.add_argument('--label', default='after')
parser.add_argument('--fps', type=int, default=12)
parser.add_argument('--samples', type=int, default=16)
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
assert 6 <= args.fps <= 30 and 1 <= args.samples <= 128
stem = 'first_person_fixture' + ('_slim' if args.variant == 'slim' else '')
source = args.source_dir / (stem + '.blend')
source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
bpy.ops.wm.open_mainfile(filepath=str(source))
scene = bpy.context.scene
source_fps = scene.render.fps / scene.render.fps_base
scene.render.engine = 'CYCLES'
scene.cycles.device = 'CPU'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = False
scene.render.resolution_x, scene.render.resolution_y = 640, 360
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.film_transparent = False
scene.camera.data.sensor_fit = 'VERTICAL'
scene.camera.data.sensor_height = 24
scene.camera.data.lens = 12 / math.tan(math.radians(70) / 2)
output = args.output_dir.resolve()
output.mkdir(parents=True, exist_ok=True)
metadata = {'source': str(source.relative_to(ROOT)) if source.is_relative_to(ROOT) else source.name,
            'source_sha256': source_hash, 'variant': args.variant, 'label': args.label,
            'kind': 'Blender authoring preview, not Minecraft', 'vertical_fov': 70,
            'resolution': [640, 360], 'fps': args.fps, 'samples': args.samples, 'clips': {}}
videos = []
for clip in ('raise', 'idle', 'inspect', 'move', 'lower'):
    durations = []
    for obj in scene.objects:
        animation = obj.animation_data
        if animation is None or not animation.nla_tracks:
            continue
        animation.action = None
        animation.use_nla = True
        for track in animation.nla_tracks:
            track.mute = track.name != clip
            if track.name == clip:
                durations.extend(strip.action.frame_range[1] / source_fps for strip in track.strips)
    if not durations:
        raise ValueError('Missing authored clip: ' + clip)
    duration = max(durations)
    frames = max(2, math.ceil(duration * args.fps) + 1)
    folder = output / (stem + '.' + clip + '.frames')
    folder.mkdir(exist_ok=True)
    for index in range(frames):
        seconds = min(index / args.fps, duration)
        frame = seconds * source_fps
        scene.frame_set(math.floor(frame), subframe=frame - math.floor(frame))
        scene.render.filepath = str(folder / f'{index:04d}.png')
        bpy.ops.render.render(write_still=True)
    destination = output / (stem + '.' + clip + '.mp4')
    title = 'Blender | ' + args.label + ' | ' + args.variant + ' | ' + clip
    subprocess.run(['ffmpeg', '-y', '-hide_banner', '-loglevel', 'error', '-framerate', str(args.fps),
                    '-i', str(folder / '%04d.png'), '-frames:v', str(frames),
                    '-vf', "drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:text='" + title + "':fontsize=15:fontcolor=white:box=1:boxcolor=black@0.65:x=10:y=10",
                    '-c:v', 'libx264', '-threads', '2', '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(destination)], check=True)
    # Retain representative original frames; encoded videos carry the full sequence.
    keep = {0, frames // 2, frames - 1}
    for index in range(frames):
        if index not in keep:
            (folder / f'{index:04d}.png').unlink()
    metadata['clips'][clip] = {'duration_seconds': duration, 'frames': frames,
                              'video': destination.name, 'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()}
    videos.append(destination)
concat = output / (stem + '.concat.txt')
concat.write_text(''.join("file '" + p.name + "'\n" for p in videos))
subprocess.run(['ffmpeg', '-y', '-hide_banner', '-loglevel', 'error', '-f', 'concat', '-safe', '0',
                '-i', str(concat), '-c', 'copy', '-movflags', '+faststart', str(output / (stem + '.all-clips.mp4'))], check=True)
concat.unlink()
if hashlib.sha256(source.read_bytes()).hexdigest() != source_hash:
    raise RuntimeError('Read-only animation rendering changed its source blend')
(output / (stem + '.animation-preview.json')).write_text(json.dumps(metadata, indent=2) + '\n')
print('PASS_READ_ONLY_ANIMATION_PREVIEW', stem, metadata['clips'])
