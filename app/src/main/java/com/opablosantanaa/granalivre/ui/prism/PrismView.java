package com.opablosantanaa.granalivre.ui.prism;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.provider.Settings;
import android.util.AttributeSet;

import com.opablosantanaa.granalivre.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PrismView extends GLSurfaceView {
    private static final float INTRO_SECONDS = 1.4f;
    private static final int PALETTE_SIZE = 64;
    private static final float[] HUE_W = {0.299f, 0.587f, 0.114f, 0.299f, 0.587f, 0.114f, 0.299f, 0.587f, 0.114f};
    private static final float[] HUE_U = {0.701f, -0.587f, -0.114f, -0.299f, 0.413f, -0.114f, -0.3f, -0.588f, 0.886f};
    private static final float[] HUE_V = {0.168f, -0.331f, 0.5f, 0.328f, 0.035f, -0.5f, -0.497f, 0.296f, 0.201f};

    public static final class Style {
        public float height = 3.5f;
        public float baseWidth = 5.5f;
        public float glow = 1f;
        public float offsetX = 0f;
        public float offsetY = 0f;
        public float noise = 0f;
        public float scale = 3.6f;
        public float hueShift = 0f;
        public float colorFrequency = 1f;
        public int[] colors = new int[0];
        public float saturation = 1.5f;
        public float timeScale = 0.5f;
        public boolean lightMode = false;
        public int backdrop = Color.BLACK;

        public Style copy() {
            Style s = new Style();
            s.height = height;
            s.baseWidth = baseWidth;
            s.glow = glow;
            s.offsetX = offsetX;
            s.offsetY = offsetY;
            s.noise = noise;
            s.scale = scale;
            s.hueShift = hueShift;
            s.colorFrequency = colorFrequency;
            s.colors = colors.clone();
            s.saturation = saturation;
            s.timeScale = timeScale;
            s.lightMode = lightMode;
            s.backdrop = backdrop;
            return s;
        }
    }

    private static final long FRAME_MS = 33;

    private final boolean reduceMotion;
    private final float resolution;
    private volatile Style style = new Style();
    private boolean resumed;
    private boolean paused;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!resumed || paused || reduceMotion) return;
            requestRender();
            postDelayed(this, FRAME_MS);
        }
    };

    public PrismView(Context context) {
        this(context, null);
    }

    public PrismView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float animatorScale = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        reduceMotion = animatorScale == 0f;
        resolution = 0.4f;
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(new PrismRenderer(context));
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setStyle(Style next) {
        style = next.copy();
        requestRender();
    }

    public void setPaused(boolean value) {
        if (paused == value) return;
        paused = value;
        restartTicks();
    }

    private void restartTicks() {
        removeCallbacks(tick);
        if (resumed && !paused && !reduceMotion) post(tick);
    }

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        restartTicks();
    }

    @Override
    public void onPause() {
        resumed = false;
        removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        getHolder().setFixedSize(Math.max(1, Math.round(w * resolution)), Math.max(1, Math.round(h * resolution)));
    }

    private static String readRaw(Context context, int id) {
        try (InputStream in = context.getResources().openRawResource(id)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static float[] rotation(float yaw, float pitch, float roll) {
        float cy = (float) Math.cos(yaw);
        float sy = (float) Math.sin(yaw);
        float cx = (float) Math.cos(pitch);
        float sx = (float) Math.sin(pitch);
        float cz = (float) Math.cos(roll);
        float sz = (float) Math.sin(roll);
        return new float[]{
                cy * cz + sy * sx * sz, cx * sz, -sy * cz + cy * sx * sz,
                -cy * sz + sy * sx * cz, cx * cz, sy * sz + cy * sx * cz,
                sy * cx, -sx, cy * cx};
    }

    private static float[] multiply(float[] a, float[] b) {
        float[] out = new float[9];
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                out[c * 3 + r] = a[r] * b[c * 3] + a[3 + r] * b[c * 3 + 1] + a[6 + r] * b[c * 3 + 2];
            }
        }
        return out;
    }

    private final class PrismRenderer implements Renderer {
        private final String vertexSource;
        private final String fragmentSource;
        private final Map<String, Integer> uniforms = new HashMap<>();
        private int program;
        private int vao;
        private int palette;
        private int[] paletteKey;
        private float neutral;
        private int width = 1;
        private int height = 1;
        private float time;
        private float introClock;
        private long last;

        PrismRenderer(Context context) {
            vertexSource = readRaw(context, R.raw.prism_vertex);
            fragmentSource = readRaw(context, R.raw.prism_fragment);
        }

        private int u(String name) {
            Integer location = uniforms.get(name);
            if (location == null) {
                location = GLES30.glGetUniformLocation(program, name);
                uniforms.put(name, location);
            }
            return location;
        }

        private int compile(int type, String source) {
            int shader = GLES30.glCreateShader(type);
            if (shader == 0) return 0;
            GLES30.glShaderSource(shader, source);
            GLES30.glCompileShader(shader);
            int[] status = new int[1];
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
            if (status[0] != 0) return shader;
            GLES30.glDeleteShader(shader);
            return 0;
        }

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            uniforms.clear();
            int vertex = compile(GLES30.GL_VERTEX_SHADER, vertexSource);
            int fragment = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
            program = GLES30.glCreateProgram();
            if (vertex != 0 && fragment != 0) {
                GLES30.glAttachShader(program, vertex);
                GLES30.glAttachShader(program, fragment);
                GLES30.glLinkProgram(program);
                int[] status = new int[1];
                GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
                if (status[0] == 0) {
                    GLES30.glDeleteProgram(program);
                    program = 0;
                }
            } else {
                program = 0;
            }
            if (vertex != 0) GLES30.glDeleteShader(vertex);
            if (fragment != 0) GLES30.glDeleteShader(fragment);

            int[] ids = new int[1];
            GLES30.glGenVertexArrays(1, ids, 0);
            vao = ids[0];
            GLES30.glGenTextures(1, ids, 0);
            palette = ids[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, palette);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, PALETTE_SIZE, 1, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
            paletteKey = null;
            introClock = reduceMotion ? 1f : 0f;
            last = 0;
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int w, int h) {
            width = Math.max(1, w);
            height = Math.max(1, h);
        }

        private void syncPalette(int[] colors) {
            if (Arrays.equals(colors, paletteKey)) return;
            paletteKey = colors.clone();
            neutral = 0;
            if (colors.length == 0) return;
            float chroma = 0;
            for (int c : colors) {
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                int top = Math.max(r, Math.max(g, b));
                chroma += top > 0 ? (top - Math.min(r, Math.min(g, b))) / (float) top : 0;
            }
            neutral = 1 - chroma / colors.length;
            ByteBuffer data = ByteBuffer.allocateDirect(PALETTE_SIZE * 4);
            for (int i = 0; i < PALETTE_SIZE; i++) {
                float position = (float) i / PALETTE_SIZE * colors.length;
                int index = (int) Math.floor(position);
                int a = colors[index % colors.length];
                int b = colors[(index + 1) % colors.length];
                float t = position - index;
                float k = t * t * (3 - 2 * t);
                data.put((byte) Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * k));
                data.put((byte) Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * k));
                data.put((byte) Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k));
                data.put((byte) 255);
            }
            data.position(0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, palette);
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, PALETTE_SIZE, 1,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, data);
        }

        @Override
        public void onDrawFrame(GL10 unused) {
            Style s = style;
            long now = System.nanoTime();
            float dt = last != 0 ? Math.max(0.001f, Math.min(0.05f, (now - last) / 1e9f)) : 1f / 60f;
            last = now;
            if (!reduceMotion) time += dt;
            if (introClock < 1f) introClock = Math.min(1f, introClock + dt / INTRO_SECONDS);

            float[] back = {Color.red(s.backdrop) / 255f, Color.green(s.backdrop) / 255f, Color.blue(s.backdrop) / 255f};
            GLES30.glViewport(0, 0, width, height);
            GLES30.glClearColor(back[0], back[1], back[2], 1f);
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            if (program == 0) return;
            syncPalette(s.colors);

            float t = time * Math.max(0, s.timeScale);
            float c0 = (float) Math.cos(t);
            float c1 = (float) Math.cos(t + 33);
            float c2 = (float) Math.cos(t + 11);
            float[] ray = multiply(rotation(0, 0, 0), new float[]{c0, 0, c2, 0, 1, 0, c1, 0, c0});

            float half = Math.max(0.1f, s.baseWidth) * 0.5f;
            float h = Math.max(0.1f, s.height);
            float ignite = introClock * introClock * (3 - 2 * introClock);
            float hue = (float) Math.toRadians(s.hueShift);
            float hc = (float) Math.cos(hue);
            float hs = (float) Math.sin(hue);
            float[] hueMatrix = new float[9];
            for (int i = 0; i < 9; i++) hueMatrix[i] = HUE_W[i] + HUE_U[i] * hc + HUE_V[i] * hs;
            float pxPerDp = getResources().getDisplayMetrics().density * resolution;

            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            GLES30.glBindVertexArray(vao);
            GLES30.glUseProgram(program);
            GLES30.glUniform2f(u("uResolution"), width, height);
            GLES30.glUniformMatrix3fv(u("uRay"), 1, false, ray, 0);
            GLES30.glUniform1f(u("uGlow"), Math.max(0, s.glow) * ignite * ignite);
            GLES30.glUniform2f(u("uOffset"), s.offsetX * pxPerDp, -s.offsetY * pxPerDp);
            GLES30.glUniform1f(u("uColorFreq"), Math.max(0, s.colorFrequency));
            GLES30.glUniform1f(u("uCenterShift"), h * 0.25f);
            GLES30.glUniform1f(u("uInvBaseHalf"), 1 / half);
            GLES30.glUniform1f(u("uInvHeight"), 1 / h);
            GLES30.glUniform1f(u("uMinAxis"), Math.min(half, h));
            GLES30.glUniform1f(u("uPxScale"), 1 / (height * 0.1f * Math.max(0.1f, s.scale)));
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, palette);
            GLES30.glUniform1i(u("uPalette"), 0);
            GLES30.glUniform1i(u("uUsePalette"), s.colors.length > 0 ? 1 : 0);
            GLES30.glUniform1f(u("uTime"), time);
            GLES30.glUniform1f(u("uNoise"), Math.max(0, s.noise));
            GLES30.glUniform1f(u("uSaturation"), Math.max(0, s.saturation));
            GLES30.glUniformMatrix3fv(u("uHue"), 1, false, hueMatrix, 0);
            GLES30.glUniform1i(u("uUseHue"), Math.abs(hue) > 0.0001f ? 1 : 0);
            GLES30.glUniform1f(u("uLight"), s.lightMode ? 1 : 0);
            GLES30.glUniform1f(u("uNeutral"), neutral);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        }
    }
}
