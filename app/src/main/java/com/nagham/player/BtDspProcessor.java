package com.nagham.player;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;

/**
 * Plugs the {@link BtDsp} sound engine into ExoPlayer's audio pipeline, right before the AudioTrack: the music is
 * decoded, measured, equalised, compressed and limited as 32-bit float, then converted back to 16-bit with TPDF
 * dither (no truncation distortion) and handed to Android, which encodes it for the Bluetooth headset.
 * When tuning is off the audio is copied through untouched.
 */
@OptIn(markerClass = UnstableApi.class)
public final class BtDspProcessor extends BaseAudioProcessor {
    public static final BtDspProcessor INSTANCE = new BtDspProcessor();

    private final BtDsp dsp = new BtDsp();
    private boolean isFloat, wasActive;
    private int channels = 2;
    private float[] work = new float[0];
    private int seed = 0x9E3779B9;

    private BtDspProcessor() {
    }

    public BtDsp.Stats stats() {
        return dsp.stats;
    }

    public void setParams(BtDsp.Params p) {
        dsp.setParams(p);
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat in) throws UnhandledAudioFormatException {
        boolean pcm16 = in.encoding == C.ENCODING_PCM_16BIT, pcmF = in.encoding == C.ENCODING_PCM_FLOAT;
        if ((!pcm16 && !pcmF) || in.channelCount < 1 || in.channelCount > 2 || in.sampleRate < 8000) {
            throw new UnhandledAudioFormatException(in);   // anything unusual just plays untouched
        }
        isFloat = pcmF;
        channels = in.channelCount;
        dsp.configure(in.sampleRate, channels);
        wasActive = false;
        return in;
    }

    @Override
    public void queueInput(ByteBuffer in) {
        int bytes = in.remaining();
        if (bytes == 0) return;
        if (!dsp.enabled()) {
            wasActive = false;
            ByteBuffer out = replaceOutputBuffer(bytes);
            out.put(in);
            out.flip();
            return;
        }
        if (!wasActive) {
            dsp.reset();
            wasActive = true;
        }
        int per = isFloat ? 4 : 2;
        int frames = bytes / (per * channels);
        int n = frames * channels;
        if (work.length < n) work = new float[n];
        if (isFloat) for (int i = 0; i < n; i++) work[i] = in.getFloat();
        else for (int i = 0; i < n; i++) work[i] = in.getShort() * (1f / 32768f);
        int rest = bytes - n * per;
        ByteBuffer tail = null;
        if (rest > 0) {                      // never happens with whole frames; keep any stray bytes
            tail = ByteBuffer.allocate(rest);
            tail.put(in);
            tail.flip();
        }
        dsp.process(work, frames);
        ByteBuffer out = replaceOutputBuffer(n * per + rest);
        write(out, work, n);
        if (tail != null) out.put(tail);
        out.flip();
    }

    @Override
    protected void onQueueEndOfStream() {
        // push the limiter's look-ahead (about 1.5 ms) out so the last samples of a song are not lost
        if (!wasActive || !dsp.enabled()) return;
        int frames = dsp.latency();
        if (frames <= 0) return;
        int n = frames * channels;
        if (work.length < n) work = new float[n];
        java.util.Arrays.fill(work, 0, n, 0f);
        dsp.process(work, frames);
        ByteBuffer out = replaceOutputBuffer(n * (isFloat ? 4 : 2));
        write(out, work, n);
        out.flip();
    }

    private void write(ByteBuffer out, float[] x, int n) {
        if (isFloat) {
            for (int i = 0; i < n; i++) out.putFloat(Math.max(-1f, Math.min(1f, x[i])));
        } else {
            for (int i = 0; i < n; i++) {
                float v = x[i] * 32767f + rnd() + rnd() - 1f;   // TPDF dither, +/- 1 LSB
                int q = Math.round(v);
                out.putShort((short) (q > 32767 ? 32767 : (q < -32768 ? -32768 : q)));
            }
        }
    }

    private float rnd() {
        seed ^= seed << 13;
        seed ^= seed >>> 17;
        seed ^= seed << 5;
        return (seed >>> 8) * (1f / 16777216f);
    }

    // No @Override on purpose: media3 renamed / overloaded these hooks between versions, and either spelling works.
    protected void onFlush() {
        dsp.reset();
        wasActive = false;
    }

    @Override
    protected void onReset() {
        dsp.reset();
        wasActive = false;
        work = new float[0];
    }
}
