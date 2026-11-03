package com.laothai.tts;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.List;

/** Settings shared by the settings screen and the speech service. */
final class Prefs {
    static final String KEY_ENGINE = "engine";   // package name of the real voice engine, "" = automatic
    static final String KEY_CONVERT = "convert"; // convert Lao to Thai (true by default)
    private static final String GOOGLE_TTS = "com.google.android.tts";

    private Prefs() {
    }

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** Other installed speech engines as {packageName, label}; this app is left out. */
    static List<String[]> listEngines(Context c) {
        List<String[]> result = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> services = pm.queryIntentServices(
                new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0);
        for (ResolveInfo info : services) {
            if (info.serviceInfo == null) {
                continue;
            }
            String pkg = info.serviceInfo.packageName;
            if (pkg.equals(c.getPackageName())) {
                continue;
            }
            CharSequence label = info.loadLabel(pm);
            result.add(new String[]{pkg, label == null ? pkg : label.toString()});
        }
        return result;
    }

    /** The engine that really speaks: the saved choice, else Google, else the first one found. */
    static String chooseEngine(Context c) {
        List<String[]> engines = listEngines(c);
        String saved = get(c).getString(KEY_ENGINE, "");
        if (!saved.isEmpty()) {
            for (String[] e : engines) {
                if (e[0].equals(saved)) {
                    return saved;
                }
            }
        }
        for (String[] e : engines) {
            if (e[0].equals(GOOGLE_TTS)) {
                return GOOGLE_TTS;
            }
        }
        return engines.isEmpty() ? null : engines.get(0)[0];
    }
}
