"""Original procedural brushless/propeller loop. Requires numpy and FFmpeg; no source recordings.
Run from project root: python docs/audio/generate_fpv_motor.py
"""
from pathlib import Path
import numpy as np
import wave, tempfile, subprocess, hashlib, json
rate=48000; seconds=8; n=rate*seconds
t=np.arange(n,dtype=np.float64)/rate
signal=np.zeros(n)
# Four slightly detuned two-blade rotors; modulation and all carriers repeat after eight seconds.
for i,hz in enumerate((223.,229.,233.,239.)):
    phase=2*np.pi*hz*t + .13*np.sin(2*np.pi*(3+i)*t+i)
    envelope=.84+.10*np.sin(2*np.pi*(2+i)*t+i)
    for harmonic in range(1,19):
        signal += envelope*np.sin(harmonic*phase+i*.7)/(harmonic**1.25)*.09
    signal += .018*np.sin(phase*.5 + i)  # All half-integer carriers still have integral cycles in 8s.
rng=np.random.default_rng(277214)
f=np.fft.rfftfreq(n,1/rate)
noise_spectrum=(rng.normal(size=len(f))+1j*rng.normal(size=len(f)))
shape=(1-np.exp(-(f/350)**2))/(1+(f/4800)**4)
noise_spectrum*=shape; noise_spectrum[0]=0;noise_spectrum[-1]=0
noise=np.fft.irfft(noise_spectrum,n=n);noise/=np.std(noise)
signal += noise*.045*(.85+.15*np.cos(2*np.pi*7*t))
signal-=signal.mean();signal*=.62/np.max(np.abs(signal))
output=Path('src/main/resources/assets/bobin_craft/sounds/drone/fpv_motor_loop.ogg')
output.parent.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory() as temp:
    wav=Path(temp)/'motor.wav'
    with wave.open(str(wav),'wb') as w:
        w.setparams((1,2,rate,0,'NONE','not compressed'));w.writeframes((signal*32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','5',
                    '-metadata','title=Bobin Craft original procedural FPV motor',str(output)],check=True)
print(json.dumps({'duration_s':seconds,'sample_rate':rate,'channels':1,'source_peak':float(abs(signal).max()),
                  'source_rms':float(np.sqrt(np.mean(signal**2))),'boundary_step':float(abs(signal[0]-signal[-1])),
                  'sha256':hashlib.sha256(output.read_bytes()).hexdigest()},indent=2))
