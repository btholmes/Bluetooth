package design.senior.bluetooth;

import android.content.Context;
import android.databinding.ObservableField;
import android.renderscript.ScriptGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public class Presenter {


    public ObservableField<String> text = new ObservableField<>();

    public TextWatcher watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            String newFreq = s.toString();
            text.set(s.toString());
//            model.setFreqOfTone(newFreq);
        }

        @Override
        public void afterTextChanged(Editable s) {
            String newFreq = s.toString();
            text.set(s.toString());
        }
    };

    public Presenter(){

    }


    /**
     * Method to open the keyboard
     * @param view
     */
    public void openKeyboard(View view){
        ((InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

    }

    /**
     * Method to close the keyboard
     * @param view
     */
    public void closeKeyboard(View view){
        ((InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(), 0);

    }
}
