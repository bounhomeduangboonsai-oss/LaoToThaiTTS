package com.laothai.tts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lao -> Thai text conversion (port of engine.py from the NVDA add-on).
 * Pure Java, no Android classes, so it can be tested on its own.
 *
 * 1. Multi-character entries of lao_thai.tsv are applied in file order.
 * 2. Whatever Lao is left is converted letter by letter.
 * A text that is only one letter/mark uses its single-character dictionary entry.
 */
public final class LaoToThai {

    private static final Map<String, String> CONS = new HashMap<>();
    private static final Map<String, String> OTHER = new HashMap<>();
    private static final String SILENT_H_BEFORE = "ງຍນມລວຣ";

    static {
        CONS.put("ກ", "ก");
        CONS.put("ຂ", "ข");
        CONS.put("ຄ", "ค");
        CONS.put("ງ", "ง");
        CONS.put("ຈ", "จ");
        CONS.put("ສ", "ส");
        CONS.put("ຊ", "ซ");
        CONS.put("ຍ", "ย");
        CONS.put("ດ", "ด");
        CONS.put("ຕ", "ต");
        CONS.put("ຖ", "ถ");
        CONS.put("ທ", "ท");
        CONS.put("ນ", "น");
        CONS.put("ບ", "บ");
        CONS.put("ປ", "ป");
        CONS.put("ຜ", "ผ");
        CONS.put("ຝ", "ฝ");
        CONS.put("ພ", "พ");
        CONS.put("ຟ", "ฟ");
        CONS.put("ມ", "ม");
        CONS.put("ຢ", "ย");
        CONS.put("ຣ", "ร");
        CONS.put("ລ", "ล");
        CONS.put("ວ", "ว");
        CONS.put("ຫ", "ห");
        CONS.put("ອ", "อ");
        CONS.put("ຮ", "ฮ");
        CONS.put("ໜ", "หน");
        CONS.put("ໝ", "หม");

        OTHER.put("ະ", "ะ");
        OTHER.put("າ", "า");
        OTHER.put("ິ", "ิ");
        OTHER.put("ີ", "ี");
        OTHER.put("ຶ", "ึ");
        OTHER.put("ື", "ื");
        OTHER.put("ຸ", "ุ");
        OTHER.put("ູ", "ู");
        OTHER.put("ຳ", "ำ");
        OTHER.put("ເ", "เ");
        OTHER.put("ແ", "แ");
        OTHER.put("ໂ", "โ");
        OTHER.put("ໃ", "ใ");
        OTHER.put("ໄ", "ไ");
        OTHER.put("ັ", "ั");
        OTHER.put("່", "่");
        OTHER.put("້", "้");
        OTHER.put("໊", "๊");
        OTHER.put("໋", "๋");
        OTHER.put("ຽ", "ย");
        OTHER.put("ົ", "");
        OTHER.put("ໍ", "อ");
        OTHER.put("ໆ", "ๆ");
        OTHER.put("ຼ", "");
        OTHER.put("ຯ", "ฯ");
        for (int i = 0; i < 10; i++) {
            OTHER.put(String.valueOf((char) (0x0ED0 + i)), String.valueOf(i));
        }
    }

    private final String[] multiLao;
    private final String[] multiThai;
    private final Map<String, String> single;

    public LaoToThai(List<String[]> entries) {
        List<String> ml = new ArrayList<>();
        List<String> mt = new ArrayList<>();
        single = new HashMap<>();
        for (String[] e : entries) {
            if (e[0].length() > 1) {
                ml.add(e[0]);
                mt.add(e[1]);
            } else {
                single.put(e[0], e[1]);
            }
        }
        multiLao = ml.toArray(new String[0]);
        multiThai = mt.toArray(new String[0]);
    }

    /** Reads a tab separated file: Lao, Thai (UTF-8, one entry per line). */
    public static LaoToThai fromStream(InputStream in) throws IOException {
        List<String[]> entries = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                int t = line.indexOf('\t');
                if (t <= 0) {
                    continue;
                }
                String lao = line.substring(0, t);
                String rest = line.substring(t + 1);
                int t2 = rest.indexOf('\t');
                String thai = t2 >= 0 ? rest.substring(0, t2) : rest;
                entries.add(new String[]{lao, thai});
            }
        }
        return new LaoToThai(entries);
    }

    public static boolean isLao(char c) {
        return c >= '຀' && c <= '໿';
    }

    public static boolean hasLao(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            if (isLao(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String strip(String s) {
        int a = 0;
        int b = s.length();
        while (a < b && Character.isWhitespace(s.charAt(a))) {
            a++;
        }
        while (b > a && Character.isWhitespace(s.charAt(b - 1))) {
            b--;
        }
        return s.substring(a, b);
    }

    public String convert(String text) {
        if (!hasLao(text)) {
            return text;
        }
        String stripped = strip(text);
        if (stripped.length() == 1) {
            String s = single.get(stripped);
            if (s != null) {
                return s;
            }
        }
        for (int i = 0; i < multiLao.length; i++) {
            if (text.contains(multiLao[i])) {
                text = text.replace(multiLao[i], multiThai[i]);
            }
        }
        return fallback(text);
    }

    static String fallback(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == 'ຫ' && i + 1 < n) {
                char nxt = text.charAt(i + 1);
                if (nxt == 'ຼ') {
                    out.append('ล');
                    i += 2;
                    continue;
                }
                if (SILENT_H_BEFORE.indexOf(nxt) >= 0) {
                    i += 1;
                    continue;
                }
            }
            String key = String.valueOf(c);
            String v = CONS.get(key);
            if (v == null) {
                v = OTHER.get(key);
            }
            if (v != null) {
                out.append(v);
            } else {
                out.append(c);
            }
            i++;
        }
        return out.toString();
    }
}
