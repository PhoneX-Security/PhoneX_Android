package net.phonex.ui.sendFile;

import net.phonex.R;

import java.util.Arrays;
import java.util.List;

/**
 * Created by miroc on 16.1.15.
 */
public enum SortingType {
    ALPHABET(R.string.file_picker_sorting_alphabet),
    DATE(R.string.file_picker_sorting_date);

    private final int text;

    private SortingType(int text) {
        this.text = text;
    }

    public static List<SortingType> getList(){
        return Arrays.asList(values());
    }

    public int getText() {
        return text;
    }

}
