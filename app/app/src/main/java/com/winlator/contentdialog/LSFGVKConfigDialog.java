package com.winlator.contentdialog;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.core.AppUtils;
import com.winlator.widget.SeekBar;

public class LSFGVKConfigDialog extends ContentDialog {
    public LSFGVKConfigDialog(XServerDisplayActivity activity) {
        super(activity, R.layout.lsfg_vk_config_dialog);
        setTitle(R.string.lsfg_vk);
        setIcon(R.drawable.icon_screen_effect);

        Spinner sMultiplier = findViewById(R.id.SLSFGMultiplier);
        Spinner sPreset = findViewById(R.id.SLSFGPreset);
        SeekBar sbFlowScale = findViewById(R.id.SBLSFGFlowScale);
        String[] multiplierItems = {
            activity.getString(R.string.lsfg_off),
            activity.getString(R.string.lsfg_multiplier_2x),
            activity.getString(R.string.lsfg_multiplier_3x),
            activity.getString(R.string.lsfg_multiplier_4x)
        };
        sMultiplier.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, multiplierItems));
        String[] presetItems = {
            activity.getString(R.string.lsfg_preset_performance),
            activity.getString(R.string.lsfg_preset_balanced),
            activity.getString(R.string.lsfg_preset_quality),
            activity.getString(R.string.lsfg_preset_custom)
        };
        sPreset.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, presetItems));

        int multiplier = activity.getLSFGMultiplier();
        sMultiplier.setSelection(multiplier == 1 ? 0 : multiplier - 1);
        sbFlowScale.setValue(activity.getLSFGFlowScale());
        sPreset.setSelection(presetPosition(activity.getLSFGPreset()));

        final boolean[] changingFromPreset = {false};
        final boolean[] ready = {false};
        sPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!ready[0] || position == 3) return;
                changingFromPreset[0] = true;
                sbFlowScale.setValue(flowScaleForPreset(position));
                changingFromPreset[0] = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        sbFlowScale.setOnValueChangeListener((seekBar, value) -> {
            if (!changingFromPreset[0] && ready[0] && sPreset.getSelectedItemPosition() != 3)
                sPreset.setSelection(3);
        });
        sPreset.post(() -> ready[0] = true);

        setOnConfirmCallback(() -> {
            int position = sMultiplier.getSelectedItemPosition();
            int selectedMultiplier = position == 0 ? 1 : position + 1;
            String selectedPreset = presetAtPosition(sPreset.getSelectedItemPosition());
            if (!activity.applyLSFGVKConfig(selectedMultiplier, sbFlowScale.getValue(), selectedPreset)) {
                AppUtils.showToast(activity, R.string.unable_to_apply_lsfg_settings);
            }
        });
    }

    private static int presetPosition(String preset) {
        if (Container.LSFG_PRESET_PERFORMANCE.equals(preset)) return 0;
        if (Container.LSFG_PRESET_BALANCED.equals(preset)) return 1;
        if (Container.LSFG_PRESET_QUALITY.equals(preset)) return 2;
        return 3;
    }

    private static String presetAtPosition(int position) {
        if (position == 0) return Container.LSFG_PRESET_PERFORMANCE;
        if (position == 1) return Container.LSFG_PRESET_BALANCED;
        if (position == 2) return Container.LSFG_PRESET_QUALITY;
        return Container.LSFG_PRESET_CUSTOM;
    }

    private static float flowScaleForPreset(int position) {
        if (position == 0) return Container.LSFG_PRESET_PERFORMANCE_FLOW_SCALE;
        if (position == 1) return Container.LSFG_PRESET_BALANCED_FLOW_SCALE;
        return Container.LSFG_PRESET_QUALITY_FLOW_SCALE;
    }
}
