package it.niedermann.owncloud.notes.branding;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Menu;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import it.niedermann.owncloud.notes.R;

import static it.niedermann.owncloud.notes.branding.BrandingUtil.readBrandColors;
import static it.niedermann.owncloud.notes.branding.BrandingUtil.tintMenuIcon;

public abstract class BrandedActivity extends AppCompatActivity implements Branded {

    @ColorInt
    protected int colorAccent;

    @Override
    protected void onStart() {
        super.onStart();

        final var typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorAccent, typedValue, true);
        colorAccent = typedValue.data;

        readBrandColors(this).observe(this, (pair) -> applyBrand(pair.first, pair.second));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        for (int i = 0; i < menu.size(); i++) {
            tintMenuIcon(menu.getItem(i), colorAccent);
        }
        return super.onCreateOptionsMenu(menu);
    }

    public void applyBrandToPrimaryToolbar(@NonNull AppBarLayout appBarLayout, @NonNull Toolbar toolbar) {
    }
}
