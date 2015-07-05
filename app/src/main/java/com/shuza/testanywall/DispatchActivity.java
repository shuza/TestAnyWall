package com.shuza.testanywall;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;

import com.parse.Parse;
import com.parse.ParseUser;

/**
 * Created by Boka on 27-Jun-15.
 */
public class DispatchActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        if(ParseUser.getCurrentUser() != null){
            startActivity(new Intent(this, MainActivity.class));
        }else{
            startActivity(new Intent(this, WelcomeActivity.class));
        }
    }
}
