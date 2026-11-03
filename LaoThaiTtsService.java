package com.laothai.tts;

import android.media.AudioFormat;
import android.os.Bundle;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A "bridge" speech engine. TalkBack (or any app) sends text here; Lao text is converted to
 * Thai script and spoken by a real Thai-capable engine installed on the phone (e.g. Google).
 */
public class LaoThaiTtsService extends TextToSpeechService {

    private static final String TAG = "LaoThaiTts";
    private static final int MAX_CHUNK = 3500;
    private static final Locale THAI = new Locale("th", "TH");

    private volatile LaoToThai converter;

    // The real engine we forward to.
    private final Object delegateLock = new Object();
    private TextToSpeech delegate;
    private String delegatePackage;
    private CountDownLatch initLatch;
    private boolean delegateOk;

    // State of the utterance currently being synthesised by the real engine.
    private final Object utterLock = new Object();
    private String currentUtterance;
    private CountDownLatch utterDone;
    private boolean utterOk;
    private volatile boolean stopped;

    private volatile String loadedLang = "eng";
    private volatile String loadedCountry = "";
    private volatile String loadedVariant = "";

    private final UtteranceProgressListener listener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
        }

        @Override
        public void onDone(String utteranceId) {
            finishUtterance(utteranceId, true);
        }

        @Override
        public void onError(String utteranceId) {
            finishUtterance(utteranceId, false);
        }

        @Override
        public void onError(String utteranceId, int errorCode) {
            finishUtterance(utteranceId, false);
        }

        @Override
        public void onStop(String utteranceId, boolean interrupted) {
            finishUtterance(utteranceId, false);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try (InputStream in = getAssets().open("lao_thai.tsv")) {
            converter = LaoToThai.fromStream(in);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Cannot load dictionary", e);
        }
        // Start the real engine early so the first sentence is not delayed.
        new Thread(new Runnable() {
            @Override
            public void run() {
                getDelegate(10000);
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        synchronized (delegateLock) {
            if (delegate != null) {
                try {
                    delegate.shutdown();
                } catch (RuntimeException ignored) {
                }
                delegate = null;
                delegatePackage = null;
                delegateOk = false;
            }
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ real engine

    private TextToSpeech getDelegate(long timeoutMs) {
        CountDownLatch latch;
        synchronized (delegateLock) {
            String wanted = Prefs.chooseEngine(this);
            if (wanted == null) {
                return null;
            }
            if (delegate == null || !wanted.equals(delegatePackage)) {
                if (delegate != null) {
                    try {
                        delegate.shutdown();
                    } catch (RuntimeException ignored) {
                    }
                    delegate = null;
                }
                delegatePackage = wanted;
                delegateOk = false;
                final CountDownLatch l = new CountDownLatch(1);
                initLatch = l;
                delegate = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        onDelegateInit(l, status);
                    }
                }, wanted);
            }
            latch = initLatch;
        }
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        synchronized (delegateLock) {
            return delegateOk ? delegate : null;
        }
    }

    private void onDelegateInit(CountDownLatch latch, int status) {
        synchronized (delegateLock) {
            if (latch == initLatch && delegate != null) {
                if (status == TextToSpeech.SUCCESS) {
                    delegateOk = true;
                    delegate.setOnUtteranceProgressListener(listener);
                } else {
                    Log.e(TAG, "Real speech engine failed to start: " + delegatePackage);
                    try {
                        delegate.shutdown();
                    } catch (RuntimeException ignored) {
                    }
                    delegate = null;
                    delegatePackage = null;
                    delegateOk = false;
                }
            }
        }
        latch.countDown();
    }

    private void finishUtterance(String id, boolean ok) {
        synchronized (utterLock) {
            if (id != null && id.equals(currentUtterance) && utterDone != null) {
                utterOk = ok;
                utterDone.countDown();
            }
        }
    }

    // ------------------------------------------------------------------ languages

    private static boolean isLaoLang(String lang) {
        return "lao".equalsIgnoreCase(lang) || "lo".equalsIgnoreCase(lang);
    }

    /** Converts the 3-letter codes that Android hands to engines into a normal Locale. */
    private static Locale toLocale(String lang, String country, String variant) {
        String l = lang == null ? "" : lang;
        String c = country == null ? "" : country;
        String v = variant == null ? "" : variant;
        if (l.length() == 3) {
            for (String iso : Locale.getISOLanguages()) {
                try {
                    if (new Locale(iso).getISO3Language().equalsIgnoreCase(l)) {
                        l = iso;
                        break;
                    }
                } catch (MissingResourceException ignored) {
                }
            }
        }
        if (c.length() == 3) {
            for (String iso : Locale.getISOCountries()) {
                try {
                    if (new Locale("", iso).getISO3Country().equalsIgnoreCase(c)) {
                        c = iso;
                        break;
                    }
                } catch (MissingResourceException ignored) {
                }
            }
        }
        return new Locale(l, c, v);
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        if (isLaoLang(lang)) {
            return (country == null || country.isEmpty())
                    ? TextToSpeech.LANG_AVAILABLE : TextToSpeech.LANG_COUNTRY_AVAILABLE;
        }
        TextToSpeech d = getDelegate(8000);
        if (d == null) {
            return TextToSpeech.LANG_NOT_SUPPORTED;
        }
        try {
            return d.isLanguageAvailable(toLocale(lang, country, variant));
        } catch (RuntimeException e) {
            return TextToSpeech.LANG_NOT_SUPPORTED;
        }
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[]{loadedLang, loadedCountry, loadedVariant};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        int result = onIsLanguageAvailable(lang, country, variant);
        if (result >= TextToSpeech.LANG_AVAILABLE) {
            loadedLang = lang == null ? "" : lang;
            loadedCountry = country == null ? "" : country;
            loadedVariant = variant == null ? "" : variant;
        }
        return result;
    }

    // ------------------------------------------------------------------ speaking

    @Override
    protected void onStop() {
        stopped = true;
        synchronized (utterLock) {
            if (utterDone != null) {
                utterDone.countDown();
            }
        }
        TextToSpeech d;
        synchronized (delegateLock) {
            d = delegate;
        }
        if (d != null) {
            try {
                d.stop();
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        stopped = false;
        CharSequence cs = request.getCharSequenceText();
        String text = cs == null ? "" : cs.toString();

        boolean convertOn = Prefs.get(this).getBoolean(Prefs.KEY_CONVERT, true);
        boolean hasLao = LaoToThai.hasLao(text);
        boolean useThai = isLaoLang(request.getLanguage()) || (convertOn && hasLao);
        LaoToThai conv = converter;
        if (convertOn && hasLao && conv != null) {
            text = conv.convert(text);
        }
        Locale target = useThai
                ? THAI
                : toLocale(request.getLanguage(), request.getCountry(), request.getVariant());

        List<String> chunks = new ArrayList<>();
        for (String chunk : split(text, MAX_CHUNK)) {
            if (hasLetterOrDigit(chunk)) {
                chunks.add(chunk);
            }
        }

        boolean[] started = new boolean[1];
        if (!chunks.isEmpty()) {
            TextToSpeech tts = getDelegate(15000);
            if (tts == null) {
                Log.e(TAG, "No real speech engine available");
                callback.error();
                return;
            }
            int lr = tts.setLanguage(target);
            if (lr == TextToSpeech.LANG_MISSING_DATA || lr == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Real engine cannot speak " + target + " (install its Thai voice)");
                callback.error();
                return;
            }
            tts.setSpeechRate(Math.max(0.1f, request.getSpeechRate() / 100f));
            tts.setPitch(Math.max(0.1f, request.getPitch() / 100f));

            for (String chunk : chunks) {
                if (stopped) {
                    return;
                }
                File wav = null;
                try {
                    wav = File.createTempFile("syn", ".wav", getCacheDir());
                    if (!synthesizeChunk(tts, chunk, wav)) {
                        if (!stopped) {
                            callback.error();
                        }
                        return;
                    }
                    if (!streamWav(wav, callback, started)) {
                        if (!stopped) {
                            callback.error();
                        }
                        return;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Temp file problem", e);
                    callback.error();
                    return;
                } finally {
                    if (wav != null) {
                        //noinspection ResultOfMethodCallIgnored
                        wav.delete();
                    }
                }
            }
        }
        if (stopped) {
            return;
        }
        if (!started[0]) {
            // nothing to say (empty or punctuation only): finish with a silent utterance
            callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1);
        }
        callback.done();
    }

    private boolean synthesizeChunk(TextToSpeech tts, String chunk, File out) {
        String id = "lt" + System.nanoTime();
        CountDownLatch latch = new CountDownLatch(1);
        synchronized (utterLock) {
            currentUtterance = id;
            utterDone = latch;
            utterOk = false;
        }
        int rc = tts.synthesizeToFile(chunk, new Bundle(), out, id);
        if (rc != TextToSpeech.SUCCESS) {
            return false;
        }
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                tts.stop();
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        synchronized (utterLock) {
            return utterOk && !stopped;
        }
    }

    // ------------------------------------------------------------------ audio

    private boolean streamWav(File f, SynthesisCallback cb, boolean[] started) {
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            long len = in.length();
            byte[] head = new byte[12];
            in.readFully(head);
            if (!tag(head, 0, "RIFF") || !tag(head, 8, "WAVE")) {
                return false;
            }
            int channels = 1;
            int rate = 22050;
            int bits = 16;
            boolean haveFormat = false;
            byte[] ch = new byte[8];
            while (in.getFilePointer() + 8 <= len) {
                in.readFully(ch);
                long size = le32(ch, 4) & 0xFFFFFFFFL;
                long start = in.getFilePointer();
                if (tag(ch, 0, "fmt ")) {
                    byte[] fmt = new byte[(int) Math.min(size, 40)];
                    in.readFully(fmt);
                    if (fmt.length >= 16) {
                        channels = le16(fmt, 2);
                        rate = le32(fmt, 4);
                        bits = le16(fmt, 14);
                        haveFormat = true;
                    }
                    in.seek(start + size + (size & 1));
                } else if (tag(ch, 0, "data")) {
                    if (!haveFormat || bits != 16 || channels < 1 || channels > 2) {
                        Log.e(TAG, "Unsupported audio from real engine");
                        return false;
                    }
                    long remaining = len - start;
                    if (size == 0 || size > remaining) {
                        size = remaining; // streamed WAV files may have a wrong size
                    }
                    if (!started[0]) {
                        if (cb.start(rate, AudioFormat.ENCODING_PCM_16BIT, channels) != TextToSpeech.SUCCESS) {
                            return false;
                        }
                        started[0] = true;
                    }
                    int bufSize = Math.max(4, (Math.min(cb.getMaxBufferSize(), 8192) / 4) * 4);
                    byte[] buf = new byte[bufSize];
                    long left = size;
                    while (left > 0 && !stopped) {
                        int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                        if (n <= 0) {
                            break;
                        }
                        if (cb.audioAvailable(buf, 0, n) != TextToSpeech.SUCCESS) {
                            return false;
                        }
                        left -= n;
                    }
                    return true;
                } else {
                    in.seek(start + size + (size & 1));
                }
            }
            return false;
        } catch (IOException e) {
            Log.e(TAG, "Cannot read audio", e);
            return false;
        }
    }

    private static boolean tag(byte[] b, int off, String t) {
        for (int i = 0; i < 4; i++) {
            if (b[off + i] != (byte) t.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int le16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16)
                | ((b[off + 3] & 0xFF) << 24);
    }

    // ------------------------------------------------------------------ text helpers

    private static boolean hasLetterOrDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetterOrDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Cuts long text into pieces the real engine accepts, preferring to cut at spaces. */
    static List<String> split(String text, int max) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            int end = Math.min(n, i + max);
            if (end < n) {
                int cut = end;
                while (cut > i + max / 2 && !Character.isWhitespace(text.charAt(cut - 1))) {
                    cut--;
                }
                if (cut > i + max / 2) {
                    end = cut;
                }
            }
            parts.add(text.substring(i, end));
            i = end;
        }
        if (parts.isEmpty()) {
            parts.add("");
        }
        return parts;
    }
}
