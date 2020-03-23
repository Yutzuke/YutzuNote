package it.niedermann.owncloud.notes.about;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import androidx.annotation.Nullable;

import it.niedermann.owncloud.notes.BuildConfig;
import it.niedermann.owncloud.notes.R;

public class AboutFragment extends PreferenceFragment {

    private static final String BUILD_VERSION = "build_version";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.about_preferences);

        findPreference(BUILD_VERSION).setSummary(BuildConfig.VERSION_NAME);

    }
}
