package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.SmartSaveManager;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.StorageInfoDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.StringUtils;
import com.winlator.xenvironment.RootFS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContainersFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private ContainerManager manager;
    private PreloaderDialog preloaderDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(getContext());
        loadContainersList();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.containers);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout frameLayout = (FrameLayout)inflater.inflate(R.layout.containers_fragment, container, false);
        recyclerView = frameLayout.findViewById(R.id.RecyclerView);
        Context context = recyclerView.getContext();
        emptyTextView = frameLayout.findViewById(R.id.TVEmptyText);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        DividerItemDecoration itemDecoration = new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL);
        itemDecoration.setDrawable(ContextCompat.getDrawable(context, R.drawable.list_item_divider));
        recyclerView.addItemDecoration(itemDecoration);
        return frameLayout;
    }

    private void loadContainersList() {
        ArrayList<Container> containers = manager.getContainers();
        recyclerView.setAdapter(new ContainersAdapter(containers));
        if (containers.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.containers_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.menu_item_add) {
            if (!RootFS.find(getContext()).isValid()) return false;
            FragmentManager fragmentManager = getParentFragmentManager();
            fragmentManager.beginTransaction()
                .addToBackStack(null)
                .replace(R.id.FLFragmentContainer, new ContainerDetailFragment())
                .commit();
            return true;
        }
        else return super.onOptionsItemSelected(menuItem);
    }

    private class ContainersAdapter extends RecyclerView.Adapter<ContainersAdapter.ViewHolder> {
        private final List<Container> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView runButton;
            private final ImageView menuButton;
            private final ImageView imageView;
            private final TextView title;

            private ViewHolder(View view) {
                super(view);
                this.imageView = view.findViewById(R.id.ImageView);
                this.title = view.findViewById(R.id.TVTitle);
                this.runButton = view.findViewById(R.id.BTRun);
                this.menuButton = view.findViewById(R.id.BTMenu);
            }
        }

        public ContainersAdapter(List<Container> data) {
            this.data = data;
        }

        @Override
        public final ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.container_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final Container item = data.get(position);
            holder.imageView.setImageResource(R.drawable.icon_container);
            holder.title.setText(item.getName());
            holder.runButton.setOnClickListener((view) -> runContainer(item));
            holder.menuButton.setOnClickListener((view) -> showListItemMenu(view, item));
        }

        @Override
        public final int getItemCount() {
            return data.size();
        }

        private void showListItemMenu(View anchorView, Container container) {
            MainActivity activity = (MainActivity)getActivity();
            PopupMenu listItemMenu = new PopupMenu(activity, anchorView);
            listItemMenu.inflate(R.menu.container_popup_menu);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                switch (menuItem.getItemId()) {
                    case R.id.menu_item_file_manager:
                        activity.showFragment(new ContainerFileManagerFragment(container.id));
                        break;
                    case R.id.menu_item_edit:
                        activity.showFragment(new ContainerDetailFragment(container.id));
                        break;
                    case R.id.menu_item_backup_saves:
                        backupSaves(container);
                        break;
                    case R.id.menu_item_restore_saves:
                        restoreSaves(container);
                        break;
                    case R.id.menu_item_duplicate:
                        ContentDialog.confirm(getContext(), R.string.do_you_want_to_duplicate_this_container, () -> {
                            preloaderDialog.show(R.string.duplicating_container);
                            manager.duplicateContainerAsync(container, () -> {
                                preloaderDialog.close();
                                loadContainersList();
                            });
                        });
                        break;
                    case R.id.menu_item_remove:
                        ContentDialog.confirm(getContext(), R.string.do_you_want_to_remove_this_container, () -> {
                            preloaderDialog.show(R.string.removing_container);
                            manager.removeContainerAsync(container, () -> {
                                preloaderDialog.close();
                                loadContainersList();
                            });
                        });
                        break;
                    case R.id.menu_item_info:
                        (new StorageInfoDialog(activity, container)).show();
                        break;
                }
                return true;
            });
            listItemMenu.show();
        }

        private void backupSaves(Container container) {
            preloaderDialog.show(R.string.searching_for_save_data);
            runAsync(() -> {
                try {
                    SmartSaveManager.ScanResult scan = SmartSaveManager.scan(container);
                    onUiThread(() -> {
                        preloaderDialog.close();
                        if (scan.entries.isEmpty()) {
                            AppUtils.showToast(getContext(), R.string.no_save_data_found);
                            return;
                        }
                        String summary = getString(R.string.save_data_found_summary,
                            scan.entries.size(), StringUtils.formatBytes(scan.totalSize, false));
                        showConfirmation(R.string.confirm_backup, summary, () -> createBackup(container, scan));
                    });
                }
                catch (Exception e) {
                    onUiThread(() -> {
                        preloaderDialog.close();
                        AppUtils.showToast(getContext(), R.string.backup_failed);
                    });
                }
            });
        }

        private void createBackup(Container container, SmartSaveManager.ScanResult scan) {
            preloaderDialog.show(R.string.creating_save_backup);
            runAsync(() -> {
                try {
                    SmartSaveManager.createBackup(container, scan);
                    onUiThread(() -> {
                        preloaderDialog.close();
                        AppUtils.showToast(getContext(), R.string.backup_completed);
                    });
                }
                catch (Exception e) {
                    onUiThread(() -> {
                        preloaderDialog.close();
                        AppUtils.showToast(getContext(), R.string.backup_failed);
                    });
                }
            });
        }

        private void restoreSaves(Container container) {
            preloaderDialog.show(R.string.searching_for_save_backups);
            runAsync(() -> {
                List<SmartSaveManager.BackupInfo> backups = SmartSaveManager.listBackups(container);
                onUiThread(() -> {
                    preloaderDialog.close();
                    if (backups.isEmpty()) {
                        AppUtils.showToast(getContext(), R.string.no_save_backups_found);
                        return;
                    }
                    String[] items = new String[backups.size()];
                    for (int i = 0; i < backups.size(); i++) {
                        SmartSaveManager.BackupInfo backup = backups.get(i);
                        items[i] = getString(R.string.save_backup_list_item, backup.displayName,
                            backup.entryCount, StringUtils.formatBytes(backup.totalSize, false));
                    }
                    ContentDialog.showSelectionList(getContext(), R.string.select_backup, items, false, selected -> {
                        if (!selected.isEmpty()) validateRestore(container, backups.get(selected.get(0)));
                    });
                });
            });
        }

        private void validateRestore(Container container, SmartSaveManager.BackupInfo backup) {
            preloaderDialog.show(R.string.validating_save_backup);
            runAsync(() -> {
                try {
                    SmartSaveManager.BackupInfo validated = SmartSaveManager.validateBackup(container, backup.directory);
                    onUiThread(() -> {
                        preloaderDialog.close();
                        String summary = getString(R.string.confirm_restore_summary, validated.entryCount,
                            StringUtils.formatBytes(validated.totalSize, false));
                        showConfirmation(R.string.confirm_restore, summary+"\n\n"+
                            getString(R.string.existing_files_will_be_overwritten),
                            () -> performRestore(container, validated));
                    });
                }
                catch (Exception e) {
                    onUiThread(() -> {
                        preloaderDialog.close();
                        AppUtils.showToast(getContext(), R.string.invalid_backup);
                    });
                }
            });
        }

        private void performRestore(Container container, SmartSaveManager.BackupInfo backup) {
            preloaderDialog.show(R.string.restoring_save_data);
            runAsync(() -> {
                try {
                    SmartSaveManager.restore(container, backup.directory);
                    onUiThread(() -> {
                        preloaderDialog.close();
                        AppUtils.showToast(getContext(), R.string.restore_completed);
                    });
                }
                catch (Exception e) {
                    onUiThread(() -> {
                        preloaderDialog.close();
                        AppUtils.showToast(getContext(), R.string.restore_failed);
                    });
                }
            });
        }

        private void showConfirmation(int titleResId, String message, Runnable callback) {
            ContentDialog dialog = new ContentDialog(getContext());
            dialog.setTitle(titleResId);
            dialog.setMessage(message);
            dialog.setOnConfirmCallback(callback);
            dialog.show();
        }

        private void runAsync(Runnable runnable) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    runnable.run();
                }
                finally {
                    executor.shutdown();
                }
            });
        }

        private void onUiThread(Runnable runnable) {
            Activity activity = getActivity();
            if (activity != null) activity.runOnUiThread(() -> {
                if (isAdded()) runnable.run();
            });
        }

        private void runContainer(Container container) {
            Activity activity = getActivity();
            Intent intent = new Intent(activity, XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            activity.startActivity(intent);
        }
    }
}
