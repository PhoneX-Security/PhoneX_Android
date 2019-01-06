package net.phonex.camera.control;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;

import net.phonex.R;
import net.phonex.camera.interfaces.CameraParamsChangedListener;
import net.phonex.camera.model.FocusMode;
import net.phonex.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Matus on 21-Jul-15.
 */
public class CameraSettingsDialogFragment extends DialogFragment {

    public static final String TAG = CameraSettingsDialogFragment.class.getSimpleName();

    private CameraParamsChangedListener paramsChangedListener;

    private FocusMode focusMode;
    private Camera.Size resolution;
    private ArrayList<Camera.Size> cameraSizes;
    private boolean hasAutoFocus;
    private List<FocusMode> focusModes = Arrays.asList(FocusMode.values());
    private boolean useFrontCamera;

    public static CameraSettingsDialogFragment newInstance(Bundle bundle, CameraParamsChangedListener listener) {
        CameraSettingsDialogFragment fragment = new CameraSettingsDialogFragment();
        fragment.setArguments(bundle);
        fragment.paramsChangedListener = listener;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        expandParams(getArguments());
    }

    private void expandParams(Bundle params) {
        if (params == null) {
            params = new Bundle();
        }
        int id = 0;
        if (params.containsKey(CameraFragment.RESOLUTION_LIST)) {
            cameraSizes = (ArrayList<Camera.Size>) params.get(CameraFragment.RESOLUTION_LIST);

            if (params.containsKey(CameraFragment.RESOLUTION)) {
                id = params.getInt(CameraFragment.RESOLUTION, 0);
            }
            if (id < cameraSizes.size()) {
                resolution = cameraSizes.get(id);
            } else if (cameraSizes.isEmpty()) {
                Log.d(TAG, "No available picture sizes");
            } else {
                resolution = cameraSizes.get(0);
                Log.df(TAG, "Picture size out of range %d of %d", id, cameraSizes.size());
            }
            id = 0;
        } else {
            throw new IllegalArgumentException("RESOLUTION_LIST");
        }
        if (params.containsKey(CameraFragment.FOCUS_MODE)) {
            id = params.getInt(CameraFragment.FOCUS_MODE);
        }
        focusMode = FocusMode.getFocusModeById(id);
        if (params.containsKey(CameraFragment.AUTO_FOCUS)) {
            hasAutoFocus = params.getBoolean(CameraFragment.AUTO_FOCUS);
        }
        if (params.containsKey(CameraFragment.FRONT_CAMERA)) {
            useFrontCamera = params.getBoolean(CameraFragment.FRONT_CAMERA);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.camera_dialog_params, container, false);

        Spinner qualitySwitcher = (Spinner) view.findViewById(R.id.qualities);
        qualitySwitcher.setAdapter(new ObjectToStringAdapter<>(getActivity(), cameraSizes));
        qualitySwitcher.setSelection(cameraSizes.indexOf(resolution));
        qualitySwitcher.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (resolution == cameraSizes.get(position)) {
                    return;
                }
                resolution = cameraSizes.get(position);
                onResolutionChanged(cameraSizes.indexOf(resolution));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (hasAutoFocus) {
            Spinner focusSwitcher = (Spinner) view.findViewById(R.id.focus_modes);
            focusSwitcher.setAdapter(new ObjectToStringAdapter<>(getActivity(), focusModes));
            focusSwitcher.setSelection(focusModes.indexOf(focusMode));
            focusSwitcher.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (focusMode == focusModes.get(position)) {
                        return;
                    }
                    focusMode = focusModes.get(position);
                    onFocusModeChanged(focusMode.getId());
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        } else {
            RelativeLayout focusLayout = (RelativeLayout) view.findViewById(R.id.focus_layout);
            focusLayout.setVisibility(View.GONE);
        }

        if (Camera.getNumberOfCameras() <= 1) {
            view.findViewById(R.id.camera_instance).setVisibility(View.GONE);
        } else {
            view.findViewById(R.id.camera_instance).setVisibility(View.VISIBLE);
            Switch cameraSwitch = (Switch) view.findViewById(R.id.switch_camera);
            cameraSwitch.setChecked(useFrontCamera);
            cameraSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    onCameraInstanceChanged(isChecked);
                    dismiss();
                }
            });
        }

        view.findViewById(R.id.close).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dismiss();
            }

        });

        return view;
    }

    public void onResolutionChanged(int id) {
        if (paramsChangedListener != null) {
            paramsChangedListener.onResolutionChanged(id, useFrontCamera);
        }
    }

    public void onCameraInstanceChanged(boolean useFrontCamera) {
        this.useFrontCamera = useFrontCamera;
        if (paramsChangedListener != null) {
            paramsChangedListener.onCameraInstanceChanged(useFrontCamera);
        }
    }

    public void onFocusModeChanged(int id) {
        if (paramsChangedListener != null) {
            paramsChangedListener.onFocusModeChanged(id, useFrontCamera);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        return dialog;
    }

    public void show(FragmentManager manager) {
        super.show(manager, TAG);
    }
}
