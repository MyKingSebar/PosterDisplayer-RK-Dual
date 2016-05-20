/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.dual.osd;

import android.app.Activity;
import android.app.Fragment;
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
	static final String OSD_DEFAULT_UN    = "ehualu";
    static final String OSD_DEFAULT_PWD    = "ehualu$888";
    private LinearLayout        mOsdExit        = null;
    
    private LinearLayout        mOsdLayout         = null;
    private ImageView           mOsdLoginBtn       = null;
    private EditText            mEnterUn           = null;
    private EditText            mEnterPwd          = null;
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
        mOsdLoginBtn = (ImageView) getActivity().findViewById(R.id.osd_login);
        mEnterPwd = (EditText) getActivity().findViewById(R.id.osd_password);
        mEnterUn = (EditText) getActivity().findViewById(R.id.osd_username);
        mOsdExit = (LinearLayout) getActivity().findViewById(R.id.osd_login_exit);
        
        mOsdLayout = (LinearLayout) getActivity().findViewById(R.id.osd_layout);
        ViewTreeObserver vto = getView().getViewTreeObserver();  
		vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener(){ 
            @Override 
		    public void onGlobalLayout() {
            	if (mIsInit)
            	{	
					if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
						RelativeLayout.LayoutParams osdLayoutParams = new RelativeLayout.LayoutParams(
								(int) (getView().getWidth() * (15.6f / 47.8f)),
								(int) (getView().getHeight() * (8f / 27f)));
						mOsdLayout.setLayoutParams(osdLayoutParams);
						mOsdLayout.setX(getView().getWidth() * (16.1f / 47.8f));
						mOsdLayout.setY(getView().getHeight() * (14.2f / 27f));
					} else {
						RelativeLayout.LayoutParams osdLayoutParams = new RelativeLayout.LayoutParams(
								(int) (getView().getWidth() * (17.3f / 27f)),
								(int) (getView().getHeight() * (9.3f / 47.8f)));
						mOsdLayout.setLayoutParams(osdLayoutParams);
						mOsdLayout.setX(getView().getWidth() * (4.85f / 27f));
						mOsdLayout.setY(getView().getHeight() * (20.2f / 47.8f));
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
                        if (softInputIsVisible) // 如果软件键盘弹出，则等待输入，不能关闭界面
                        {
                            PosterOsdActivity.INSTANCE.cancelDismissTime();
                        }
                        else   
                        {
                            PosterOsdActivity.INSTANCE.setDismissTime();
                        }
                      //获取rootInvisibleHeight在窗体的不可视区域高度(被其他View遮挡的区域高度)  
                        int rootInvisibleHeight = decorView.getRootView().getHeight() - rect.bottom;  
                        //若不可视区域高度大于150，则键盘显示  
                        if (rootInvisibleHeight > 150) {  
                            int[] location = new int[2];  
                            //获取当前窗体内的绝对坐标,location[0]-->x坐标,location[1]-->y坐标
                            mOsdLoginBtn.getLocationInWindow(location);  
                            //计算srollHeight滚动高度，使scrollToView在可见区域  
                            int srollHeight = (location[1] + mOsdLoginBtn.getHeight()) - rect.bottom; 
                            if(srollHeight>50){ 
                            	 decorView.scrollTo(0, srollHeight);  
                            }
                        } else {  
                            // 无需滚动 
                        	decorView.scrollTo(0, 0);  
                        } 
                    }
            	}       	
		    }  
		});

        mOsdLoginBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v)
            {
            	boolean virefyIsOk = false;
                if (mEnterUn.getText().toString().trim().equals("") ||
                	mEnterPwd.getText().toString().trim().equals(""))
                {
                    Toast.makeText(getActivity(), R.string.login_dialog_unnullmsg, Toast.LENGTH_SHORT).show();
                }
                else if (OSD_DEFAULT_UN.equals(mEnterUn.getText().toString()))
                {
                	String strPwd = SysParamManager.getInstance().getSysPasswd();
                	if (TextUtils.isEmpty(strPwd))
                	{
                		SysParamManager.getInstance().setSysPasswd(OSD_DEFAULT_PWD);
                		virefyIsOk = OSD_DEFAULT_PWD.equals(mEnterPwd.getText().toString());
                	}
                	else
                	{
                		virefyIsOk = strPwd.equals(mEnterPwd.getText().toString());
                	}
                }
                
				if (virefyIsOk) 
				{
                	//登陆成功 view回到原位置
    				getActivity().getWindow().peekDecorView().scrollTo(0, 0);
					InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
					if (imm.isActive()) {
						imm.hideSoftInputFromWindow(
								v.getApplicationWindowToken(),
								InputMethodManager.HIDE_NOT_ALWAYS);
					}

					if (getActivity() instanceof PosterOsdActivity) {
						((PosterOsdActivity) getActivity()).startOsdMenuFragment(mOsdMenuId);
					}
				} 
				else 
				{
					Toast.makeText(getActivity(),
							R.string.login_dialog_unmsgerror,
							Toast.LENGTH_SHORT).show();
				}
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
}
