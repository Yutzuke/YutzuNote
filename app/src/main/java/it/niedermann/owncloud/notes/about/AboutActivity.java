package it.niedermann.owncloud.notes.about;

import android.os.Bundle;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.branding.BrandedActivity;
import it.niedermann.owncloud.notes.databinding.ActivityAboutBinding;

public class AboutActivity extends BrandedActivity {

    private ActivityAboutBinding binding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        getFragmentManager().beginTransaction().replace(binding.container.getId(), new AboutFragment()).commit();
    }

    @Override
    public void applyBrand(int mainColor, int textColor) {
        applyBrandToPrimaryToolbar(binding.appBar, binding.toolbar);
    }
}