"""Writes res/layout/day_widget.xml: the Najwa widget, modernised, with everything visible.

    python scripts/gen_day_widget.py app/src/main/res/layout/day_widget.xml
"""
import sys

AR_REG = '@font/noto_sans_arabic_regular'
AR_BOLD = '@font/noto_sans_arabic_bold'


def text(id_, size, color="#F6F4EC", bold=False, extra="", width="wrap_content", height="wrap_content"):
    return (f'<TextView android:id="@+id/{id_}" android:layout_width="{width}" android:layout_height="{height}" android:text="" '
            f'android:textColor="{color}" android:textSize="{size}sp" android:fontFamily="{AR_BOLD if bold else AR_REG}" '
            f'android:includeFontPadding="false" android:maxLines="1" android:ellipsize="end" {extra}/>')


def cell(i):
    start = '' if i == 0 else ' android:layout_marginStart="4dp"'
    return f'''<LinearLayout android:id="@+id/dw_p{i}" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:gravity="center_horizontal" android:paddingTop="5dp" android:paddingBottom="5dp"{start}>
{text(f"dw_p{i}_name", 11, "#B8CBC0", extra='android:gravity="center"', width="match_parent")}
{text(f"dw_p{i}_time", 13, bold=True, extra='android:gravity="center" android:layout_marginTop="2dp"', width="match_parent")}
{text(f"dw_p{i}_iqama", 11, "#B8CBC0", extra='android:gravity="center" android:layout_marginTop="1dp"', width="match_parent")}
</LinearLayout>'''


def card(k, bar=False):
    progress = (f'<ProgressBar android:id="@+id/dw_{k}_bar" style="?android:attr/progressBarStyleHorizontal" android:layout_width="match_parent" '
                f'android:layout_height="4dp" android:layout_marginTop="5dp" android:max="1000" android:mirrorForRtl="true" '
                f'android:progressDrawable="@drawable/dw_goal_track" android:importantForAccessibility="no" />') if bar else ''
    return f'''<LinearLayout android:id="@+id/dw_{k}" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:orientation="vertical" android:background="@drawable/dw_card" android:paddingStart="9dp" android:paddingEnd="9dp" android:paddingTop="7dp" android:paddingBottom="7dp" android:layout_marginStart="{"0dp" if k == "a" else "6dp"}">
{text(f"dw_{k}_title", 11, "#B8CBC0", width="match_parent", extra='android:textAlignment="viewStart"')}
{text(f"dw_{k}_value", 14, bold=True, width="match_parent", extra='android:textAlignment="viewStart" android:layout_marginTop="3dp" android:autoSizeTextType="uniform" android:autoSizeMinTextSize="9sp" android:autoSizeMaxTextSize="14sp" android:autoSizeStepGranularity="1sp"')}
{progress}
{text(f"dw_{k}_note", 11, "#B8CBC0", width="match_parent", extra='android:textAlignment="viewStart" android:layout_marginTop="3dp" android:maxLines="2"').replace('android:maxLines="1" ','')}
</LinearLayout>'''


