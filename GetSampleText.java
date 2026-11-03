package com.laothai.tts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

/** Gives Android's voice settings a sample sentence to play. */
public class GetSampleText extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String lang = getIntent() == null ? null : getIntent().getStringExtra("language");
        boolean lao = "lao".equalsIgnoreCase(lang) || "lo".equalsIgnoreCase(lang);
        Intent result = new Intent();
        result.putExtra("sampleText", lao
                ? getString(R.string.test_sample)
                : "This is an example of speech synthesis.");
        setResult(TextToSpeech.LANG_AVAILABLE, result);
        finish();
    }
}
