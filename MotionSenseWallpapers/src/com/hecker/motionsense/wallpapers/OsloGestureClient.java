/*
 * SPDX-FileCopyrightText: 2026 Hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hecker.motionsense.wallpapers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

/**
 * Small binder client for the Pixel 4 Oslo service.
 *
 * It keeps low-priority regular subscriptions as a fallback so the CHRE gesture stays enabled
 * when no other client needs it, and echo subscriptions for gestures that support them. Echo
 * callbacks let Sidekick animate without stealing media/control gestures from foreground clients.
 */
final class OsloGestureClient {
    interface Callback {
        void onPresence();
        void onReach();
        void onFlick(int direction);
        void onSwipe(int direction);
    }

    private static final String TAG = "Sidekick/Oslo";
    private static final String OSLO_PACKAGE = "com.google.oslo";
    private static final String OSLO_SERVICE = "com.google.oslo.service.OsloService";
    private static final String SERVICE_DESCRIPTOR =
            "com.google.oslo.service.serviceinterface.aidl.IOsloService";
    private static final String LISTENER_DESCRIPTOR =
            "com.google.oslo.service.serviceinterface.aidl.IOsloServiceGestureListener";

    private static final int TRANSACTION_REGISTER = 1;
    private static final int TRANSACTION_UNREGISTER = 2;
    private static final int TRANSACTION_GESTURE = 1;

    private static final int FLICK = 1;
    private static final int FLICK_ECHO = 2;
    private static final int PRESENCE = 3;
    private static final int REACH = 4;
    private static final int REACH_ECHO = 5;
    private static final int SWIPE = 7;
    private static final int SWIPE_ECHO = 8;

    // Oslo priority 1 is LOW. This keeps Sidekick behind foreground/ambient system clients.
    private static final int PRIORITY_LOW = 1;

    private final Context mContext;
    private final Callback mCallback;
    private final IBinder mToken = new Binder();
    private final GestureListener mListener = new GestureListener();
    private final android.os.Handler mMainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private IBinder mService;
    private boolean mStarted;
    private boolean mBound;
    private boolean mRegistered;

    OsloGestureClient(Context context, Callback callback) {
        mContext = context;
        mCallback = callback;
    }

    void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(OSLO_PACKAGE, OSLO_SERVICE));
        try {
            mBound = mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            if (!mBound) {
                Log.w(TAG, "Oslo service is not available");
            }
        } catch (RuntimeException e) {
            mBound = false;
            Log.w(TAG, "Unable to bind to Oslo service", e);
        }
    }

    void stop() {
        if (!mStarted && !mBound && mService == null) {
            return;
        }
        mStarted = false;
        unregister();
        mService = null;

        if (mBound) {
            try {
                mContext.unbindService(mConnection);
            } catch (IllegalArgumentException ignored) {
                // Service disappeared while the wallpaper was being hidden.
            }
            mBound = false;
        }
    }

    void destroy() {
        stop();
        mMainHandler.removeCallbacksAndMessages(null);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!mStarted) {
                return;
            }
            mService = service;
            register();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRegistered = false;
            mService = null;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            mRegistered = false;
            mService = null;
        }

        @Override
        public void onNullBinding(ComponentName name) {
            mRegistered = false;
            mService = null;
        }
    };

    private void register() {
        if (mService == null || mRegistered) {
            return;
        }

        try {
            // Mark first so a failure after a partial registration still cleans every listener.
            mRegistered = true;
            // Low-priority regular subscriptions keep a gesture alive only when the system does
            // not already have a higher-priority subscriber.
            registerOne(FLICK, config("flick"));
            registerOne(PRESENCE, config("presence"));
            registerOne(REACH, config("reach"));
            registerOne(SWIPE, config("swipe"));

            // Echo subscriptions observe active gestures even when a foreground Oslo client owns
            // the regular slot. Presence has no echo gesture in the Pixel 4 Oslo service.
            registerOne(FLICK_ECHO, config("flick.echo"));
            registerOne(REACH_ECHO, config("reach.echo"));
            registerOne(SWIPE_ECHO, config("swipe.echo"));
            Log.d(TAG, "Motion Sense Sidekick listeners registered");
        } catch (RemoteException | SecurityException e) {
            Log.w(TAG, "Unable to register Motion Sense wallpaper listeners", e);
            unregister();
        }
    }

    private static Bundle config(String id) {
        Bundle b = new Bundle();
        b.putString("id", "com.hecker.motionsense.wallpapers." + id);
        b.putFloat("radius", 1.0f);
        b.putInt("sensitivity", 1);
        b.putInt("granularity", 3);
        b.putInt("priority", PRIORITY_LOW);
        return b;
    }

    private void registerOne(int type, Bundle config) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeStrongBinder(mToken);
            data.writeStrongBinder(mListener);
            data.writeInt(type);
            data.writeTypedObject(config, 0);
            if (!mService.transact(TRANSACTION_REGISTER, data, reply, 0)) {
                throw new RemoteException("Oslo register transaction failed");
            }
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void unregister() {
        if (mService == null || !mRegistered) {
            mRegistered = false;
            return;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeStrongBinder(mToken);
            data.writeStrongBinder(mListener);
            if (mService.transact(TRANSACTION_UNREGISTER, data, reply, 0)) {
                reply.readException();
            }
        } catch (RemoteException | RuntimeException e) {
            Log.d(TAG, "Oslo listener already gone", e);
        } finally {
            reply.recycle();
            data.recycle();
            mRegistered = false;
        }
    }

    private final class GestureListener extends Binder implements IInterface {
        GestureListener() {
            attachInterface(this, LISTENER_DESCRIPTOR);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) {
                    reply.writeString(LISTENER_DESCRIPTOR);
                }
                return true;
            }
            if (code != TRANSACTION_GESTURE) {
                return super.onTransact(code, data, reply, flags);
            }

            data.enforceInterface(LISTENER_DESCRIPTOR);
            Bundle output = data.readTypedObject(Bundle.CREATOR);
            if (output == null || !output.getBoolean("detected", false)) {
                return true;
            }

            final int direction = output.getInt("direction", 0);
            final boolean directional = output.containsKey("direction");
            final boolean hasAxialVelocity = output.containsKey("axialVelocity");
            final Object angle = output.get("angle");

            mMainHandler.post(() -> {
                if (!mStarted) {
                    return;
                }

                // Flick output has direction but no axialVelocity. Swipe has both.
                if (directional) {
                    if (hasAxialVelocity) {
                        mCallback.onSwipe(direction);
                    } else {
                        mCallback.onFlick(direction);
                    }
                    return;
                }

                // Reach carries float[] angle; Presence carries a scalar angle.
                if (angle instanceof float[]) {
                    mCallback.onReach();
                } else if (angle instanceof Float) {
                    mCallback.onPresence();
                } else if (hasAxialVelocity) {
                    // Be tolerant of vendor bundles that omit the angle key.
                    mCallback.onReach();
                }
            });
            return true;
        }
    }
}
