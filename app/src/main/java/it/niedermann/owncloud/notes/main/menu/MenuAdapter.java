package it.niedermann.owncloud.notes.main.menu;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;

import com.nextcloud.android.sso.Constants;
import com.nextcloud.android.sso.helper.VersionCheckHelper;
import com.nextcloud.android.sso.model.FilesAppType;

import java.util.ArrayList;
import java.util.List;

import it.niedermann.owncloud.notes.FormattingHelpActivity;
import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.about.AboutActivity;
import it.niedermann.owncloud.notes.databinding.ItemNavigationBinding;
import it.niedermann.owncloud.notes.persistence.entity.Account;
import it.niedermann.owncloud.notes.preferences.PreferencesActivity;
import it.niedermann.owncloud.notes.shared.util.AccountSyncUtil;

public class MenuAdapter extends RecyclerView.Adapter<MenuViewHolder> {

    @NonNull
    private final MenuItem[] menuItems;
    @NonNull
    private final Consumer<MenuItem> onClick;

    public MenuAdapter(@NonNull Context context, @NonNull Account account, int settingsRequestCode, @NonNull Consumer<MenuItem> onClick) {
        List<MenuItem> menuList = new ArrayList<>();
        menuList.add(new MenuItem(new Intent(context, FormattingHelpActivity.class), R.string.action_formatting_help, R.drawable.ic_baseline_help_outline_24));

        if (!AccountSyncUtil.isLocalAccount(context, account)) {
            menuList.add(new MenuItem(generateTrashbinIntent(context, account), R.string.action_trashbin, R.drawable.ic_delete_grey600_24dp));
        }

        menuList.add(new MenuItem(new Intent(context, PreferencesActivity.class), settingsRequestCode, R.string.action_settings, R.drawable.ic_settings_grey600_24dp));
        menuList.add(new MenuItem(new Intent(context, AboutActivity.class), R.string.simple_about, R.drawable.ic_info_outline_grey600_24dp));

        this.menuItems = menuList.toArray(new MenuItem[0]);

        this.onClick = onClick;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public MenuViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MenuViewHolder(ItemNavigationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MenuViewHolder holder, int position) {
        holder.bind(menuItems[position], onClick);
    }

    public void updateAccount(@NonNull Context context, @NonNull Account account) {
        menuItems[1].setIntent(new Intent(generateTrashbinIntent(context, account)));
    }

    @Override
    public int getItemCount() {
        return menuItems.length;
    }

    @NonNull
    private static Intent generateTrashbinIntent(@NonNull Context context, @NonNull Account account) {
        return generateTrashbinWebIntent(account);
    }

    private static Intent generateTrashbinAppIntent(@NonNull Context context, @NonNull Account account, boolean prod) throws PackageManager.NameNotFoundException {
        final var packageManager = context.getPackageManager();
        final String packageName = prod ? FilesAppType.ACCOUNT_MANAGER.packageId : FilesAppType.DEV.packageId;
        final var intent = new Intent();
        intent.setClassName(packageName, "com.owncloud.android.ui.trashbin.TrashbinActivity");
        if (packageManager.resolveActivity(intent, 0) != null) {
            return intent
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Intent.EXTRA_USER, account.getAccountName());
        }
        throw new PackageManager.NameNotFoundException("Could not resolve target activity.");
    }

    private static Intent generateTrashbinWebIntent(@NonNull Account account) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(account.getUrl() + "/index.php/apps/files/?dir=/&view=trashbin"));
    }
}
