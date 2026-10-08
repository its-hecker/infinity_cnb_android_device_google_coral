package org.lineageos.settings.motionsense.lab;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

/** Foreground-only clients for the stock Oslo binder protocol, including echo de-duplication. */
final class LabGestureClient {
    interface Callback {
        void gesture(int kind, int side, float confidence);
        void connection(boolean connected);
    }
    static final int SWIPE = 1, TAP = 2, REACH = 3;
    private static final String SERVICE = "com.google.oslo.service.serviceinterface.aidl.IOsloService";
    private static final String LISTENER = "com.google.oslo.service.serviceinterface.aidl.IOsloServiceGestureListener";
    private final Context context;
    private final Callback callback;
    private final Binder token = new Binder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener flick = new Listener(SWIPE), tap = new Listener(TAP), reach = new Listener(REACH);
    private IBinder binder;
    private boolean active, bound, registered;
    private int lastKind, lastSide;
    private long lastTime = -1;
    LabGestureClient(Context owner, Callback target) { context = owner; callback = target; }
    void start() { if (active) return; active = true; bind(); }
    void stop() {
        active = false; main.removeCallbacksAndMessages(null); unregister();
        binder = null; unbind(); callback.connection(false); lastTime = -1;
    }
    private void unbind() {
        if (bound) { try { context.unbindService(connection); } catch (RuntimeException ignored) {} }
        bound = false;
    }
    private void reconnect() {
        registered = false; binder = null; callback.connection(false);
        if (active) { main.removeCallbacks(retry); main.postDelayed(retry, 1250); }
    }
    private final Runnable retry = () -> { if (active) { unbind(); bind(); } };
    private void bind() {
        try {
            bound = context.bindService(new Intent().setComponent(new ComponentName("com.google.oslo",
                    "com.google.oslo.service.OsloService")), connection, Context.BIND_AUTO_CREATE);
            if (!bound) reconnect();
        } catch (RuntimeException e) { bound = false; reconnect(); }
    }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            if (!active) return;
            binder = service;
            try {
                registered = true; // permits cleanup after a partial registration
                for (int type : new int[] {1, 2, 9, 10, 4, 5}) {
                    Listener listener = type <= 2 ? flick : type >= 9 ? tap : reach;
                    Bundle config = new Bundle();
                    config.putString("id", "org.lineageos.settings.lab." + type);
                    config.putFloat("radius", 1f); config.putInt("sensitivity", 1);
                    config.putInt("granularity", 3); config.putInt("priority", 1);
                    Parcel data = Parcel.obtain(), reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(SERVICE); data.writeStrongBinder(token);
                        data.writeStrongBinder(listener); data.writeInt(type); data.writeTypedObject(config, 0);
                        if (!binder.transact(1, data, reply, 0)) throw new RemoteException();
                        reply.readException();
                    } finally { data.recycle(); reply.recycle(); }
                }
                callback.connection(true);
            } catch (RemoteException | RuntimeException e) { unregister(); reconnect(); }
        }
        @Override public void onServiceDisconnected(ComponentName name) { reconnect(); }
        @Override public void onBindingDied(ComponentName name) { reconnect(); }
        @Override public void onNullBinding(ComponentName name) { reconnect(); }
    };
    private void unregister() {
        if (binder != null && registered) {
            // Oslo removes both regular and echo registrations by listener binder.
            for (Listener listener : new Listener[] {flick, tap, reach}) {
                Parcel data = Parcel.obtain(), reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(SERVICE); data.writeStrongBinder(token);
                    data.writeStrongBinder(listener);
                    if (binder.transact(2, data, reply, 0)) reply.readException();
                } catch (RemoteException | RuntimeException ignored) {}
                finally { data.recycle(); reply.recycle(); }
            }
        }
        registered = false;
    }
    private final class Listener extends Binder implements IInterface {
        private final int kind;
        Listener(int value) { kind = value; attachInterface(this, LISTENER); }
        @Override public IBinder asBinder() { return this; }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(LISTENER); return true;
            }
            if (code != 1) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(LISTENER);
            Bundle event = data.readTypedObject(Bundle.CREATOR);
            if (event != null && event.getBoolean("detected", false)) {
                int side = kind == SWIPE ? LabEngine.physicalSide(event.getInt("direction", 0)) : 0;
                float likelihood = event.getFloat("likelihood", 0);
                main.post(() -> {
                    if (!active || (kind == SWIPE && side == 0)) return;
                    long now = SystemClock.elapsedRealtime();
                    if (lastTime >= 0 && now-lastTime < 250 && lastKind == kind && lastSide == side) return;
                    lastTime = now; lastKind = kind; lastSide = side;
                    callback.gesture(kind, side, likelihood);
                });
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    }
}
