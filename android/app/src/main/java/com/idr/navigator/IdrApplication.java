package com.idr.navigator;

public final class IdrApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        com.idr.navigator.engine.EngineDiagnostics.install(this);
        try { com.idr.navigator.engine.ModelInstaller.recover(this); }
        catch(Exception e) { com.idr.navigator.engine.EngineDiagnostics.failure(this,"Model recovery",e); }
        com.idr.navigator.cloud.CloudScheduler.startup(this);
    }
}
