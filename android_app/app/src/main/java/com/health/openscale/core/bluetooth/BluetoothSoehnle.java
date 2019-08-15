/* Copyright (C) 2019 olie.xdev <olie.xdev@googlemail.com>
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>
*/

package com.health.openscale.core.bluetooth;

import android.content.Context;

import com.health.openscale.core.datatypes.ScaleMeasurement;
import com.health.openscale.core.utils.Converters;
import com.welie.blessed.BluetoothBytesParser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import timber.log.Timber;

public class BluetoothSoehnle extends BluetoothCommunication {
    public BluetoothSoehnle(Context context) {
        super(context);
    }

    @Override
    public String driverName() {
        return "Soehnle Shape Scale";
    }

    @Override
    protected boolean onNextStep(int stepNr) {
        switch (stepNr) {
            case 0:
                // Turn on notification for Weight Service
                setNotificationOn(BluetoothGattUuid.SERVICE_WEIGHT_SCALE, BluetoothGattUuid.CHARACTERISTIC_WEIGHT_MEASUREMENT);
                break;
            case 1:
                // Turn on notification for Body Composition Service
                setNotificationOn(BluetoothGattUuid.SERVICE_BODY_COMPOSITION, BluetoothGattUuid.CHARACTERISTIC_BODY_COMPOSITION_MEASUREMENT);
                break;

            default:
                return false;
        }

        return true;
    }

    @Override
    public void onBluetoothNotify(UUID characteristic, byte[] value) {
        Timber.d("on bluetooth notify change " + byteInHex(value) + " on " + characteristic.toString());

        if (value != null && value.length >= 14 ) {
            float weight = Converters.fromUnsignedInt16Be(value, 9) / 10.0f; // kg
            final int year = ((value[3] & 0xFF) << 8) | (value[2] & 0xFF);
            final int month = (int) value[4];
            final int day = (int) value[5];
            final int hours = (int) value[6];
            final int min = (int) value[7];
            final int sec = (int) value[8];

            String date_string = year + "/" + month + "/" + day + "/" + hours + "/" + min;
            try {
                Date date_time = new SimpleDateFormat("yyyy/MM/dd/HH/mm").parse(date_string);
            } catch (ParseException e) {
                Timber.e("parse error " + e.getMessage());
            }

            Timber.d("notfiy weight " + weight);
            Timber.d("notfiy time "+ date_string);
        }

        if(characteristic.equals(BluetoothGattUuid.CHARACTERISTIC_WEIGHT_MEASUREMENT)) {
            handleWeightMeasurement(value);
        }
        else if(characteristic.equals(BluetoothGattUuid.CHARACTERISTIC_BODY_COMPOSITION_MEASUREMENT)) {
            handleBodyCompositionMeasurement(value);
        }
    }

    private void handleWeightMeasurement(byte[] value) {
        BluetoothBytesParser parser = new BluetoothBytesParser(value);
        final int flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8);
        boolean isKg = (flags & 0x01) == 0;
        final boolean timestampPresent = (flags & 0x02) > 0;
        final boolean userIDPresent = (flags & 0x04) > 0;
        final boolean bmiAndHeightPresent = (flags & 0x08) > 0;

        ScaleMeasurement scaleMeasurement = new ScaleMeasurement();

        // Determine the right weight multiplier
        float weightMultiplier = isKg ? 0.005f : 0.01f;

        // Get weight
        float weightValue = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) * weightMultiplier;
        scaleMeasurement.setWeight(weightValue);

        if(timestampPresent) {
            Date timestamp = parser.getDateTime();
            scaleMeasurement.setDateTime(timestamp);
            Timber.d("timestamp is present");
        }

        if(userIDPresent) {
            int userID = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8);
            Timber.d(String.format("User id: %i", userID));
        }

        if(bmiAndHeightPresent) {
            float BMI = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) * 0.1f;
            float heightInMeters = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) * 0.001f;
            Timber.d("BMI " + BMI);
            Timber.d("heightinMeters " + heightInMeters);
        }

        Timber.d(String.format("Got weight: %s", weightValue));
    }

    private void handleBodyCompositionMeasurement(byte[] value) {
        BluetoothBytesParser parser = new BluetoothBytesParser(value);
        final int flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16);
        boolean isKg = (flags & 0x0001) == 0;
        float massMultiplier = (float) (isKg ? 0.005 : 0.01);
        boolean timestampPresent = (flags & 0x0002) > 0;
        boolean userIDPresent = (flags & 0x0004) > 0;
        boolean bmrPresent = (flags & 0x0008) > 0;
        boolean musclePercentagePresent = (flags & 0x0010) > 0;
        boolean muscleMassPresent = (flags & 0x0020) > 0;
        boolean fatFreeMassPresent = (flags & 0x0040) > 0;
        boolean softLeanMassPresent = (flags & 0x0080) > 0;
        boolean bodyWaterMassPresent = (flags & 0x0100) > 0;
        boolean impedancePresent = (flags & 0x0200) > 0;
        boolean weightPresent = (flags & 0x0400) > 0;
        boolean heightPresent = (flags & 0x0800) > 0;
        boolean multiPacketMeasurement = (flags & 0x1000) > 0;

        float bodyFatPercentage = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) * 0.1f;

        // Read timestamp if present
        if (timestampPresent) {
            Date timestamp = parser.getDateTime();
            Timber.d("timestamp is present");
        }

        // Read userID if present
        if (userIDPresent) {
            int userID = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8);
            Timber.d(String.format("user id: %i", userID));
        }

        // Read bmr if present
        if (bmrPresent) {
            int bmrInJoules = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16);
            int bmrInKcal = Math.round(((bmrInJoules / 4.1868f) * 10.0f) / 10.0f);
        }

        // Read musclePercentage if present
        if (musclePercentagePresent) {
            float musclePercentage = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) * 0.1f;
            Timber.d("muscle percentage is present");
        }

        // Read muscleMass if present
        if (muscleMassPresent) {
            float muscleMass = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) * massMultiplier;
            Timber.d("muscle mass is present");
        }

        // Read fatFreeMassPresent if present
        if (fatFreeMassPresent) {
            float fatFreeMass = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) * massMultiplier;
            Timber.d("fat free mass is present");
        }

        // Read softleanMass if present
        if (softLeanMassPresent) {
            float softLeanMass = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) * massMultiplier;
            Timber.d("soft lean mass is present");
        }

        // Read bodyWaterMass if present
        if (bodyWaterMassPresent) {
            float bodyWaterMass = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) * massMultiplier;
            Timber.d("body water mass is present");
        }

        // Read impedance if present
        if (impedancePresent) {
            float impedance = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) * 0.1f;
            Timber.d("impedance is present");
        }

        // Read weight if present
        if (weightPresent) {
            float weightValue = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) * massMultiplier;
            Timber.d("weight value is present");
        }

        // Read height if present
        if (heightPresent) {
            float heightValue = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16);
            Timber.d("height value is present");
        }

        Timber.d(String.format("Got body composition: %s", byteInHex(value)));
    }
}
