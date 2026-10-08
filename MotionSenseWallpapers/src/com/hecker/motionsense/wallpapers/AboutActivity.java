/*
 * SPDX-FileCopyrightText: 2026 Hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hecker.motionsense.wallpapers;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** About and entry point for Sidekick plus Google's original Pokémon live wallpaper. */
public class AboutActivity extends Activity {

    private static final ComponentName GOOGLE_POKEMON = new ComponentName(
            "com.google.pixel.livewallpaper",
            "com.google.pixel.livewallpaper.pokemon.wallpapers.PokemonWallpaper");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);

        findViewById(R.id.open_pokemon).setOnClickListener(v -> openPokemon());
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

    private void openPokemon() {
        Intent direct = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        direct.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, GOOGLE_POKEMON);
        try {
            startActivity(direct);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, R.string.pokemon_unavailable, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void copy(String text) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(text, text));
            Toast.makeText(this, R.string.about_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
