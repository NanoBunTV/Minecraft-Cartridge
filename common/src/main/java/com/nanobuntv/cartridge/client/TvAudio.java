package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.Cartridge;
import java.nio.ShortBuffer;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.system.MemoryUtil;

/**
 * One console's sound (D23): a streaming OpenAL source in Minecraft's own audio context, so the master volume
 * (Minecraft's listener gain) and the listener position apply to it. The NES is mono, so the stereo stream is mixed
 * down to mono, which OpenAL can place in 3D; the source sits at the linked TV nearest the listener and fades over
 * the spectator radius like vanilla sounds. In play mode it can be made un-attenuated (D63, off by default).
 * <p>
 * Every AL call happens on the console's emulator thread ({@link #push}); the render thread only sets the volatile
 * placement fields. The AL context is current process-wide, so that's allowed. If Minecraft rebuilds its sound engine
 * (device change, resource reload) the source disappears and is recreated.
 */
final class TvAudio {
    private static final int START_BUFFERS = 3; // ~50 ms queued before playback (re)starts
    private static final int MAX_BUFFERS = 10;  // beyond ~170 ms, drop audio to catch up
    private static final int AL_SOURCE_DISTANCE_MODEL = 0xD000, AL_LINEAR_DISTANCE = 0xD003;

    private volatile float x, y, z, gain, maxDistance = 32;
    private volatile boolean relative;
    private volatile boolean paused;

    private int source;
    /** What the last {@link #push} saw, for the dev self-test. */
    volatile int pushes, plays, lastState, lastQueued;
    private ShortBuffer mono = MemoryUtil.memAllocShort(4096);

    /** Render thread: where and how loud. A gain of 0 silences it (no TV linked). */
    void place(double x, double y, double z, float gain, float maxDistance, boolean relative) {
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        this.gain = gain;
        this.maxDistance = maxDistance;
        this.relative = relative;
    }

    void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** Emulator thread: queues {@code frames} stereo frames (interleaved L, R). */
    void push(short[] stereo, int frames, double sampleRate) {
        if (frames <= 0 || !ensureSource()) return;
        try {
            recycle();
            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) {
                // It ran dry (the game paused or stalled). A stopped source counts every buffer queued on it as
                // already played, so new ones would be recycled before reaching START_BUFFERS: rewind it to INITIAL.
                AL10.alSourceRewind(source);
            }
            int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
            if (queued < MAX_BUFFERS) {
                if (mono.capacity() < frames) {
                    MemoryUtil.memFree(mono);
                    mono = MemoryUtil.memAllocShort(frames);
                }
                mono.clear();
                for (int i = 0; i < frames; i++) mono.put((short) ((stereo[i * 2] + stereo[i * 2 + 1]) / 2));
                mono.flip();
                int buffer = AL10.alGenBuffers();
                AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, mono, (int) Math.round(sampleRate));
                AL10.alSourceQueueBuffers(source, buffer);
                queued++;
            }
            applyPlacement();
            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            if (paused) {
                if (state == AL10.AL_PLAYING) AL10.alSourcePause(source);
            } else if (state != AL10.AL_PLAYING && queued >= START_BUFFERS) {
                AL10.alSourcePlay(source);
                plays++;
            }
            pushes++;
            lastState = state;
            lastQueued = queued;
        } catch (RuntimeException e) {
            Cartridge.LOGGER.debug("TV audio push failed", e);
        }
    }

    /** Emulator thread: keeps the paused state current while no frames run. */
    void idle() {
        if (source == 0 || ALC10.alcGetCurrentContext() == 0L || !AL10.alIsSource(source)) return;
        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (paused && state == AL10.AL_PLAYING) AL10.alSourcePause(source);
    }

    /** Emulator thread, when the console stops. */
    /** For the dev self-test. */
    String describe() {
        String state = switch (lastState) {
            case AL10.AL_PLAYING -> "playing";
            case AL10.AL_PAUSED -> "paused";
            case AL10.AL_STOPPED -> "stopped";
            case AL10.AL_INITIAL -> "initial";
            default -> String.valueOf(lastState);
        };
        return "pushes " + pushes + ", plays " + plays + ", last state " + state + ", queued " + lastQueued + ", gain " + gain
                + (paused ? ", PAUSED" : "");
    }

    void close() {
        try {
            if (source != 0 && ALC10.alcGetCurrentContext() != 0L && AL10.alIsSource(source)) {
                AL10.alSourceStop(source);
                recycle();
                AL10.alDeleteSources(source);
            }
        } catch (RuntimeException e) {
            Cartridge.LOGGER.debug("TV audio close failed", e);
        }
        source = 0;
        MemoryUtil.memFree(mono);
        mono = MemoryUtil.memAllocShort(0);
    }

    private boolean ensureSource() {
        if (ALC10.alcGetCurrentContext() == 0L) return false;
        if (source != 0 && AL10.alIsSource(source)) return true;
        AL10.alGetError();
        source = AL10.alGenSources();
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            source = 0;
            return false;
        }
        AL10.alSourcef(source, AL10.AL_PITCH, 1.0F);
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
        return true;
    }

    private void recycle() {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(source));
    }

    private void applyPlacement() {
        AL10.alSourcef(source, AL10.AL_GAIN, gain);
        if (relative) {
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
            AL10.alSourcei(source, AL_SOURCE_DISTANCE_MODEL, AL10.AL_NONE);
        } else {
            // As vanilla's Channel.linearAttenuation: full volume at the TV, silent at maxDistance.
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
            AL10.alSource3f(source, AL10.AL_POSITION, x, y, z);
            AL10.alSourcei(source, AL_SOURCE_DISTANCE_MODEL, AL_LINEAR_DISTANCE);
            AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, maxDistance);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0F);
            AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 0.0F);
        }
    }
}
