package it.niedermann.owncloud.notes.shared.util;

import android.app.Activity;
import android.content.Context;
import trikita.log.Log;

import androidx.annotation.NonNull;

import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.exceptions.AndroidGetAccountsPermissionNotGranted;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException;
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException;
import com.nextcloud.android.sso.helper.SingleAccountHelper;
import com.nextcloud.android.sso.ui.UiExceptionManager;

public class SSOUtil {

    private static final String TAG = SSOUtil.class.getSimpleName();

    private SSOUtil() {
        throw new UnsupportedOperationException("Do not instantiate this util class.");
    }

    /**
     * Opens a dialog which allows the user to pick a Nextcloud account (which previously has to be configured in the files app).
     * Also allows to configure a new Nextcloud account in the files app and directly import it.
     *
     * @param activity should implement AccountImporter.onActivityResult
     */
    public static void askForNewAccount(@NonNull Activity activity) {
        try {
            AccountImporter.pickNewAccount(activity);
        } catch (NextcloudFilesAppNotInstalledException e1) {
            UiExceptionManager.showDialogForException(activity, e1);
            Log.w(TAG, "=============================================================");
            Log.w(TAG, "Nextcloud app is not installed. Cannot choose account");
            e1.printStackTrace();
        } catch (AndroidGetAccountsPermissionNotGranted e2) {
            AccountImporter.requestAndroidAccountPermissionsAndPickAccount(activity);
        }
    }

    public static boolean isConfigured(Context context) {
        try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(context);
            return true;
        } catch (NextcloudFilesAppAccountNotFoundException | NoCurrentAccountSelectedException e) {
            return false;
        }
    }

    /**
     * Murena account's userId is set same as it's email address (passed by accountManager app).
     * For old accounts (@e.email) userId = email.
     * For new accounts (@murena.io) userId is first part of email (ex: for email abc@murena.io, userId is abc).
     * For api requests, we needed to pass the actual userId. This method remove the unwanted part (@murena.io) from the userId
     */
    @NonNull
    public static String sanitizeUserId(@NonNull String userId) {
        final String murenaMailEndPart = "@murena.io";

        if (!userId.endsWith(murenaMailEndPart)) {
            return userId;
        }

        return userId.split(murenaMailEndPart)[0];
    }
}
