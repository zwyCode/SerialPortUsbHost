package com.lingware.newserialport;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.EventLog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Created by wuyiz on 2017/8/3.
 */

public class FragmentOne extends Fragment {

    public interface setOnClickPageOneListener {
        void onTouchPageOne(View view, MotionEvent event);
    }

    private setOnClickPageOneListener mOnClickListener;
    private TextView mUpTextView;
    private TextView mDownTextView;
    private TextView mLeftTextView;
    private TextView mRightTextView;
    private TextView mOkTextView;
    private myTouchListener mTouchListener;
    private TextView mPressedValueText;
    private EditText threadEdit;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.page_one, container, false);

        Log.e("ZWY", "---->onCreateView");

        mUpTextView = (TextView) view.findViewById(R.id.button_up);
        mDownTextView = (TextView) view.findViewById(R.id.button_down);
        mLeftTextView = (TextView) view.findViewById(R.id.button_left);
        mRightTextView = (TextView) view.findViewById(R.id.button_right);
        mOkTextView = (TextView) view.findViewById(R.id.button_ok);
        threadEdit = (EditText) view.findViewById(R.id.thread_edit);
        mPressedValueText = (TextView) view.findViewById(R.id.presss_value);

        mTouchListener = new myTouchListener();

        mUpTextView.setOnTouchListener(mTouchListener);
        mDownTextView.setOnTouchListener(mTouchListener);
        mLeftTextView.setOnTouchListener(mTouchListener);
        mRightTextView.setOnTouchListener(mTouchListener);
        mOkTextView.setOnTouchListener(mTouchListener);
        return view;
    }

    public void setOnPageOneClickListener(setOnClickPageOneListener onClickListener) {
        mOnClickListener = onClickListener;
    }

    private class myTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if(mOnClickListener != null)
                mOnClickListener.onTouchPageOne(v, event);
            return false;
        }
    }

    public int getThreadValue() {
        int value = 0;
        if(threadEdit != null) {
            value = Integer.valueOf(threadEdit.getText().toString());
        }

        return value;
    }

    public void setPressedValue(int value) {
        if(mPressedValueText != null) {
            mPressedValueText.setText(String.valueOf(value));
        }
    }
}
