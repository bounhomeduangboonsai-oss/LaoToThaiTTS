package com.laothai.tts;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class SettingsActivity extends Activity {

    private TextToSpeech tester;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        final RadioGroup group = (RadioGroup) findViewById(R.id.engineGroup);
        String saved = Prefs.get(this).getString(Prefs.KEY_ENGINE, "");
        addRadio(group, getString(R.string.engine_auto), "", saved.isEmpty());
        boolean any = false;
        for (String[] e : Prefs.listEngines(this)) {
            addRadio(group, e[1], e[0], e[0].equals(saved));
            any = true;
        }
        if (group.getCheckedRadioButtonId() == -1) {
            ((RadioButton) group.getChildAt(0)).setChecked(true);
        }
        if (!any) {
            Toast.makeText(this, R.string.no_engines, Toast.LENGTH_LONG).show();
            ((TextView) findViewById(R.id.engineLabel)).append("\n" + getString(R.string.no_engines));
        }
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                View rb = g.findViewById(checkedId);
                if (rb != null && rb.getTag() != null) {
                    Prefs.get(SettingsActivity.this).edit()
                            .putString(Prefs.KEY_ENGINE, (String) rb.getTag()).apply();
                }
            }
        });

        CheckBox convert = (CheckBox) findViewById(R.id.convertCheck);
        convert.setChecked(Prefs.get(this).getBoolean(Prefs.KEY_CONVERT, true));
        convert.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Prefs.get(SettingsActivity.this).edit().putBoolean(Prefs.KEY_CONVERT, checked).apply();
            }
        });

        ((Button) findViewById(R.id.testButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTest();
            }
        });

        ((Button) findViewById(R.id.openTtsButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                } catch (ActivityNotFoundException e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            }
        });
    }

    private void addRadio(RadioGroup group, String label, String pkg, boolean checked) {
        RadioButton rb = new RadioButton(this);
        rb.setId(View.generateViewId());
        rb.setText(label);
        rb.setTag(pkg);
        rb.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
        group.addView(rb);
        if (checked) {
            rb.setChecked(true);
        }
    }

    /** Speaks a Lao sentence through this app's own engine, exactly like TalkBack would. */
    private void runTest() {
        if (tester != null) {
            tester.shutdown();
            tester = null;
        }
        tester = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS || tester == null) {
                    Toast.makeText(SettingsActivity.this, R.string.test_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                int r = tester.setLanguage(new Locale("lo", "LA"));
                if (r < 0) {
                    Toast.makeText(SettingsActivity.this, R.string.test_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                tester.speak(getString(R.string.test_sample), TextToSpeech.QUEUE_FLUSH, null, "test");
            }
        }, getPackageName());
    }

    @Override
    protected void onDestroy() {
        if (tester != null) {
            tester.shutdown();
            tester = null;
        }
        super.onDestroy();
    }
}
