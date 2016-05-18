/*
 * Copyright (C) 2013 poster PCE YoungSee Inc. 
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.dual.posterdisplayer;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.ExtendDisplayManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.youngsee.dual.authorization.AuthorizationManager;
import com.youngsee.dual.common.Actions;
import com.youngsee.dual.common.Contants;
import com.youngsee.dual.common.FileUtils;
import com.youngsee.dual.common.MediaInfoRef;
import com.youngsee.dual.common.PackageInstaller;
import com.youngsee.dual.common.SubWindowInfoRef;
import com.youngsee.dual.common.SysParamManager;
import com.youngsee.dual.common.YSConfiguration;
import com.youngsee.dual.customview.AudioView;
import com.youngsee.dual.customview.DateTimeView;
import com.youngsee.dual.customview.GalleryView;
import com.youngsee.dual.customview.MarqueeView;
import com.youngsee.dual.customview.MultiMediaView;
import com.youngsee.dual.customview.PosterBaseView;
import com.youngsee.dual.customview.TimerView;
import com.youngsee.dual.customview.YSWebView;
import com.youngsee.dual.logmanager.LogManager;
import com.youngsee.dual.logmanager.LogUtils;
import com.youngsee.dual.logmanager.Logger;
import com.youngsee.dual.osd.UDiskUpdata;
import com.youngsee.dual.posterdisplayer.R;
import com.youngsee.dual.power.PowerOnOffManager;
import com.youngsee.dual.screenmanager.ScreenManager;
import com.youngsee.dual.update.APKUpdateManager;
import com.youngsee.dual.webservices.WsClient;

@SuppressLint("Wakelock")
public class PosterMainActivity extends Activity{
	public static PosterMainActivity INSTANCE = null;
	
	private WakeLock mWklk = null;
	private FrameLayout mMainLayout = null;
	
	private PopupWindow mOsdPupupWindow = null; // OSD 弹出菜单

	private Intent popService = null;
	private boolean isPopServiceRunning = false; // 插播字幕

	private Dialog mUpdateProgramDialog = null;
	private InternalReceiver mInternalReceiver = null;
	
	private MediaInfoRef mBgImgInfo = null;
	
	private MultiMediaView mMainWindow = null;
    private Set<PosterBaseView> mSubWndCollection   = null;  // 屏幕布局信息

	private static final int EVENT_CHECK_SET_ONOFFTIME = 0;

	private static int    MAX_CLICK_CNTS = 5;
	private long          mLastClickTime = 0;
	private static int mCurrentClickCnts = 0;
	
	@SuppressWarnings("deprecation")
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PosterApplication.setSystemBarVisible(this, false);
        setContentView(R.layout.activity_main);
		//getWindow().setFormat(PixelFormat.TRANSLUCENT);

		Logger.d("====>PosterMainActivity onCreate: " + getIntent().toString());
		
		INSTANCE = this;

		// 初始安装APK时，需安装YSSysCtroller.apk
		if (PosterApplication.getInstance().getConfiguration().isInstallYsctrl()) 
		{
			int versionCode = PosterApplication.getInstance().getVerCode();
			SharedPreferences sharedPreferences = getSharedPreferences("ys_poster_displayer", Activity.MODE_PRIVATE);
			int installed = sharedPreferences.getInt("monitorInstalled", 0);
			int installedVersion = sharedPreferences.getInt("versionCode", 0);
			if (installed == 0 || versionCode != installedVersion) 
			{
				// install system ctrl APK
				PackageInstaller install = new PackageInstaller();
				String controller = install.retrieveSourceFromAssets("YSSysController.apk");
				if (!TextUtils.isEmpty(controller) && install.installSystemPkg(controller, "YSSysController.apk")) 
				{
				    SharedPreferences.Editor editor = sharedPreferences.edit();
					editor.putInt("monitorInstalled", 1);
					editor.putInt("versionCode", versionCode);
					editor.commit();
					
					// start the APK
					startService(new Intent(Actions.SYSCTRL_SERVICE_ACTION));
				}
			}
		}
        
		// 初始化背景颜色
		mMainLayout = (FrameLayout) findViewById(R.id.pgmroot);
		mMainLayout.setBackgroundColor(Color.BLACK);

        if (mWklk == null)
        {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWklk = pm.newWakeLock((PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "PosterMain");
        }

		// 唤醒屏幕
        if (mWklk != null)
        {
            mWklk.acquire();
        }

		// 初始化系统参数
		PosterApplication.getInstance().initAppParam();

		// 检测是否鉴权
        if (!AuthorizationManager.getInstance().checkAuthStatus(AuthorizationManager.MODE_IMMEDIATE))
        {
            AuthorizationManager.getInstance().startAuth();
        }
		
        // 启动屏幕管理线程
        if (ScreenManager.getInstance() == null) 
        {
     	    ScreenManager.createInstance(this).startRun();
        }
		
		// 启动网络管理线程
		if (WsClient.getInstance() == null) 
		{
			WsClient.createInstance(this).startRun();
		}

		// 启动日志输出线程
		if (LogUtils.getInstance() == null) 
		{
			LogUtils.createInstance(this).startRun();
		}

		// 初始化OSD菜单
        initOSD();

		// 定义OSD菜单弹出方式
		mMainLayout.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				long clickTime = SystemClock.uptimeMillis();
				long dtime = clickTime - mLastClickTime;
				if (mLastClickTime == 0 || dtime < 3000) {
					mCurrentClickCnts++;
					mLastClickTime = clickTime;
				} else {
					mLastClickTime = 0;
					mCurrentClickCnts = 0;
				}

				// When click times is more than 5, then popup the tools bar
				if (mCurrentClickCnts > MAX_CLICK_CNTS) {
					showOsd();
					mLastClickTime = 0;
					mCurrentClickCnts = 0;
				}
			}
		});

		// 启动定时器，定时清理文件和上传日志
		PosterApplication.getInstance().startTimingDel();
		PosterApplication.getInstance().startTimingUploadLog();

		// 检测定时开关机状态
		PowerOnOffManager.getInstance().checkAndSetOnOffTime(
				PowerOnOffManager.AUTOSCREENOFF_COMMON);

		// 检测是否需要升级新版本
		if (SysParamManager.getInstance().getAutoUpgrade() == 1) 
		{
			APKUpdateManager.getInstance().startAutoDetector();
		}
		        
		if (PosterApplication.getInstance().getConfiguration().isMonitorElectric()) 
		{
		    PosterApplication.getInstance().startTimerRunPowerMeter();
		}
	}

	private void initReceiver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_REMOVED);
		filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
		filter.addDataScheme("file");
		mInternalReceiver = new InternalReceiver();
		registerReceiver(mInternalReceiver, filter);
	}

	public void showOsd() {
		if (mOsdPupupWindow != null) {
			if (mOsdPupupWindow.isShowing()) {
				mOsdPupupWindow.dismiss();
			} else {
				mOsdPupupWindow.showAtLocation(mMainLayout, Gravity.TOP | Gravity.LEFT, 0, 0);
				mHandler.postDelayed(rHideOsdPopWndDelay, 30000);
			}
		}
	}
	
	@Override
    public void onStart(){
		super.onStart();
		initReceiver();
	}

	@Override
	protected void onResume() 
	{
		if (mMainWindow != null)
		{
			mMainWindow.onViewResume();
		}
		
		if (mSubWndCollection != null)
        {
            for (PosterBaseView wnd : mSubWndCollection)
            {
                wnd.onViewResume();
            }
        }
		
		if (TextUtils.isEmpty(ScreenManager.getInstance().getPlayingPgmId()))
        {
            LogUtils.getInstance().toAddPLog(0, Contants.PlayProgramStart, ScreenManager.getInstance().getPlayingPgmId(), "", "");
        }
		
	    hideNavigationBar();
	    
        if (PowerOnOffManager.getInstance().getCurrentStatus() == PowerOnOffManager.STATUS_STANDBY)
        {
            PowerOnOffManager.getInstance().setCurrentStatus(PowerOnOffManager.STATUS_ONLINE);
            PowerOnOffManager.getInstance().checkAndSetOnOffTime(PowerOnOffManager.AUTOSCREENOFF_URGENT);
        }
        
        super.onResume();
	}

	@Override
	protected void onPause() 
	{
		mHandler.removeCallbacks(rSetWndBgDelay);
		mHandler.removeCallbacks(rHideOsdPopWndDelay);
		mHandler.removeCallbacks(rGoToExtendScreenDelay);
		mHandler.removeCallbacks(rStartMainScreenApk);
		
		if (mMainWindow != null)
		{
			mMainWindow.onViewPause();
		}
		
		if (mSubWndCollection != null)
        {
            for (PosterBaseView wnd : mSubWndCollection)
            {
                wnd.onViewPause();
            }
        }

        if (!TextUtils.isEmpty(ScreenManager.getInstance().getPlayingPgmId()))
        {
            LogUtils.getInstance().toAddPLog(0, Contants.PlayProgramEnd, ScreenManager.getInstance().getPlayingPgmId(), "", "");
        }
        
		if (mOsdPupupWindow.isShowing()) {
			mOsdPupupWindow.dismiss();
		}

		super.onPause();
	}
	
	@Override
    public void onStop(){
    	unregisterReceiver(mInternalReceiver);
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		mHandler.removeCallbacks(rSetWndBgDelay);
		mHandler.removeCallbacks(rHideOsdPopWndDelay);
		mHandler.removeCallbacks(rGoToExtendScreenDelay);
		mHandler.removeCallbacks(rStartMainScreenApk);
		mHandler.removeMessages(EVENT_CHECK_SET_ONOFFTIME);

		cleanupLayout();
		
		if (mOsdPupupWindow.isShowing()) {
			mOsdPupupWindow.dismiss();
		}

		synchronized (this) {
			if (isPopServiceRunning) {
				stopService(popService);
				isPopServiceRunning = false;
			}
		}

		// 结束屏幕管理线程
		if (ScreenManager.getInstance() != null) {
			ScreenManager.getInstance().stopRun();
		}

		// 结束网络管理线程
		if (WsClient.getInstance() != null) {
			WsClient.getInstance().stopRun();
		}

		// 结束日志输出线程
		if (LogUtils.getInstance() != null) {
			LogUtils.getInstance().stopRun();
		}

		// 结束APK更新
		if (APKUpdateManager.getInstance() != null) {
			APKUpdateManager.getInstance().destroy();
		}

		if (PowerOnOffManager.getInstance() != null) {
			PowerOnOffManager.getInstance().destroy();
		}
		
		if (AuthorizationManager.getInstance() != null) {
			AuthorizationManager.getInstance().destroy();
		}
		
		if (LogManager.getInstance() != null) {
			LogManager.getInstance().destroy();
		}

		// 终止定时器
		PosterApplication.getInstance().cancelTimingDel();
		PosterApplication.getInstance().cancelTimingUploadLog();

		dismissUpdateProgramDialog();

		if (PosterApplication.getInstance().getConfiguration().isMonitorElectric())
		{
		   PosterApplication.getInstance().cancelTimerRunPowerMeter();
		}
		
		// 恢复屏幕
		if (mWklk != null) {
			mWklk.release();
			mWklk = null;
		}

		INSTANCE = null;
		super.onDestroy();
		System.exit(0);
	}

	@Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if( hasFocus ) {
            hideNavigationBar();
        }
    }
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			// Log.i("dddd", "back--");
			return true; // 不响应Back键

		case KeyEvent.KEYCODE_MENU:
			enterToOSD(PosterOsdActivity.OSD_MAIN_ID);
			return true; // 打开OSD主菜单

		case KeyEvent.KEYCODE_PAGE_UP:
			return true; // 主窗口中上一个素材

		case KeyEvent.KEYCODE_PAGE_DOWN:
			return true; // 主窗口中下一个素材

		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			return true; // 主窗口视频播放

		case KeyEvent.KEYCODE_MEDIA_STOP:
			return true; // 主窗口视频暂停
			
		case KeyEvent.KEYCODE_VOLUME_UP:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		    hideNavigationBar();
		    break;
		}

		return super.onKeyDown(keyCode, event);
	}

	private void showUpdateProgramDialog() {
		if ((mUpdateProgramDialog != null) && mUpdateProgramDialog.isShowing()) {
			mUpdateProgramDialog.dismiss();
		}
		mUpdateProgramDialog = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_info).setTitle(R.string.udisk_update_pgm)
				.setMessage(R.string.udisk_content).setCancelable(true)
				.setPositiveButton(R.string.udisk_title, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						UDiskUpdata diskUpdate = new UDiskUpdata(PosterMainActivity.this);
                        diskUpdate.updateProgram();
						mUpdateProgramDialog = null;
					}
				})
				.setNegativeButton(R.string.udisk_btn_cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						mUpdateProgramDialog = null;
					}
				}).create();
		mUpdateProgramDialog.show();
	}

	public void dismissUpdateProgramDialog() {
		if ((mUpdateProgramDialog != null)
				&& mUpdateProgramDialog.isShowing()) {
			mUpdateProgramDialog.dismiss();
			mUpdateProgramDialog = null;
		}
	}

	private class InternalReceiver extends BroadcastReceiver {
		@Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_MOUNTED) || 
                action.equals(Intent.ACTION_MEDIA_REMOVED) || 
                action.equals(Intent.ACTION_MEDIA_BAD_REMOVAL))
            {
                String path = intent.getData().getPath();
                if (path.substring(5).startsWith(Contants.UDISK_NAME_PREFIX))
                {
                    if (action.equals(Intent.ACTION_MEDIA_MOUNTED) && 
                        PosterApplication.existsPgmInUdisk(path))
                    {
                        showUpdateProgramDialog();
                    }
                    else
                    {
                        dismissUpdateProgramDialog();
                    }
                }
            }
        }
	}

	@SuppressLint("HandlerLeak")
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case EVENT_CHECK_SET_ONOFFTIME:
				PowerOnOffManager.getInstance().checkAndSetOnOffTime(
						(msg.getData().getInt("type")));
				break;
			}
			super.handleMessage(msg);
		}
	};

	public void checkAndSetOnOffTime(int type) {
		Bundle bundle = new Bundle();
		bundle.putInt("type", type);
		Message msg = mHandler.obtainMessage();
		msg.what = EVENT_CHECK_SET_ONOFFTIME;
		msg.setData(bundle);
		msg.sendToTarget();
	}

	private void cleanupLayout() 
	{
		/**************************
		 * 注意：辅屏的主窗口不能移除，否 *
		 * 则SurfaceView将显示不出来    *
		 **************************/
		if (mMainWindow != null)
		{
			mMainWindow.stopWork();
			mMainWindow.setMediaList(null);
			mMainWindow.setViewPosition(0, 0);
			mMainWindow.setViewSize(0, 0);
		}
		
		// 移除子窗口
		if (mSubWndCollection != null)
        {
            for (PosterBaseView subWnd : mSubWndCollection)
            {
            	subWnd.onViewDestroy();
            	if (subWnd instanceof MultiMediaView)
            	{
            		((MultiMediaView)subWnd).clearViews();
            	}
            	subWnd.setVisibility(View.GONE);
            	mMainLayout.removeView(subWnd);
            }
            
            mSubWndCollection.clear();
            mSubWndCollection = null;
        }

		// 清除背景图片
		if (mBgImgInfo != null)
		{
		    mBgImgInfo = null;
		    mMainLayout.setBackground(null);
		}
		
		// 清空上一个节目的缓存
        PosterApplication.clearMemoryCache();
	}
	
	// 加载新节目
	public void loadNewProgram(ArrayList<SubWindowInfoRef> subWndList) 
	{
		// Create new program windows
        if (subWndList != null)
        {
        	Logger.i("Window number is: " + subWndList.size());
        	
    		// Clean old program
    		cleanupLayout();

            // initialize
            int xPos = 0;
            int yPos = 0;
            int width = 0;
            int height = 0;
            String wndName = null;
            String wndType = null;
            List<MediaInfoRef> mediaList = null;
            
            PosterBaseView tempSubWnd = null;
            mSubWndCollection = new HashSet<PosterBaseView>();
            
            // Through the sub window list, and create the correct view for it.
            for (SubWindowInfoRef subWndInfo : subWndList)
            {
                tempSubWnd = null;
                
                // 窗体类型和名称
                if ((wndType = subWndInfo.getSubWindowType()) == null)
                {
                    continue;
                }
                wndName = subWndInfo.getSubWindowName();
                
                // 窗体位置
                xPos = subWndInfo.getXPos();
                yPos = subWndInfo.getYPos();
                width = subWndInfo.getWidth();
                height = subWndInfo.getHeight();
                
                // 素材
                mediaList = subWndInfo.getSubWndMediaList();
                
                // 创建窗口
                if (wndType.contains("Main") || wndType.contains("StandbyScreen"))
                {
                	if (mMainWindow == null)
                	{
                		mMainWindow = new MultiMediaView(this, true);
                		tempSubWnd = mMainWindow;
                	}
                	else
                	{
                		mMainWindow.setViewName(wndName);
                		mMainWindow.setViewType(wndType);
                		mMainWindow.setMediaList(mediaList);
                		mMainWindow.setViewPosition(xPos, yPos);
                		mMainWindow.setViewSize(width, height);
                        continue;
                	}
                }
                else if (wndType.contains("Background"))
                {
                    // 背景图片
                    if (mediaList != null && mediaList.size() > 0 && "File".equals(mediaList.get(0).source))
                    {
                        mBgImgInfo = mediaList.get(0);
                        setWindowBackgroud();
                    }
                    continue;
                }
                else if (wndType.contains("Image") || wndType.contains("Weather"))
                {
                	tempSubWnd = new MultiMediaView(this);
                }
                else if (wndType.contains("Audio"))
                {
                    tempSubWnd = new AudioView(this);
                }
                else if (wndType.contains("Scroll"))
                {
                    tempSubWnd = new MarqueeView(this);
                }
                else if (wndType.contains("Clock"))
                {
                    tempSubWnd = new DateTimeView(this);
                }
                else if (wndType.contains("Gallery"))
                {
                    tempSubWnd = new GalleryView(this);
                }
                else if (wndType.contains("Web"))
                {
                    tempSubWnd = new YSWebView(this);
                }
                else if (wndType.contains("Timer"))
                {
                    tempSubWnd = new TimerView(this);
                }
                
                // 设置窗口参数，并添加
                if (tempSubWnd != null)
                {
                    tempSubWnd.setViewName(wndName);
                    tempSubWnd.setViewType(wndType);
                    tempSubWnd.setMediaList(mediaList);
                    tempSubWnd.setViewPosition(xPos, yPos);
                    tempSubWnd.setViewSize(width, height);
                    mMainLayout.addView(tempSubWnd);
					if (tempSubWnd != mMainWindow) 
					{
						mSubWndCollection.add(tempSubWnd);
					}
                }
            }
        }
        
        if (mSubWndCollection != null)
        {
        	if (mMainWindow != null)
        	{
        		mMainWindow.startWork();
        	}
        	
            for (PosterBaseView subWnd : mSubWndCollection)
            {
            	subWnd.startWork();
            }
        }

        if (PosterApplication.getInstance().isDaulScreenMode() && 
           !PosterApplication.getInstance().isShowInExtendDisplay())
        {
        	mHandler.postDelayed(rGoToExtendScreenDelay, 200);
            PosterApplication.getInstance().setShowInExtendDisplay(true);
        }
	}

	/**
     * Set the background picture of the window.
     */
    private boolean setWindowBackgroud()
    {
        mHandler.removeCallbacks(rSetWndBgDelay);
        
        if (mMainLayout == null || mBgImgInfo == null)
        {
            Logger.i("Main layout didn't ready, can't load background image.");
            mHandler.postDelayed(rSetWndBgDelay, 500);
            return false;
        }
        else if (!FileUtils.isExist(mBgImgInfo.filePath))
        {
            Logger.i("Background Image [" + mBgImgInfo.filePath + "] didn't exist.");
            PosterBaseView.downloadMedia(mBgImgInfo);
            mHandler.postDelayed(rSetWndBgDelay, 500);
            return false;
        }
        else if (!PosterBaseView.md5IsCorrect(mBgImgInfo))
        {
            Logger.i("Background Image [" + mBgImgInfo.filePath + "] verifycode is wrong.");
            PosterBaseView.downloadMedia(mBgImgInfo);
            mHandler.postDelayed(rSetWndBgDelay, 500);
            return false;
        }

        // 读取图片
        Bitmap mBgBmp = loadBgPicture(mBgImgInfo);
        
        // 图片生成失败
        if (mBgBmp == null)
        {
            mHandler.postDelayed(rSetWndBgDelay, 500);
            return false;
        }
        else
        {
            // 设置背景
            mMainLayout.setBackground(new BitmapDrawable(getResources(), mBgBmp));
        }
        
        return true;
    }
    
    private Bitmap loadBgPicture(final MediaInfoRef picInfo)
    {
        Bitmap srcBmp = null;

        try
        {
            if (picInfo == null || FileUtils.mediaIsPicFromNet(picInfo))
            {
                Logger.e("picture is come from network");
                return null;
            }

            // Create the Stream
            InputStream isImgBuff = PosterBaseView.createImgInputStream(picInfo);

            try
            {
                if (isImgBuff != null)
                {
                    // Create the bitmap for BitmapFactory
                    srcBmp = BitmapFactory.decodeStream(isImgBuff, null, PosterBaseView.setBitmapOption(picInfo));
                }
            }
            catch (java.lang.OutOfMemoryError e)
            {
                Logger.e("picture is too big, out of memory!");

                if (srcBmp != null && !srcBmp.isRecycled())
                {
                    srcBmp.recycle();
                    srcBmp = null;
                }
                
                System.gc();
            }
            finally
            {
                if (isImgBuff != null)
                {
                    isImgBuff.close();
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        
        return srcBmp;
    }
    
    private Runnable rHideOsdPopWndDelay = new Runnable() {
		@Override
		public void run() {
			mHandler.removeCallbacks(rHideOsdPopWndDelay);
			if (mOsdPupupWindow != null && mOsdPupupWindow.isShowing()) {
				mOsdPupupWindow.dismiss();
			}
		}
	};
	
    /**
     * 如果背景图片不存在，则轮循检测图片文件是否下载完成.
     */
    private Runnable rSetWndBgDelay   = new Runnable() {
        @Override
        public void run()
        {
            setWindowBackgroud();
        }
    };
                                      
	private Runnable rGoToExtendScreenDelay = new Runnable() {
		@Override
		public void run() 
		{
			sendToExtendScreen();
		}
	};
	
	private Runnable rStartMainScreenApk = new Runnable() {
		@Override
		public void run() 
		{
			bootMainScreenApk();
		}
	};
	
    /**
     * go to extend screen
     */
    private void sendToExtendScreen()
    {
        mHandler.removeCallbacks(rGoToExtendScreenDelay);
        ((ExtendDisplayManager)getSystemService(Context.EXTEND_DISPLAY_SERVICE)).moveTo(this);
        
        // 启动主屏的APK
        mHandler.postDelayed(rStartMainScreenApk, 200);
    }
    
    /**
     * boot main screen apk
     */
    private void bootMainScreenApk()
    {
        mHandler.removeCallbacks(rStartMainScreenApk);
        
        String pkgName = PosterApplication.getInstance().getConfiguration().getBootPackageName();
		if (!YSConfiguration.BOOT_APK_PACKAGE_NAME_NONE.equals(pkgName) &&
			apkIsExist(pkgName)) 
		{
			startActivity(PosterApplication.getInstance().getPackageManager()
					.getLaunchIntentForPackage(pkgName));
		}
    }
    
    private boolean apkIsExist(String packageName)
    {
    	if (!TextUtils.isEmpty(packageName))
    	{
    		try
        	{
        		ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
        		return (info != null);
        	} 
        	catch (NameNotFoundException e)
        	{
        		return false;
        	}
    	}
    	
    	return false;
    }
    
	public void setPopServiceRunning(boolean isRunning) {
		synchronized (this) {
			isPopServiceRunning = isRunning;
		}
	}

	public void startPopSub(String text, int playSpeed, int duration,
			int number, String fontName, int fontColor) 
	{
        synchronized (this)
        {
            if (isPopServiceRunning == true)
            {
                stopService(popService);
            }
            
            popService = new Intent(this, PopSubService.class);
            popService.putExtra(PopSubService.DURATION, duration);
            popService.putExtra(PopSubService.NUMBER, number);
            popService.putExtra(PopSubService.TEXT, text);
            popService.putExtra(PopSubService.FONTCOLOR, fontColor);
            popService.putExtra(PopSubService.FONTNAME, (fontName != null) ? fontName.substring(fontName.lastIndexOf(File.separator) + 1, fontName.lastIndexOf(".")) : null);
            popService.putExtra(PopSubService.SPEED, playSpeed);
            Logger.i("Start popService");
            startService(popService);
            isPopServiceRunning = true;
        }
	}

	public void startAudio()
    {
    	if (mSubWndCollection != null)
        {
            for (PosterBaseView wnd : mSubWndCollection)
            {
                if (wnd.getViewName().startsWith("Audio"))
                {
                	wnd.onViewResume();
                }
            }
        }
    }
    
    public void stopAudio()
    {
    	if (mSubWndCollection != null)
        {
            for (PosterBaseView wnd : mSubWndCollection)
            {
                if (wnd.getViewName().startsWith("Audio"))
                {
                	wnd.onViewPause();
                }
            }
        }
    }

	public Bitmap combineScreenCap(Bitmap bitmap) {

		if (mMainWindow != null && mMainWindow.needCombineCap()) 
		{
			Bitmap videoCap = ((MultiMediaView) mMainWindow).getVideoCap();
			if (videoCap != null) 
			{
				Bitmap newb = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
				Canvas cv = new Canvas(newb);
				cv.drawBitmap(bitmap, 0, 0, null);
				Paint paint = new Paint();
				paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
				cv.drawBitmap(videoCap, mMainWindow.getXPos(), mMainWindow.getYPos(), paint);
				cv.save(Canvas.ALL_SAVE_FLAG);
				cv.restore();
				return newb;
			}
		}

		return bitmap;
	}
	
	// 初始化OSD弹出菜单
	private void initOSD() {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View osdView = inflater.inflate(R.layout.osd_pop_menu_view, null);
		osdView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
			    showOsd();
			}
		});
		
		mOsdPupupWindow = new PopupWindow(osdView, 100, LinearLayout.LayoutParams.MATCH_PARENT, true);
		mOsdPupupWindow.setAnimationStyle(R.style.osdAnimation);
		mOsdPupupWindow.setOutsideTouchable(false);
		mOsdPupupWindow.setFocusable(true);

		// 初始化点击动作
		((ImageView) osdView.findViewById(R.id.osd_mainmenu))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_MAIN_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_server))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_SERVER_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_clock))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_CLOCK_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_system))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_SYSTEM_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_filemanage))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_FILEMANAGER_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_tools))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_TOOL_ID);
					}
				});

		((ImageView) osdView.findViewById(R.id.osd_about))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						enterToOSD(PosterOsdActivity.OSD_ABOUT_ID);
					}
				});
	}

	private void enterToOSD(int menuId) {
		//PosterApplication.getInstance().initLanguage();
		Intent intent = new Intent(this, PosterOsdActivity.class);
		intent.putExtra("menuId", menuId);
		startActivity(intent);
		
		if (mOsdPupupWindow.isShowing()) {
			mOsdPupupWindow.dismiss();
		}
	}

    private void hideNavigationBar() {
        int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
            | View.SYSTEM_UI_FLAG_FULLSCREEN;     // hide status bar

        if (android.os.Build.VERSION.SDK_INT >= 19){ 
            uiFlags |= 0x00001000;    //SYSTEM_UI_FLAG_IMMERSIVE_STICKY: hide navigation bars - compatibility: building API level is lower thatn 19, use magic number directly for higher API target level
        } else {
            uiFlags |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
        }

        getWindow().getDecorView().setSystemUiVisibility(uiFlags);
    }
}
