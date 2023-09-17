package com.flashphoner.wcsexample.phone_min;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.flashphoner.fpwcsapi.Flashphoner;
import com.flashphoner.fpwcsapi.bean.Connection;
import com.flashphoner.fpwcsapi.bean.Data;
import com.flashphoner.fpwcsapi.constraints.AudioConstraints;
import com.flashphoner.fpwcsapi.session.Call;
import com.flashphoner.fpwcsapi.session.CallOptions;
import com.flashphoner.fpwcsapi.session.CallStatusEvent;
import com.flashphoner.fpwcsapi.session.IncomingCallEvent;
import com.flashphoner.fpwcsapi.session.Session;
import com.flashphoner.fpwcsapi.session.SessionEvent;
import com.flashphoner.fpwcsapi.session.SessionOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.webrtc.MediaConstraints;

import java.util.Map;

/**
 * Example of a SIP phone built-in into a mobile app
 */
public class PhoneMinActivity extends AppCompatActivity {

    private static final int CALL_REQUEST_CODE = 100;
    private static final int INCOMING_CALL_REQUEST_CODE = 101;
    private static String TAG = PhoneMinActivity.class.getName();
    // UI references.
    private EditText mWcsUrlView;
    private EditText mSipLoginView;
    private EditText mSipPasswordView;
    private EditText mSipDomainView;
    private EditText mSipPortView;
    private EditText mAuthTokenView;
    private CheckBox mSipRegisterRequiredView;
    private TextView mConnectStatus;
    private Button mConnectButton;
    private Button mConnectTokenButton;
    private EditText mCalleeView;
    private EditText mInviteParametersView;

    private CheckBox googEchoCancellation;
    private CheckBox googAutoGainControl;
    private CheckBox googNoiseSupression;
    private CheckBox googHighpassFilter;
    private CheckBox googEchoCancellation2;
    private CheckBox googAutoGainControl2;
    private CheckBox googNoiseSuppression2;

    private CheckBox mProximitySensor;
    private CheckBox mSpeakerPhone;

    private TextView mCallStatus;
    private Button mCallButton;
    private Button mHoldButton;

    private EditText mDTMF;
    private Button mDTMFButton;

    /**
     * Associated session with WCS
     */
    private Session session;

    /**
     * SIP call
     */
    private Call call;

    /**
     * Processing the call status and displaying UI changes
     */
    private CallStatusEvent callStatusEvent;

    /**
     * UI alert for incoming call
     */
    private AlertDialog incomingCallAlert;

