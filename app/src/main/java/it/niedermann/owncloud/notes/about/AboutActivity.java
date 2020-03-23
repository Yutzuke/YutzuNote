package it.niedermann.owncloud.notes.about;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import it.niedermann.owncloud.notes.R;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        getFragmentManager().beginTransaction().replace(R.id.container, new AboutFragment()).commit();
    }
}