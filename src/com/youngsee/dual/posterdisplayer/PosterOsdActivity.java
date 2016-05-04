/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.dual.posterdisplayer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.youngsee.dual.common.Contants;
import com.youngsee.dual.posterdisplayer.R;
import com.youngsee.dual.osd.OsdLoginFragment;
import com.youngsee.dual.osd.OsdMainMenuFragment;
import com.youngsee.dual.osd.OsdSubMenuFragment;
import com.youngsee.dual.posterdisplayer.PosterApplication;
import com.youngsee.dual.power.PowerOnOffManager;
import com.youngsee.dual.screenmanager.ScreenManager;

public class PosterOsdActivity extends Activity
{
	public static PosterOsdActivity INSTANCE = null;
    public static final String  OSD_CONFIG            = "osd_config";
    public static final String  OSD_PASSWORD          = "password";
    public static final String  OSD_ISMEMORY          = "isMemory";
    
    // Note: if the view should show on SubMenu,
    // the menu Id should increase from here.
    public static final int     OSD_SERVER_ID         = 0;
    public static final int     OSD_CLOCK_ID          = 1;
    public static final int     OSD_TOOL_ID           = 2;
    public static final int     OSD_ABOUT_ID          = 3;

    // these Menu ID didn't show on SubMenu
    public static final int     OSD_SYSTEM_ID         = 4;
    public static final int     OSD_FILEMANAGER_ID    = 5;
    public static final int     OSD_MAIN_ID           = 6;
    
    private static final String LOGIN_FRAGMENT_TAG    = "OsdLoginTag";
    private static final String MAINMENU_FRAGMENT_TAG = "OsdMainMenuTag";
    private static final String SUBMENU_FRAGMENT_TAG  = "OsdSubMenuTag";
    
    // The msg id to dismiss the OSD screen
    private static final int     EVENT_OSD_DISMISS     = 0;
    private static final int     EVENT_START_FRAGMENT  = 1;
    
    private WakeLock            mWklk                 = null;
    private int                 mMenuId               = OSD_MAIN_ID;
    
    private final long DEFAULT_OSD_TIMEOUT = 3*60*1000;
    
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        PosterApplication.setSystemBarVisible(this, false);
        setContentView(R.layout.activity_osd);
        //getWindow().setFormat(PixelFormat.TRANSLUCENT);
        
        INSTANCE = this;
        if (ScreenManager.getInstance() != null)
        {
            ScreenManager.getInstance().mOsdIsOpen = true;
        }
        
        mMenuId = getIntent().getIntExtra("menuId", OSD_MAIN_ID);
        
        // 创建WakeLock
        if (mWklk == null)
        {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWklk = pm.newWakeLock((PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "PosterOsd");
        }
        
        // 唤醒屏幕
        if (mWklk != null)
        {
            mWklk.acquire();
        }
        
        startOsdFragment();
        setDismissTime();
    }
    
    @Override
    protected void onResume()
    {
        super.onResume();
        if (PowerOnOffManager.getInstance().getCurrentStatus() == PowerOnOffManager.STATUS_STANDBY)
        {
            PowerOnOffManager.getInstance().setCurrentStatus(PowerOnOffManager.STATUS_ONLINE);
            PowerOnOffManager.getInstance().checkAndSetOnOffTime(PowerOnOffManager.AUTOSCREENOFF_URGENT);
        }
    }
    
    @Override
    protected void onPause()
    {
        super.onPause();
    }
    
    @SuppressLint("Wakelock")
    @Override
    protected void onDestroy()
    {
        mHandler.removeMessages(EVENT_OSD_DISMISS);
        mHandler.removeMessages(EVENT_START_FRAGMENT);
        
        // 恢复屏幕
        if (mWklk != null)
        {
            mWklk.release();
            mWklk = null;
        }
        
        // 恢复标志
        if (ScreenManager.getInstance() != null)
        {
            ScreenManager.getInstance().mOsdIsOpen = false;
        }
        
        if (PowerOnOffManager.getInstance() != null) {
        	PowerOnOffManager.getInstance().dismissPromptDialog();
        }
        
        INSTANCE = null;
        
        super.onDestroy();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        switch (keyCode)
        {
        case KeyEvent.KEYCODE_MENU:
            this.finish();
            return true;  // 打开OSD主菜单
        }
        
        return super.onKeyDown(keyCode, event);
    }
    