    private boolean connectWithToken = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_streaming_min);

        TextView policyTextView = (TextView) findViewById(R.id.privacy_policy);
        policyTextView.setMovementMethod(LinkMovementMethod.getInstance());
        String policyLink ="<a href=https://flashphoner.com/flashphoner-privacy-policy-for-android-tools/>Privacy Policy</a>";
        policyTextView.setText(Html.fromHtml(policyLink));

        /**
         * Initialization of the API.
         */
        Flashphoner.init(this);

        /**
         * UI controls
         */
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        mWcsUrlView = (EditText) findViewById(R.id.wcs_url);
        mWcsUrlView.setText(sharedPref.getString("wcs_url", getString(R.string.wcs_url)));
        mSipLoginView = (EditText) findViewById(R.id.sip_login);
        mSipLoginView.setText(sharedPref.getString("sip_login", getString(R.string.sip_login)));
        mSipPasswordView = (EditText) findViewById(R.id.sip_password);
        mSipPasswordView.setText(sharedPref.getString("sip_password", getString(R.string.sip_password)));
        mSipDomainView = (EditText) findViewById(R.id.sip_domain);
        mSipDomainView.setText(sharedPref.getString("sip_domain", getString(R.string.sip_domain)));
        mSipPortView = (EditText) findViewById(R.id.sip_port);
        mSipPortView.setText(sharedPref.getString("sip_port", getString(R.string.sip_port)));
        mSipRegisterRequiredView = (CheckBox) findViewById(R.id.register_required);
        mSipRegisterRequiredView.setChecked(sharedPref.getBoolean("sip_register_required", true));
        mAuthTokenView = (EditText) findViewById(R.id.auth_token);

        callStatusEvent = new CallStatusEvent() {
            /**
             * WCS received SIP 100 TRYING
             * @param call
             */
            @Override
            public void onTrying(final Call call) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallButton.setText(R.string.action_hangup);
                        mCallButton.setTag(R.string.action_hangup);
                        mCallButton.setEnabled(true);
                        mCallStatus.setText(call.getStatus());
                    }
                });
            }

            /**
             * WCS received SIP BUSY_HERE or BUSY_EVERYWHERE from SIP
             * @param call
             */
            @Override
            public void onBusy(final Call call) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallButton.setText(R.string.action_call);
                        mCallButton.setTag(R.string.action_call);
                        mCallButton.setEnabled(true);
                        mCallStatus.setText(call.getStatus());
                    }
                });
            }

            /**
             * Call is failed
             * @param call
             */
            @Override
            public void onFailed(Call call) {

            }

            /**
             * WCS received SIP 180 RINGING from SIP
             * @param call
             */
            @Override
            public void onRing(final Call call) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallButton.setText(R.string.action_hangup);
                        mCallButton.setTag(R.string.action_hangup);
                        mCallButton.setEnabled(true);
                        mCallStatus.setText(call.getStatus());
                    }
                });


            }

            /**
             * Call is set on hold
             * @param call
             */
            @Override
            public void onHold(final Call call) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallStatus.setText(call.getStatus());
                    }
                });
            }

            /**
             * Call established
             * @param call
             */
            @Override
            public void onEstablished(final Call call) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallStatus.setText(call.getStatus());
                        mHoldButton.setEnabled(true);
                        mDTMFButton.setEnabled(true);
                    }
                });

            }

            /**
             * Call is terminated
             * @param call
             */
            @Override
            public void onFinished(final Call call) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallButton.setText(R.string.action_call);
                        mCallButton.setTag(R.string.action_call);
                        mCallButton.setEnabled(true);
                        mCallStatus.setText(call.getStatus());
                        mHoldButton.setText(R.string.action_hold);
                        mHoldButton.setTag(R.string.action_hold);
                        mHoldButton.setEnabled(false);
                        mDTMFButton.setEnabled(false);
                        if (incomingCallAlert != null) {
                            incomingCallAlert.hide();
                            incomingCallAlert = null;
                        }
                    }
                });

            }
        };

        mConnectStatus = (TextView) findViewById(R.id.connect_status);
        mConnectButton = (Button) findViewById(R.id.connect_button);
        /**
         * Connect button pressed
         */
        mConnectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                if (mConnectButton.getTag() == null || Integer.valueOf(R.string.action_connect).equals(mConnectButton.getTag())) {
                    connectWithToken = false;
                    mConnectButton.setEnabled(false);
                    mConnectTokenButton.setEnabled(false);
                    createSession();
                    /**
                     * Connection containing SIP details
                     */
                    Connection connection = new Connection();
                    connection.setSipLogin(mSipLoginView.getText().toString());
                    connection.setSipPassword(mSipPasswordView.getText().toString());
                    connection.setSipDomain(mSipDomainView.getText().toString());
                    connection.setSipOutboundProxy(mSipDomainView.getText().toString());
                    connection.setSipPort(Integer.parseInt(mSipPortView.getText().toString()));
                    connection.setSipRegisterRequired(mSipRegisterRequiredView.isChecked());
                    connection.setKeepAlive(true);
                    session.connect(connection);

                    SharedPreferences sharedPref = PhoneMinActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("wcs_url", mWcsUrlView.getText().toString());
                    editor.putString("sip_login", mSipLoginView.getText().toString());
                    editor.putString("sip_password", mSipPasswordView.getText().toString());
                    editor.putString("sip_domain", mSipDomainView.getText().toString());
                    editor.putString("sip_port", mSipPortView.getText().toString());
                    editor.putBoolean("sip_register_required", mSipRegisterRequiredView.isChecked());
                    editor.apply();
                } else {
                    mConnectButton.setEnabled(false);
                    mConnectTokenButton.setEnabled(false);
                    session.disconnect();
                }
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        mConnectTokenButton = (Button) findViewById(R.id.connect_token_button);
        mConnectTokenButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mConnectTokenButton.getTag() == null || Integer.valueOf(R.string.action_connect_token).equals(mConnectTokenButton.getTag())) {
                    connectWithToken = true;
                    String authToken = mAuthTokenView.getText().toString();
                    if (authToken.isEmpty()) {
                        return;
                    }
                    mConnectButton.setEnabled(false);
                    mConnectTokenButton.setEnabled(false);
                    createSession();
                    Connection connection = new Connection();
                    connection.setAuthToken(authToken);
                    connection.setKeepAlive(true);
                    session.connect(connection);
                } else {
                    mConnectButton.setEnabled(false);
                    mConnectTokenButton.setEnabled(false);
                    session.disconnect();
                }
            }
        });
        mConnectTokenButton.setEnabled(false);

        mCalleeView = (EditText) findViewById(R.id.callee);
        mCalleeView.setText(sharedPref.getString("callee", getString(R.string.default_callee_name)));

        mInviteParametersView = (EditText) findViewById(R.id.invite_parameters);

        googEchoCancellation = (CheckBox) findViewById(R.id.googEchoCancellationCB);
        googAutoGainControl = (CheckBox) findViewById(R.id.googAutoGainControlCB);
        googNoiseSupression = (CheckBox) findViewById(R.id.googNoiseSupressionCB);
        googHighpassFilter = (CheckBox) findViewById(R.id.googHighpassFilterCB);
        googEchoCancellation2 = (CheckBox) findViewById(R.id.googEchoCancellation2CB);
        googAutoGainControl2 = (CheckBox) findViewById(R.id.googAutoGainControl2CB);
        googNoiseSuppression2 = (CheckBox) findViewById(R.id.googNoiseSuppression2CB);

        mProximitySensor = (CheckBox) findViewById(R.id.proximitySensor);
        mProximitySensor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Flashphoner.getAudioManager().setUseProximitySensor(isChecked);
            }
        });
        mSpeakerPhone = (CheckBox) findViewById(R.id.speakerPhone);
        mSpeakerPhone.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Flashphoner.getAudioManager().setUseSpeakerPhone(isChecked);
            }
        });

        mCallStatus = (TextView) findViewById(R.id.call_status);
        mCallButton = (Button) findViewById(R.id.call_button);
        /**
         * Call button pressed
         */
        mCallButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCallButton.getTag() == null || Integer.valueOf(R.string.action_call).equals(mCallButton.getTag())) {
                    if ("".equals(mCalleeView.getText().toString())) {
                        return;
                    }
                    ActivityCompat.requestPermissions(PhoneMinActivity.this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            CALL_REQUEST_CODE);

                    SharedPreferences sharedPref = PhoneMinActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("callee", mCalleeView.getText().toString());
                    editor.apply();
                } else {
                    mCallButton.setEnabled(false);
                    call.hangup();
                    call = null;
                }
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        mHoldButton = (Button) findViewById(R.id.hold_button);
        /**
         * Hold or Unhold button pressed
         * Hold the call if the call is ESTABLISHED.
         * Unhold the call if the call is on hold.
         */
        mHoldButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mHoldButton.getTag() == null || Integer.valueOf(R.string.action_hold).equals(mHoldButton.getTag())) {
                    call.hold();
                    mHoldButton.setText(R.string.action_unhold);
                    mHoldButton.setTag(R.string.action_unhold);
                } else {
                    call.unhold();
                    mHoldButton.setText(R.string.action_hold);
                    mHoldButton.setTag(R.string.action_hold);
                }

            }
        });

        mDTMF = (EditText) findViewById(R.id.dtmf);
        mDTMFButton = (Button) findViewById(R.id.dtmf_button);
        mDTMFButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (call != null) {
                    call.sendDTMF(mDTMF.getText().toString(), Call.DTMFType.RFC2833);
                }
            }
        });
    }

    private void createSession() {
        SessionOptions sessionOptions = new SessionOptions(mWcsUrlView.getText().toString());
        session = Flashphoner.createSession(sessionOptions);
        session.on(new SessionEvent() {
            @Override
            public void onAppData(Data data) {

            }

            /**
             * Connection established
             * @param connection Current connection state
             */
            @Override
            public void onConnected(final Connection connection) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mConnectButton.setText(R.string.action_disconnect);
                        mConnectButton.setTag(R.string.action_disconnect);
                        mConnectButton.setEnabled(true);
                        mConnectTokenButton.setText(R.string.action_disconnect);
                        mConnectTokenButton.setTag(R.string.action_disconnect);
                        mConnectTokenButton.setEnabled(true);
                        if (!mSipRegisterRequiredView.isChecked() || connectWithToken) {
                            mConnectStatus.setText(connection.getStatus());
                            mCallButton.setEnabled(true);
                        } else {
                            mConnectStatus.setText(connection.getStatus() + ". Registering...");
                        }
                        String token = connection.getAuthToken();
                        if (token != null && !token.isEmpty()) {
                            mAuthTokenView.setText(token);
                            mConnectTokenButton.setEnabled(true);
                        }
                    }
                });
            }

            /**
             * Phone registered
             * @param connection Current connection state
             */
            @Override
            public void onRegistered(final Connection connection) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mConnectStatus.setText(connection.getStatus());
                        mCallButton.setEnabled(true);
                    }
                });
            }

            /**
             * Phone disconnected
             * @param connection Current connection state
             */
            @Override
            public void onDisconnection(final Connection connection) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mConnectButton.setText(R.string.action_connect);
                        mConnectButton.setTag(R.string.action_connect);
                        mConnectButton.setEnabled(true);
                        mConnectTokenButton.setText(R.string.action_connect_token);
                        mConnectTokenButton.setTag(R.string.action_connect_token);
                        mConnectTokenButton.setEnabled(true);
                        mConnectStatus.setText(connection.getStatus());
                        mCallButton.setText(R.string.action_call);
                        mCallButton.setTag(R.string.action_call);
                        mCallButton.setEnabled(false);
                        mCallStatus.setText("");
                        mHoldButton.setText(R.string.action_hold);
                        mHoldButton.setTag(R.string.action_hold);
                        mHoldButton.setEnabled(false);
                        mDTMFButton.setEnabled(false);
                    }
                });
            }
        });

        /**
         * Add handler for incoming call
         */
        session.on(new IncomingCallEvent() {
            @Override
            public void onCall(final Call call) {
                call.on(callStatusEvent);
                /**
                 * Display UI alert for the new incoming call
                 */
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(PhoneMinActivity.this);

                        builder.setTitle("Incoming call");

                        builder.setMessage("Incoming call from '" + call.getCaller() + "'");
                        builder.setPositiveButton("Answer", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                PhoneMinActivity.this.call = call;
                                ActivityCompat.requestPermissions(PhoneMinActivity.this,
                                        new String[]{Manifest.permission.RECORD_AUDIO},
                                        INCOMING_CALL_REQUEST_CODE);
                            }
                        });
                        builder.setNegativeButton("Hangup", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                call.hangup();
                                incomingCallAlert = null;
                            }
                        });
                        incomingCallAlert = builder.show();
                    }
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CALL_REQUEST_CODE: {
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    mCallButton.setEnabled(false);
                    /**
                     * Get call options from the callee text field
                     */
                    CallOptions callOptions = new CallOptions(mCalleeView.getText().toString());
                    AudioConstraints audioConstraints = callOptions.getConstraints().getAudioConstraints();
                    MediaConstraints mediaConstraints = audioConstraints.getMediaConstraints();

                    mediaConstraints.optional.add(
                            new MediaConstraints.KeyValuePair("googEchoCancellation", Boolean.toString(googEchoCancellation.isChecked())));
                    mediaConstraints.optional.add(
                            new MediaConstraints.KeyValuePair("googAutoGainControl", Boolean.toString(googAutoGainControl.isChecked())));
                    mediaConstraints.optional.add(
                            new MediaConstraints.KeyValuePair("googNoiseSupression", Boolean.toString(googNoiseSupression.isChecked())));
                    mediaConstraints.optional.add(
                            new MediaConstraints.KeyValuePair("googHighpassFilter", Boolean.toString(googHighpassFilter.isChecked())));
                    mediaConstraints.optional.add(
                            new MediaConstraints.KeyValuePair("googEchoCancellation2", Boolean.toString(googEchoCancellation2.isChecked())));
                    mediaConstraints.optional.add(
                            new MediaConstraints.KeyValuePair("googAutoGainControl2", Boolean.toString(googAutoGainControl2.isChecked())));
                    mediaConstraints.optional.add(
                            new MediaConstraints.KeyValuePair("googNoiseSuppression2", Boolean.toString(googNoiseSuppression2.isChecked())));

                    try {
                        Map<String, String> inviteParameters = new Gson().fromJson(mInviteParametersView.getText().toString(),
                                new TypeToken<Map<String, String>>() {
                                }.getType());
                        callOptions.setInviteParameters(inviteParameters);
                    } catch (Throwable t) {
                        Log.e(TAG, "Invite Parameters have wrong format of json object");
                    }
                    call = session.createCall(callOptions);
                    call.on(callStatusEvent);
                    /**
                     * Make the outgoing call
                     */
                    call.call();
                    Log.i(TAG, "Permission has been granted by user");
                    break;
                }
            }
            case INCOMING_CALL_REQUEST_CODE: {
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    call.hangup();
                    incomingCallAlert = null;
                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    mCallButton.setText(R.string.action_hangup);
                    mCallButton.setTag(R.string.action_hangup);
                    mCallButton.setEnabled(true);
                    mCallStatus.setText(call.getStatus());
                    call.answer();
                    incomingCallAlert = null;
                    Log.i(TAG, "Permission has been granted by user");
                }
            }

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.disconnect();
        }
    }
}

