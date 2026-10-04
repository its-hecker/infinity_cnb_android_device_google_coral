/*
 * SPDX-FileCopyrightText: 2026 Hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hecker.motionsense.wallpapers;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** About the wallpapers: the developer, how to get in touch and how to support the work. */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);

        findViewById(R.id.contact).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.contact_url))));
            } catch (ActivityNotFoundException e) {
                copy(getString(R.string.contact_url));
            }
        });
        findViewById(R.id.copy_binance).setOnClickListener(v -> copy(getString(R.string.binance_id)));
        findViewById(R.id.copy_usdt).setOnClickListener(v -> copy(getString(R.string.usdt_trc20)));
    }

    private void copy(String text) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(text, text));
            Toast.makeText(this, R.string.about_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
