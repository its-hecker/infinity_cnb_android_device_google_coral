/*
 * SPDX-FileCopyrightText: 2026 its-hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.motionsense;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import org.lineageos.settings.R;

public class MediaAppsActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG_MEDIA_APPS = "media_apps";
    private static final int MENU_SEARCH = 1;
    private static final int MENU_RESET = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new MediaAppsFragment(), TAG_MEDIA_APPS)
                    .commit();
        }
    }

    private MediaAppsFragment getFragment() {
        return (MediaAppsFragment) getSupportFragmentManager().findFragmentByTag(TAG_MEDIA_APPS);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem search = menu.add(Menu.NONE, MENU_SEARCH, Menu.NONE,
                R.string.motion_sense_media_apps_search);
        search.setIcon(R.drawable.ic_motion_sense_search);
        search.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS
                | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        SearchView searchView = new SearchView(this);
        searchView.setQueryHint(getString(R.string.motion_sense_media_apps_search));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                MediaAppsFragment fragment = getFragment();
                if (fragment != null) {
                    fragment.setQuery(newText);
                }
                return true;
            }
        });
        search.setActionView(searchView);

        menu.add(Menu.NONE, MENU_RESET, Menu.NONE, R.string.motion_sense_media_apps_reset)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_RESET) {
            MediaAppsFragment fragment = getFragment();
            if (fragment != null) {
                fragment.resetToGoogleList();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
