/**
 * Copyright 2010-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *    http://aws.amazon.com/apache2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazonaws.demo.temperaturecontrol;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iotdata.AWSIotDataClient;
import com.amazonaws.services.iotdata.model.GetThingShadowRequest;
import com.amazonaws.services.iotdata.model.GetThingShadowResult;
import com.amazonaws.services.iotdata.model.UpdateThingShadowRequest;
import com.amazonaws.services.iotdata.model.UpdateThingShadowResult;
import com.google.gson.Gson;

import java.nio.ByteBuffer;

public class TemperatureActivity extends Activity {

    private static final String LOG_TAG = TemperatureActivity.class.getCanonicalName();

    // --- Constants to modify per your configuration ---

    // Endpoint Prefix = random characters at the beginning of the custom AWS
    // IoT endpoint
    // describe endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com,
    // endpoint prefix string is XXXXXXX
    private static final String CUSTOMER_SPECIFIC_ENDPOINT_PREFIX = "A1TTLRECU8VD0V";
    // Cognito pool ID. For this app, pool needs to be unauthenticated pool with
    // AWS IoT permissions.
    private static final String COGNITO_POOL_ID = "us-east-1:eeac01ed-153d-4948-ae94-c1b106906eb2";
    // Region of AWS IoT
    private static final Regions MY_REGION = Regions.US_EAST_1;

    CognitoCachingCredentialsProvider credentialsProvider;

    AWSIotDataClient iotDataClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize the Amazon Cognito credentials provider
        credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                COGNITO_POOL_ID, // Identity Pool ID
                MY_REGION // Region
        );

        // endpoint prefix = random characters at the beginning of the custom
        // endpoint
        // describe endpoint call returns:
        // XXXXXXXXXX.iot.<region>.amazonaws.com, endpoint prefix string is
        // XXXXXXX
        iotDataClient = new AWSIotDataClient(credentialsProvider);
        String iotDataEndpoint = String.format("%s.iot.%s.amazonaws.com",
                CUSTOMER_SPECIFIC_ENDPOINT_PREFIX, MY_REGION.getName());
        iotDataClient.setEndpoint(iotDataEndpoint);

        NumberPicker np = (NumberPicker) findViewById(R.id.setpoint);
        np.setMinValue(60);
        np.setMaxValue(80);
        np.setWrapSelectorWheel(false);

        getShadows();
    }

    public void temperatureStatusUpdated(String temperatureStatusState) {
        Gson gson = new Gson();
        TemperatureStatus ts = gson.fromJson(temperatureStatusState, TemperatureStatus.class);

        Log.i(LOG_TAG, String.format("intTemp:  %d", ts.state.reported.room1.set_point));
        Log.i(LOG_TAG, String.format("damper:  %d", ts.state.reported.room1.damper_open));

        TextView intTempText = (TextView) findViewById(R.id.intTemp);
        intTempText.setText(ts.state.reported.room1.set_point.toString());
        if ("stopped".equals(ts.state.reported.room1.set_point)) {
            intTempText.setTextColor(getResources().getColor(R.color.colorOff));
        } else if ("heating".equals(ts.state.reported.room1.set_point)) {
            intTempText.setTextColor(getResources().getColor(R.color.colorHeating));
        } else if ("cooling".equals(ts.state.reported.room1.set_point)) {
            intTempText.setTextColor(getResources().getColor(R.color.colorCooling));
        }

        TextView extTempText = (TextView) findViewById(R.id.extTemp);
        extTempText.setText(ts.state.reported.room1.set_point.toString());
    }

    public void temperatureControlUpdated(String temperatureControlState) {
        Gson gson = new Gson();
        TemperatureControl tc = gson.fromJson(temperatureControlState, TemperatureControl.class);

        Log.i(LOG_TAG, String.format("set_point: %d", tc.state.desired.setPoint));
        Log.i(LOG_TAG, String.format("enabled: %b", tc.state.desired.enabled));

        NumberPicker np = (NumberPicker) findViewById(R.id.setpoint);
        np.setValue(tc.state.desired.setPoint);

        ToggleButton tb = (ToggleButton) findViewById(R.id.enableButton);
        tb.setChecked(tc.state.desired.enabled);
    }

    public void getShadow(View view) {
        getShadows();
    }

    public void getShadows() {
        GetShadowTask getStatusShadowTask = new GetShadowTask("WBHomeServerNew");
        getStatusShadowTask.execute();

        GetShadowTask getControlShadowTask = new GetShadowTask("WBHomeServerNew");
        getControlShadowTask.execute();
    }

    public void enableDisableClicked(View view) {
        ToggleButton tb = (ToggleButton) findViewById(R.id.enableButton);

        Log.i(LOG_TAG, String.format("System %s", tb.isChecked() ? "enabled" : "disabled"));
        UpdateShadowTask updateShadowTask = new UpdateShadowTask();
        updateShadowTask.setThingName("WBHomeServerNew");
        String newState = String.format("{\"state\":{\"reported\":{\"enabled\":%s}}}",
                tb.isChecked() ? "true" : "false");
        Log.i(LOG_TAG, newState);
        updateShadowTask.setState(newState);
        updateShadowTask.execute();
    }

    public void updateset_point(View view) {
        NumberPicker np = (NumberPicker) findViewById(R.id.setpoint);
        Integer newset_point = np.getValue();
        Log.i(LOG_TAG, "New set_point:" + newset_point);
        UpdateShadowTask updateShadowTask = new UpdateShadowTask();
        updateShadowTask.setThingName("WBHomeServerNew");
        String newState = String.format("{\"state\":{\"reported\":{\"set_point\":%d}}}", newset_point);
        Log.i(LOG_TAG, newState);
        updateShadowTask.setState(newState);
        updateShadowTask.execute();
    }

    private class GetShadowTask extends AsyncTask<Void, Void, AsyncTaskResult<String>> {

        private final String thingName;

        public GetShadowTask(String name) {
            thingName = name;
        }

        @Override
        protected AsyncTaskResult<String> doInBackground(Void... voids) {
            try {
                GetThingShadowRequest getThingShadowRequest = new GetThingShadowRequest()
                        .withThingName(thingName);
                GetThingShadowResult result = iotDataClient.getThingShadow(getThingShadowRequest);
                byte[] bytes = new byte[result.getPayload().remaining()];
                result.getPayload().get(bytes);
                String resultString = new String(bytes);
                return new AsyncTaskResult<String>(resultString);
            } catch (Exception e) {
                Log.e("E", "getShadowTask", e);
                return new AsyncTaskResult<String>(e);
            }
        }

        @Override
        protected void onPostExecute(AsyncTaskResult<String> result) {
            if (result.getError() == null) {
                Log.i(GetShadowTask.class.getCanonicalName(), result.getResult());
                if ("TemperatureControl".equals(thingName)) {
                    temperatureControlUpdated(result.getResult());
                } else if ("WBHomeServerNew".equals(thingName)) {
                    temperatureStatusUpdated(result.getResult());
                }
            } else {
                Log.e(GetShadowTask.class.getCanonicalName(), "getShadowTask", result.getError());
            }
        }
    }

    private class UpdateShadowTask extends AsyncTask<Void, Void, AsyncTaskResult<String>> {

        private String thingName;
        private String updateState;

        public void setThingName(String name) {
            thingName = name;
        }

        public void setState(String state) {
            updateState = state;
        }

        @Override
        protected AsyncTaskResult<String> doInBackground(Void... voids) {
            try {
                UpdateThingShadowRequest request = new UpdateThingShadowRequest();
                request.setThingName(thingName);

                ByteBuffer payloadBuffer = ByteBuffer.wrap(updateState.getBytes());
                request.setPayload(payloadBuffer);

                UpdateThingShadowResult result = iotDataClient.updateThingShadow(request);

                byte[] bytes = new byte[result.getPayload().remaining()];
                result.getPayload().get(bytes);
                String resultString = new String(bytes);
                return new AsyncTaskResult<String>(resultString);
            } catch (Exception e) {
                Log.e(UpdateShadowTask.class.getCanonicalName(), "updateShadowTask", e);
                return new AsyncTaskResult<String>(e);
            }
        }

        @Override
        protected void onPostExecute(AsyncTaskResult<String> result) {
            if (result.getError() == null) {
                Log.i(UpdateShadowTask.class.getCanonicalName(), result.getResult());
            } else {
                Log.e(UpdateShadowTask.class.getCanonicalName(), "Error in Update Shadow",
                        result.getError());
            }
        }
    }
}
