package com.idr.navigator;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(com.idr.navigator.plugins.DrEnginePlugin.class);
        registerPlugin(com.idr.navigator.plugins.SensorPlugin.class);
        registerPlugin(com.idr.navigator.plugins.ModelPlugin.class);
        registerPlugin(com.idr.navigator.plugins.DirectionsPlugin.class);
        registerPlugin(com.idr.navigator.plugins.CloudPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