    /*
    public void handleActionsAfterLogin(int action)
    {
        switch (action)
        {
        case OsdSubMenuFragment.UPDATE_PROGRAM_ACTION_ID:
            new AlertDialog.Builder(this).setTitle(R.string.tools_dialog_u_disk_update_header)
                    .setPositiveButton(R.string.enter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            UDiskUpdata diskUpdate = new UDiskUpdata(PosterOsdActivity.this);
                            diskUpdate.updateProgram();
                        }
                    }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                        }
                    }).show();
            break;
        case OsdSubMenuFragment.CLEAN_PROGRAM_ACTION_ID:
            new AlertDialog.Builder(this).setTitle(R.string.tools_dialog_clearallporgram)
                    .setPositiveButton(R.string.enter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            CleanDisk cleanPgm = new CleanDisk(PosterOsdActivity.this);
                            cleanPgm.cleanProgram();
                        }
                    }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                        }
                    }).show();
        default:
            break;
        }
        
        // 准备参数
        Bundle bundle = new Bundle();
        bundle.putInt("osdMenuId", OSD_TOOL_ID);
        
        // 传递参数
        OsdSubMenuFragment subMenu = new OsdSubMenuFragment();
        subMenu.setArguments(bundle);
        
        // 启动Login Fragment
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE); // 切换时有渐变效果
        ft.replace(R.id.osdroot, subMenu, SUBMENU_FRAGMENT_TAG).commitAllowingStateLoss();
    }
    */
    
    public void startOsdMenuFragment(int nMenuId)
    {
        switch (nMenuId)
        {
        case OSD_MAIN_ID:
        {
            // 启动Main menu Fragment
            OsdMainMenuFragment mainMenu = new OsdMainMenuFragment();
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE); // 切换时有渐变效果
            ft.replace(R.id.osdroot, mainMenu, MAINMENU_FRAGMENT_TAG).commitAllowingStateLoss();
        }
            break;
        
        case OSD_SERVER_ID:
        case OSD_CLOCK_ID:
        case OSD_TOOL_ID:
        case OSD_ABOUT_ID:
        {
            // 准备参数
            Bundle bundle = new Bundle();
            bundle.putInt("osdMenuId", nMenuId);
            
            // 传递参数
            OsdSubMenuFragment subMenu = new OsdSubMenuFragment();
            subMenu.setArguments(bundle);
            
            // 启动sub menu Fragment
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE); // 切换时有渐变效果
            ft.replace(R.id.osdroot, subMenu, SUBMENU_FRAGMENT_TAG).commitAllowingStateLoss();
        }
            break;
        
        case OSD_SYSTEM_ID:
            PosterApplication.startApplication(this, Contants.SETTING_PACKAGENAME);
            PosterOsdActivity.this.finish();
            break;
            
        case OSD_FILEMANAGER_ID:
            PosterApplication.startApplication(this, Contants.FILEBROWSER_PACKAGENAME);
            PosterOsdActivity.this.finish();
            break;
        }
    }
    
    /*
     * 在进入menu前启动用户名密码验证对话框
     */
    public void startLoginFragmentWithMenu(int mMenuId)
    {
        // 准备参数
        Bundle bundle = new Bundle();
        bundle.putInt("osdMenuId", mMenuId);
        
        // 传递参数
        OsdLoginFragment login = new OsdLoginFragment();
        login.setArguments(bundle);
        
        // 启动Login Fragment
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_NONE); // 切换时无效果
        ft.replace(R.id.osdroot, login, LOGIN_FRAGMENT_TAG).commitAllowingStateLoss();
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            setDismissTime();
        } else {
        	cancelDismissTime(); 
        }
        
        super.onWindowFocusChanged(hasFocus);
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent event)
    {
        setDismissTime();
        return super.dispatchTouchEvent(event);
    }
    
    public void setDismissTime()
    {
        mHandler.removeMessages(EVENT_OSD_DISMISS);
        Message message = mHandler.obtainMessage();
        message.what = EVENT_OSD_DISMISS;
        mHandler.sendMessageDelayed(message, DEFAULT_OSD_TIMEOUT);
    }
    
    public void cancelDismissTime()
    {
        mHandler.removeMessages(EVENT_OSD_DISMISS);
    }
    
    private void startOsdFragment()
    {
        Message message = mHandler.obtainMessage();
        message.what = EVENT_START_FRAGMENT;
        mHandler.sendMessage(message);
    }
    
    @SuppressLint("HandlerLeak")
    final Handler mHandler = new Handler() {
         @Override
         public void handleMessage(Message msg)
         {
             switch (msg.what)
             {
             case EVENT_OSD_DISMISS:
                  PosterOsdActivity.this.finish();
                  return;
                  
             case EVENT_START_FRAGMENT:
                 SharedPreferences sharedPreferences = getSharedPreferences(OSD_CONFIG, MODE_PRIVATE);
                 if (!sharedPreferences.getBoolean(OSD_ISMEMORY, false))
                 {
                     startLoginFragmentWithMenu(mMenuId);
                 }
                 else
                 {
                     startOsdMenuFragment(mMenuId);
                 }
                 return;
             }
             
             super.handleMessage(msg);
         }
     };
}
