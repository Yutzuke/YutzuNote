package it.niedermann.owncloud.notes.about;

import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.main.MainActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        getFragmentManager().beginTransaction().replace(R.id.container, new AboutFragment()).commit();
        updateAccentColor();
    }

    private void updateAccentColor() {
        //change toolbar color
        getSupportActionBar().setBackgroundDrawable(
                new ColorDrawable(MainActivity.ACCENT_COLOR));

        //change status bar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(darkenColor(MainActivity.ACCENT_COLOR));
        }
    }

    @ColorInt
    int darkenColor(@ColorInt int color) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[2] *= 0.8f;
        return android.graphics.Color.HSVToColor(hsv);
    }
}