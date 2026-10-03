/*
 * SPDX-FileCopyrightText: 2026 its-hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.motionsense;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.AppSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import com.android.settingslib.widget.TopIntroPreference;

import org.lineageos.settings.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lists the apps Quick Gestures treat as media apps. Apps on the list are shown first;
 * switching an app on or off adds it to or removes it from aware_media_apps.
 */
public class MediaAppsFragment extends SettingsBasePreferenceFragment {

    private static final class AppEntry {
        final String packageName;
        final CharSequence label;
        final Drawable icon;

        AppEntry(String packageName, CharSequence label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private List<AppEntry> mApps;
    private Set<String> mSelected;
    private String mQuery = "";

    private PreferenceCategory mListedCategory;
    private PreferenceCategory mOtherCategory;
    private Preference mStatus;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = requireContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);

        TopIntroPreference intro = new TopIntroPreference(context);
        intro.setKey("media_apps_intro");
        intro.setTitle(R.string.motion_sense_media_apps_intro);
        screen.addPreference(intro);

        mStatus = new Preference(context);
        mStatus.setKey("media_apps_status");
        mStatus.setTitle(R.string.motion_sense_media_apps_loading);
        mStatus.setSelectable(false);
        screen.addPreference(mStatus);

        mListedCategory = new PreferenceCategory(context);
        mListedCategory.setKey("media_apps_listed");
        mListedCategory.setTitle(R.string.motion_sense_media_apps_listed);
        screen.addPreference(mListedCategory);

        mOtherCategory = new PreferenceCategory(context);
        mOtherCategory.setKey("media_apps_other");
        mOtherCategory.setTitle(R.string.motion_sense_media_apps_other);
        screen.addPreference(mOtherCategory);

        mListedCategory.setVisible(false);
        mOtherCategory.setVisible(false);

        loadApps(context.getApplicationContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }

    private void loadApps(Context context) {
        mExecutor.execute(() -> {
            PackageManager pm = context.getPackageManager();
            Map<String, ApplicationInfo> infos = new HashMap<>();

            // Apps with a launcher icon, plus listed apps that have none
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                infos.put(ai.packageName, ai);
            }
            for (String app : MediaApps.get(context)) {
                if (!infos.containsKey(app)) {
                    try {
                        infos.put(app, pm.getApplicationInfo(app, 0));
                    } catch (PackageManager.NameNotFoundException e) {
                        // not installed
                    }
                }
            }
            infos.remove(context.getPackageName());

            List<AppEntry> apps = new ArrayList<>();
            for (ApplicationInfo ai : infos.values()) {
                apps.add(new AppEntry(ai.packageName, ai.loadLabel(pm), ai.loadIcon(pm)));
            }
            Collator collator = Collator.getInstance();
            apps.sort((a, b) -> collator.compare(a.label.toString(), b.label.toString()));

            mHandler.post(() -> {
                if (isAdded()) {
                    mApps = apps;
                    mSelected = MediaApps.get(requireContext());
                    rebuild();
                }
            });
        });
    }

    void setQuery(String query) {
        mQuery = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        if (mApps != null) {
            rebuild();
        }
    }

    void resetToGoogleList() {
        MediaApps.reset(requireContext());
        mSelected = MediaApps.get(requireContext());
        if (mApps != null) {
            rebuild();
        }
    }

    private boolean matches(AppEntry app) {
        return mQuery.isEmpty()
                || app.label.toString().toLowerCase(Locale.getDefault()).contains(mQuery)
                || app.packageName.toLowerCase(Locale.getDefault()).contains(mQuery);
    }

    /** Listed apps first; an app only changes group when the list is rebuilt. */
    private void rebuild() {
        Context context = getPreferenceManager().getContext();
        mListedCategory.removeAll();
        mOtherCategory.removeAll();

        for (AppEntry app : mApps) {
            if (!matches(app)) {
                continue;
            }
            AppSwitchPreference preference = new AppSwitchPreference(context);
            preference.setKey(app.packageName);
            preference.setTitle(app.label);
            preference.setIcon(app.icon);
            preference.setPersistent(false);
            preference.setChecked(mSelected.contains(app.packageName));
            preference.setOnPreferenceChangeListener((p, newValue) -> {
                if ((Boolean) newValue) {
                    mSelected.add(app.packageName);
                } else {
                    mSelected.remove(app.packageName);
                }
                MediaApps.put(requireContext(), mSelected);
                return true;
            });
            (mSelected.contains(app.packageName) ? mListedCategory : mOtherCategory)
                    .addPreference(preference);
        }

        mListedCategory.setVisible(mListedCategory.getPreferenceCount() > 0);
        mOtherCategory.setVisible(mOtherCategory.getPreferenceCount() > 0);
        boolean empty = mListedCategory.getPreferenceCount() == 0
                && mOtherCategory.getPreferenceCount() == 0;
        mStatus.setTitle(R.string.motion_sense_media_apps_no_match);
        mStatus.setVisible(empty);
    }
}
