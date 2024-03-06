/*
 * Copyright MURENA SAS 2024
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package it.niedermann.owncloud.notes.shared.account;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.model.SingleSignOnAccount;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.niedermann.owncloud.notes.persistence.ApiProvider;
import it.niedermann.owncloud.notes.persistence.NotesRepository;
import it.niedermann.owncloud.notes.persistence.entity.Account;
import it.niedermann.owncloud.notes.shared.model.Capabilities;
import it.niedermann.owncloud.notes.shared.model.IResponseCallback;
import trikita.log.Log;

public class LocalAccountViewModel extends AndroidViewModel {

    private static final String TAG = LocalAccountViewModel.class.getSimpleName();

    @NonNull
    private final NotesRepository notesRepository;

    @NonNull
    private final ExecutorService executor;

    public LocalAccountViewModel(@NonNull Application application) {
        super(application);
        notesRepository = NotesRepository.getInstance(application);
        executor = Executors.newSingleThreadExecutor();
    }

    public void logInToLocalAccount(@NonNull IResponseCallback<LocalAccountBundle> callback) {
        executor.submit(() -> {
            SingleSignOnAccount singleSignOnAccount = AccountImporter.pickLocalAccount(getApplication());
            Log.i(TAG, "Added local account: " + "name:" + singleSignOnAccount.name);
            try {
                final var capabilities = new Capabilities();
                final String displayName = singleSignOnAccount.name;
                notesRepository.addAccount(singleSignOnAccount.url, singleSignOnAccount.userId, singleSignOnAccount.name, capabilities, displayName, new IResponseCallback<>() {
                    @Override
                    public void onSuccess(Account account) {
                        final var bundle = new LocalAccountBundle(singleSignOnAccount, account, capabilities);
                        callback.onSuccess(bundle);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        Log.e(TAG, throwable);
                        callback.onError(throwable);
                    }
                });
            } catch (Throwable t) {
                ApiProvider.getInstance().invalidateAPICache(singleSignOnAccount);
                Log.e(TAG, t);
                callback.onError(t);
            }
        });
    }
}