layout = f'''<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/dw_root" android:layout_width="match_parent" android:layout_height="match_parent" android:layoutDirection="inherit">
<!-- Najwa's own card and orbit art, as in the classic widget; only the content is new. -->
<ImageView android:id="@+id/dw_backdrop" android:layout_width="match_parent" android:layout_height="match_parent" android:src="@drawable/widget_background" android:scaleType="fitXY" android:importantForAccessibility="no" />
<LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:layoutDirection="inherit" android:paddingStart="14dp" android:paddingEnd="14dp" android:paddingTop="12dp" android:paddingBottom="12dp">
<LinearLayout android:layout_width="match_parent" android:layout_height="24dp" android:orientation="horizontal" android:gravity="center_vertical">
<ImageView android:id="@+id/dw_mark" android:layout_width="16dp" android:layout_height="16dp" android:src="@drawable/ic_najwa_mark" android:importantForAccessibility="no" />
{text("dw_brand", 13, bold=True, width="0dp", extra='android:layout_weight="1" android:layout_marginStart="6dp"')}
{text("dw_city", 11, "#B8CBC0", height="20dp", extra='android:gravity="center_vertical" android:maxWidth="120dp" android:paddingHorizontal="9dp" android:background="@drawable/widget_pill"')}
<ImageView android:id="@+id/dw_refresh" android:layout_width="28dp" android:layout_height="28dp" android:layout_marginStart="4dp" android:padding="5dp" android:src="@drawable/ic_widget_refresh" android:contentDescription="@string/system_refresh" />
</LinearLayout>
{text("dw_date", 11, "#B8CBC0", width="match_parent", extra='android:textAlignment="viewStart" android:layout_marginTop="2dp"')}
<FrameLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="10dp" android:orientation="horizontal" android:gravity="bottom">
<LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
{text("dw_phase", 12, "#B8CBC0", bold=True, width="match_parent", extra='android:textAlignment="viewStart"')}
{text("dw_prayer", 26, bold=True, width="match_parent", extra='android:textAlignment="viewStart" android:paddingTop="2dp"')}
{text("dw_time", 12, "#B8CBC0", width="match_parent", extra='android:textAlignment="viewStart" android:layout_marginTop="2dp"')}
</LinearLayout>
<LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="vertical" android:gravity="end">
{text("dw_count_label", 11, "#B8CBC0", extra='android:gravity="end"')}
<Chronometer android:id="@+id/dw_countdown" android:layout_width="wrap_content" android:layout_height="38dp" android:gravity="center_vertical|end" android:textSize="30sp" android:textStyle="bold" android:fontFamily="sans-serif-medium" android:textColor="#A5E8C4" android:includeFontPadding="false" android:layoutDirection="ltr" android:textDirection="ltr" android:fontFeatureSettings="tnum" android:countDown="true" android:maxLines="1" />
</LinearLayout>
</LinearLayout>
<!-- Second clock: the iqama, counted at the same time as the adhan. -->
<LinearLayout android:id="@+id/dw_iqama_box" android:layout_width="wrap_content" android:layout_height="24dp" android:layout_marginTop="6dp" android:orientation="horizontal" android:gravity="center_vertical" android:background="@drawable/dw_chip" android:paddingStart="10dp" android:paddingEnd="10dp">
{text("dw_iqama_label", 11, "#F0C787", extra='android:paddingEnd="8dp"')}
<Chronometer android:id="@+id/dw_iqama_countdown" android:layout_width="wrap_content" android:layout_height="wrap_content" android:textSize="13sp" android:textStyle="bold" android:fontFamily="sans-serif-medium" android:textColor="#F0C787" android:includeFontPadding="false" android:layoutDirection="ltr" android:textDirection="ltr" android:fontFeatureSettings="tnum" android:countDown="true" android:maxLines="1" />
</LinearLayout>
<!-- The day's orbit: the sun's path through the five prayers, drawn as a bitmap. -->
<ImageView android:id="@+id/dw_orbit" android:layout_width="match_parent" android:layout_height="40dp" android:layout_marginTop="6dp" android:scaleType="fitXY" android:importantForAccessibility="no" />
<LinearLayout android:id="@+id/dw_day" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal">
{"".join(cell(i) for i in range(5))}
</LinearLayout>
<FrameLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
<LinearLayout android:id="@+id/dw_worship" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="8dp" android:orientation="horizontal" android:baselineAligned="false">
{card("a", bar=True)}
{card("b")}
{card("c")}
</LinearLayout>
{text("dw_race", 12, "#2A1E07", bold=True, width="match_parent", height="28dp", extra='android:layout_marginTop="8dp" android:gravity="center" android:background="@drawable/dw_ribbon" android:paddingStart="10dp" android:paddingEnd="10dp"')}
</LinearLayout>
</FrameLayout>
'''
open(sys.argv[1], "w", encoding="utf-8", newline="\n").write(layout)
print("ok")
