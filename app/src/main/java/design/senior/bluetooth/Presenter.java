package design.senior.bluetooth;

import android.content.Context;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableField;
import android.renderscript.ScriptGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public class Presenter {




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

    public void test(){
//        View v = new View(this);
//        ((EditText)(v)).addTextChangedListener(watcher);

    }

}
