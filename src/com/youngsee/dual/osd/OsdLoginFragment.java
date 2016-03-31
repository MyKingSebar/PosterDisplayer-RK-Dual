/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.dual.osd;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.youngsee.dual.common.SysParamManager;
import com.youngsee.dual.common.YSConfiguration;
import com.youngsee.dual.posterdisplayer.PosterApplication;
import com.youngsee.dual.posterdisplayer.R;
import com.youngsee.dual.posterdisplayer.PosterOsdActivity;

public class OsdLoginFragment extends Fragment
{
    private static final String OSD_DEFAULT_PWD    = "123456";
    
    private Editor              mEditor            = null;
    private SharedPreferences   mSharedPreferences = null;

    private LinearLayout        mOsdExit        = null;
    
    private LinearLayout        mOsdLayout         = null;
    private ImageView           mOsdLoginBtn       = null;
    private EditText            mEnterPwd, mOldPwd, mNewPwd = null;
    private CheckBox            mMemoryPwd         = null;
    private ImageView           mResetPwd          = null;
    private View                mResetView         = null;
    private int                 mOsdMenuId         = 0;
    
    private boolean             mIsInit         = false;
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null)
        {
            mOsdMenuId = args.getInt("osdMenuId");
        }
    }
    
    /**
     * Create the view for this fragment, using the arguments given to it.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // 不能将Fragment的视图附加到此回调的容器元素，因此attachToRoot参数必须为false
        View view = inflater.inflate(R.layout.fragment_osd_login, container, false);
        String code = PosterApplication.getInstance().getConfiguration().getFeatureCode();
        if(code != null && code.equalsIgnoreCase(YSConfiguration.FEATURE_CODE_YUESHI)){
            view.findViewById(R.id.RLLoginRoot).setBackgroundResource(R.drawable.welcome1_ys);
        }
        
        return view;
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        mIsInit = true;
        super.onActivityCreated(savedInstanceState);
        initLoginOsdFragment();
        
    }
    
    @Override
    public void onResume()
    {
        super.onResume();
    }
    
    @Override
    public void onPause()
    {
        super.onPause();
    }
    
    @Override
    public void onDestroy()
    {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (mEnterPwd != null && imm.isActive())
        {
            imm.hideSoftInputFromWindow(mEnterPwd.getApplicationWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
        super.onDestroy();
    }
    
    /*
     * Some of the initialization operation
     */
    private void initLoginOsdFragment()
    {
    	mSharedPreferences = getActivity().getSharedPreferences(PosterOsdActivity.OSD_CONFIG, Context.MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
        
        mOsdExit = (LinearLayout) getActivity().findViewById(R.id.osd_login_exit);
        mOsdLayout = (LinearLayout) getActivity().findViewById(R.id.osd_layout);
        
        mOsdLoginBtn = (ImageView) getActivity().findViewById(R.id.osd_login);
        mEnterPwd = (EditText) getActivity().findViewById(R.id.osd_password);
        mMemoryPwd = (CheckBox) getActivity().findViewById(R.id.memory_password);
        mResetPwd = (ImageView) getActivity().findViewById(R.id.reset_password);
        
        mResetView = LayoutInflater.from(getActivity()).inflate(R.layout.osd_reset_pwd, null);
        mOldPwd = (EditText) mResetView.findViewById(R.id.old_password);
        mNewPwd = (EditText) mResetView.findViewById(R.id.new_password);
        
        ViewTreeObserver vto = getView().getViewTreeObserver();  
		vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener(){ 
            @Override 
            public void onGlobalLayout() {
                if (mIsInit)
                {
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                    {
                        RelativeLayout.LayoutParams osdLayoutParams = new RelativeLayout.LayoutParams((int) (getView().getWidth() * (9.9f / 25)), (int) (getView().getHeight() * (0.56f / 14)));
                        mOsdLayout.setLayoutParams(osdLayoutParams);
                        mOsdLayout.setX(getView().getWidth() * (7.71f / 25));
                        mOsdLayout.setY(getView().getHeight() * (7.82f / 14));
                    }
                    else
                    {
                        RelativeLayout.LayoutParams osdLayoutParams = new RelativeLayout.LayoutParams((int) (getView().getWidth() * (5.6f / 8)), (int) (getView().getHeight() * (0.33f / 14)));
                        mOsdLayout.setLayoutParams(osdLayoutParams);
                        mOsdLayout.setX(getView().getWidth() * (1.47f / 8));
                        mOsdLayout.setY(getView().getHeight() * (6.88f / 14));
                    }
                    
                    mOsdExit.setX(getView().getWidth() - 65);
                    mOsdExit.setY(5);
                    mIsInit = false;
                }
                else
                {
                    // 检查软键盘是否弹出
                    if (getActivity() != null && 
                        getActivity().getWindow() != null && 
                        getActivity().getWindow().peekDecorView() != null)
                    {
                        View decorView = getActivity().getWindow().peekDecorView();
                        Rect rect = new Rect();
                        decorView.getWindowVisibleDisplayFrame(rect);
                        int displayHight = rect.bottom - rect.top;
                        int hight = decorView.getHeight();
                        boolean softInputIsVisible = (double) displayHight / hight < 0.8;
                        if (softInputIsVisible)
                        {
                            PosterOsdActivity.INSTANCE.cancelDismissTime();
                        }
                        else
                        {
                            PosterOsdActivity.INSTANCE.setDismissTime();
                        }
                    }
                }
            }  
        });
        
        mOsdLoginBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v)
            {
                String spwd = SysParamManager.getInstance().getSysPasswd();
                if ((!TextUtils.isEmpty(spwd) && spwd.equals(mEnterPwd.getText().toString()))
                    || OSD_DEFAULT_PWD.equals(mEnterPwd.getText().toString()))
                {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
                    if (imm.isActive())
                    {
                        imm.hideSoftInputFromWindow(v.getApplicationWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                    }
                    
                    if (getActivity() instanceof PosterOsdActivity)
                    {
                        ((PosterOsdActivity) getActivity()).startOsdMenuFragment(mOsdMenuId);
                    }
                }
                else if (mEnterPwd.getText().toString().trim().equals(""))
                {
                    Toast.makeText(getActivity(), R.string.login_dialog_nullmsg, Toast.LENGTH_SHORT).show();
                }
                else
                {
                    Toast.makeText(getActivity(), R.string.login_dialog_msgerror, Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        mMemoryPwd.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
            	String spwd = SysParamManager.getInstance().getSysPasswd();
                if ((!TextUtils.isEmpty(spwd) && spwd.equals(mEnterPwd.getText().toString()))
                    || OSD_DEFAULT_PWD.equals(mEnterPwd.getText().toString()))
                {
                    mEditor.putBoolean(PosterOsdActivity.OSD_ISMEMORY, isChecked);
                    mEditor.commit();
                }
                else
                {
                    Toast.makeText(getActivity(), R.string.login_dialog_writetruemsg, Toast.LENGTH_LONG).show();
                    mMemoryPwd.setChecked(false);
                }
            }
        });
        
        mResetPwd.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v)
            {
                resetPassword();
            }
        });
        
        mOsdExit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v)
            {
                getActivity().finish();
            }
        });
    }
    
    /*
     * Reset the password
     */
    private void resetPassword()
    {
        ViewGroup vg = (ViewGroup) mResetView.getParent();
        if (vg != null)
        {
            vg.removeAllViewsInLayout();
        }
        new AlertDialog.Builder(getActivity()).setTitle(R.string.login_modifyrmsg).setView(mResetView)
                .setPositiveButton(R.string.enter, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        String old_Str = SysParamManager.getInstance().getSysPasswd();
                        if (mOldPwd.getText().toString().equals(old_Str) && 
                           !mOldPwd.getText().toString().equals(mNewPwd.getText().toString()))
                        {
                            SysParamManager.getInstance().setSysPasswd(mNewPwd.getText().toString());
                        }
                        else if(mOldPwd.getText().toString().equals(mNewPwd.getText().toString()))
                        {
                            Toast.makeText(getActivity(), R.string.login_dialog_newpwddifferent, Toast.LENGTH_SHORT).show();
                        }
                        else
                        {
                            Toast.makeText(getActivity(), R.string.login_dialog_retrywrite, Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        
                    }
                }).create().show();
    }
}
