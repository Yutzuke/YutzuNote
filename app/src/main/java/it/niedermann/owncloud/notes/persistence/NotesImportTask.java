package it.niedermann.owncloud.notes.persistence;

import static it.niedermann.owncloud.notes.persistence.NotesServerSyncTask.HEADER_KEY_ETAG;
import static it.niedermann.owncloud.notes.persistence.NotesServerSyncTask.HEADER_KEY_LAST_MODIFIED;

import android.content.Context;

import it.niedermann.owncloud.notes.shared.util.ResponseUtil;
import trikita.log.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.niedermann.owncloud.notes.persistence.entity.Account;
import it.niedermann.owncloud.notes.persistence.sync.NotesAPI;
import it.niedermann.owncloud.notes.shared.model.IResponseCallback;
import it.niedermann.owncloud.notes.shared.model.ImportStatus;
import it.niedermann.owncloud.notes.shared.util.ApiVersionUtil;


public class NotesImportTask {

    private static final String TAG = NotesImportTask.class.getSimpleName();

    private final NotesAPI notesAPI;
    @NonNull
    private final NotesRepository repo;
    @NonNull
    private final Account localAccount;
    @NonNull
    private final ExecutorService executor;
    @NonNull
    private final ExecutorService fetchExecutor;

    NotesImportTask(@NonNull Context context, @NonNull NotesRepository repo, @NonNull Account localAccount, @NonNull ExecutorService executor, @NonNull ApiProvider apiProvider) throws NextcloudFilesAppAccountNotFoundException {
        this(context, repo, localAccount, executor, Executors.newFixedThreadPool(20), apiProvider);
    }

    private NotesImportTask(@NonNull Context context, @NonNull NotesRepository repo, @NonNull Account localAccount, @NonNull ExecutorService executor, @NonNull ExecutorService fetchExecutor, @NonNull ApiProvider apiProvider) throws NextcloudFilesAppAccountNotFoundException {
        this.repo = repo;
        this.localAccount = localAccount;
        this.executor = executor;
        this.fetchExecutor = fetchExecutor;
        this.notesAPI = apiProvider.getNotesAPI(context, AccountImporter.getSingleSignOnAccount(context, localAccount.getAccountName()), ApiVersionUtil.getPreferredApiVersion(localAccount.getApiVersion()));
    }

    public LiveData<ImportStatus> importNotes(@NonNull IResponseCallback<Void> callback) {
        final var status$ = new MutableLiveData<ImportStatus>();
        Log.i(TAG, "STARTING IMPORT");
        executor.submit(() -> {
            Log.i(TAG, "… Fetching notes IDs");
            final var status = new ImportStatus();
            try {
                final var response = notesAPI.getNotesIDs().blockingSingle();
                final var cleanedHeaders = ResponseUtil.getLowerCaseHeaders(response.getKey());
                final var remoteIds = response.getValue();
                status.total = remoteIds.size();
                status$.postValue(status);
                Log.i(TAG, "… Total count: " + remoteIds.size());
                final var latch = new CountDownLatch(remoteIds.size());
                for (long id : remoteIds) {
                    fetchExecutor.submit(() -> {
                        try {
                            repo.addNote(localAccount.getId(), notesAPI.getNote(id).blockingSingle().getResponse());
                        } catch (Throwable t) {
                            Log.w(TAG, "Could not import note with remoteId " + id + ": " + t.getMessage());
                            status.warnings.add(t);
                        }
                        status.count++;
                        status$.postValue(status);
                        latch.countDown();
                    });
                }
                try {
                    latch.await();

                    // update ETag and Last-Modified in order to reduce size of next response
                    localAccount.setETag(cleanedHeaders.get(HEADER_KEY_ETAG));

                    final var lastModified = Calendar.getInstance();
                    lastModified.setTimeInMillis(0);
                    final String lastModifiedHeader = cleanedHeaders.get(HEADER_KEY_LAST_MODIFIED);
                    if (lastModifiedHeader != null) {
                        lastModified.setTimeInMillis(Date.parse(lastModifiedHeader));
                    }

                    Log.d(TAG, "ETag: " + cleanedHeaders.get(HEADER_KEY_ETAG) + "; Last-Modified: " + lastModified + " (" + lastModified + ")");

                    localAccount.setModified(lastModified);

                    repo.updateETag(localAccount.getId(), localAccount.getETag());
                    repo.updateModified(localAccount.getId(), localAccount.getModified().getTimeInMillis());

                    Log.i(TAG, "IMPORT FINISHED");
                    callback.onSuccess(null);
                } catch (InterruptedException e) {
                    callback.onError(e);
                }
            } catch (Throwable t) {
                final Throwable cause = t.getCause();
                if (t.getClass() == RuntimeException.class && cause != null) {
                    Log.e(TAG, "Could not fetch list of note IDs: " + cause.getMessage());
                    callback.onError(cause);
                } else {
                    Log.e(TAG, "Could not fetch list of note IDs: " + t.getMessage());
                    callback.onError(t);
                }
            }
        });
        return status$;
    }
}
