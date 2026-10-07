package io.resizek;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** アプリ一覧から開く画面。使い方と「いつもの設定」(長辺の大きさ・画質) だけ。 */
public final class SettingsActivity extends Activity {
    private TextView example;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col);

        TextView title = text(26, R.color.text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setText(R.string.app_name);
        col.addView(title);

        TextView how = text(15, R.color.text);
        how.setText(R.string.how_to);
        how.setLineSpacing(0, 1.25f);
        col.addView(how, gap(12));

        col.addView(heading(R.string.edge_title), gap(28));
        RadioGroup edges = new RadioGroup(this);
        int edge = Prefs.edge(this);
        for (int v : Prefs.EDGES) {
            RadioButton b = radio(v + " px" + (v == Prefs.DEFAULT_EDGE ? getString(R.string.default_mark) : ""));
            b.setId(v);
            edges.addView(b);
        }
        edges.check(edge);
        edges.setOnCheckedChangeListener((g, id) -> {
            Prefs.setEdge(this, id);
            updateExample(id);
        });
        col.addView(edges);
        example = text(13, R.color.text_sub);
        col.addView(example, gap(4));
        updateExample(edge);

        col.addView(heading(R.string.quality_title), gap(28));
        RadioGroup qs = new RadioGroup(this);
        qs.setOrientation(RadioGroup.HORIZONTAL);
        for (int v : Prefs.QUALITIES) {
            RadioButton b = radio(String.valueOf(v));
            b.setId(v);
            qs.addView(b, new RadioGroup.LayoutParams(0, -2, 1f));
        }
        qs.check(Prefs.quality(this));
        qs.setOnCheckedChangeListener((g, id) -> Prefs.setQuality(this, id));
        col.addView(qs);
        TextView qn = text(13, R.color.text_sub);
        qn.setText(R.string.quality_note);
        col.addView(qn, gap(4));

        TextView privacy = text(13, R.color.text_sub);
        privacy.setText(R.string.privacy_note);
        privacy.setLineSpacing(0, 1.2f);
        col.addView(privacy, gap(32));

        Ui.edgeToEdge(getWindow(), scroll, Ui.dp(this, 20));
        setContentView(scroll);
    }

    /** 4032x3024 (よくある 1200 万画素) の写真ならどうなるかを添える。 */
    private void updateExample(int edge) {
        example.setText(getString(R.string.edge_example, edge, Math.round(3024f * edge / 4032f)));
    }

    private TextView heading(int res) {
        TextView t = text(13, R.color.accent);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setText(res);
        return t;
    }

    private RadioButton radio(String label) {
        RadioButton b = new RadioButton(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTextColor(getColor(R.color.text));
        b.setButtonTintList(ColorStateList.valueOf(getColor(R.color.accent)));
        b.setMinHeight(Ui.dp(this, 44));
        return b;
    }

    private TextView text(float sp, int colorRes) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getColor(colorRes));
        return t;
    }

    private LinearLayout.LayoutParams gap(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, topDp);
        return lp;
    }
}
