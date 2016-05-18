/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.dual.posterdisplayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.youngsee.dual.common.Contants;
import com.youngsee.dual.common.DiskLruCache;
import com.youngsee.dual.common.ElectricManager;
import com.youngsee.dual.common.FileUtils;
import com.youngsee.dual.common.MediaInfoRef;
import com.youngsee.dual.common.ReflectionUtils;
import com.youngsee.dual.common.RuntimeExec;
import com.youngsee.dual.common.SysOnOffTimeInfo;
import com.youngsee.dual.common.SysParamManager;
import com.youngsee.dual.common.YSConfiguration;
import com.youngsee.dual.customview.PosterBaseView;
import com.youngsee.dual.ftpoperation.FtpFileInfo;
import com.youngsee.dual.ftpoperation.FtpHelper;
import com.youngsee.dual.ftpoperation.FtpOperationInterface;
import com.youngsee.dual.logmanager.Logger;
import com.youngsee.dual.posterdisplayer.R;
import com.youngsee.dual.screenmanager.ScreenManager;
import com.youngsee.dual.webservices.SysParam;
import com.youngsee.dual.webservices.WsClient;
import com.youngsee.dual.webservices.XmlCmdInfoRef;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.util.LruCache;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.view.View;

public class PosterApplication extends Application
{
    private static PosterApplication        INSTANCE                       = null;
    
    private YSConfiguration                 mConfiguration                 = null;

    private static byte[]                   mEthMac                        = null;
    private static String                   mCpuId                         = null;
    
    private static String                   mLogFilePath                   = null;
    private static String                   mAPKUpdateFullPath             = null;
    private static String                   mStandbyScreenImgFullPath      = null;
    private static String                   mStartUpScreenImgFullPath      = null;
    private static String                   mCaptureScreenImgFullPath      = null;
    private static String                   mTempFolderFullPath            = null;
    private static String                   mMacAddressFilePath            = null;

    // Define the image cache (per APP instance)
    private static LruCache<String, Bitmap> mImgMemoryCache          = null;
    
    // Define the image disk cache (per APP instance)
    private static DiskLruCache             mImgDiskCache                  = null;
    private static final int                DISK_CACHE_SIZE                = 1024 * 1024 * 40; // 40MB
                                                                                                
    /* Defined file name */
    public static final String              LOCAL_CONFIG_FILENAME          = "isconfig.xml";
    public static final String              LOCAL_CAST_FILENAME_T          = "playlist_t.xml";
    public static final String              LOCAL_NORMAL_PLAYLIST_FILENAME = "playlist.xml";
    public static final String              LOCAL_CASTE_FILENAME_T         = "playliste_t.xml";
    public static final String              LOCAL_EMGCY_PLAYLIST_FILENAME  = "playliste.xml";
    public static final String              LOCAL_SYSTEM_LOGNAME           = "common_";
    public static final String              LOCAL_PLAY_LOGNAME             = "play_";

    // Tag String
    private final String                    NORMAL_FRAGMENT_TAG            = "NormalProgramTag";
    private final String                    URGENT_FRAGMENT_TAG            = "UrgentProgramTag";

    private Timer                           mDelPeriodFileTimer            = null;
    private Timer                           mUploadLogTimer                = null;
    private Timer  							mGetElectricTimer         	   = null;
    
    private AlarmManager mAlarmManager = null;
    
    private final String SYSPROP_HWROTATION_CLASS = "android.os.SystemProperties";
    private final String SYSPROP_HWROTATION_GETMETHOD = "getInt";
    private final String SYSPROP_HWROTATION = "persist.sys.hwrotation";
    private final int SYSPROP_HWROTATION_DEFAULT = -1;

    private boolean                         mShowInExtendDisplay           = false;
    
    public static PosterApplication getInstance()
    {
        return INSTANCE;
    }
    
    @Override
    public void onCreate()
    {
        super.onCreate();
        INSTANCE = this;
        
        // Allocate memory for the image cache space to program
        int cacheSize = (int) Runtime.getRuntime().maxMemory() / 10;
        mImgMemoryCache = new LruCache<String, Bitmap>(cacheSize)
        {
            @Override
            protected int sizeOf(String key, Bitmap bitmap)
            {
                int nSize = 0;
                if (bitmap != null)
                {
                    nSize = bitmap.getByteCount();
                }
                return nSize;
            }
            
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldBitmap, Bitmap newBitmap)
            {
                if (evicted)
                {
                    if ((oldBitmap != null) && (!oldBitmap.isRecycled()))
                    {
                        oldBitmap.recycle();
                        oldBitmap = null;
                    }
                }
            }
        };
           
        // Allocate disk size for the image disk cache space
        File cacheDir = DiskLruCache.getDiskCacheDir(this, "thumbnails");
        if ((mImgDiskCache = DiskLruCache.openCache(this, cacheDir, DISK_CACHE_SIZE)) != null)
        {
            mImgDiskCache.clearCache();
        }
        
        mAlarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        mConfiguration = YSConfiguration.getInstance(this);
    }

    public String getNormalPgmTag()
    {
        return NORMAL_FRAGMENT_TAG;
    }
    
    public String getUrgentPgmTag()
    {
        return URGENT_FRAGMENT_TAG;
    }
    
    public void initAppParam()
    {
        getEthMacAddress();
        SysParamManager.getInstance().initSysParam();
    }
    
    public String getKernelVersion()
    {
        String strVersion = null;
        RandomAccessFile reader = null;
        try
        {
            reader = new RandomAccessFile("/proc/version", "r");
            strVersion = reader.readLine();
        }
        catch (Exception e)
        {
        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        
        if (strVersion != null)
        {
            String verisonBuf[] = strVersion.split("\\s+");
            for (String strVer : verisonBuf)
            {
                if (strVer.contains("."))
                {
                    return strVer;
                }
            }
        }
        
        return strVersion;
    }
    
    public int getVerCode()
    {
        int verCode = -1;
        try
        {
            verCode = getPackageManager().getPackageInfo(Contants.POSTER_PACKAGENAME, 0).versionCode;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return verCode;
    }
    
    public String getVerName()
    {
        String verName = "";
        try
        {
            verName = getPackageManager().getPackageInfo(Contants.POSTER_PACKAGENAME, 0).versionName;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return verName;
    }
    
    public static int getScreenHeigth()
    {
    	int screenHeight = 0;
    	
    	if (PosterMainActivity.INSTANCE != null)
    	{
    	    // 获取状态栏的高度
	        int resourceId = PosterMainActivity.INSTANCE.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
	        int height = PosterMainActivity.INSTANCE.getResources().getDimensionPixelSize(resourceId);
	    
		    // 获取屏幕实际大小(以像素为单位)
		    DisplayMetrics metric = new DisplayMetrics();
		    PosterMainActivity.INSTANCE.getWindowManager().getDefaultDisplay().getMetrics(metric);
		    screenHeight = metric.heightPixels + height;
    	}
				
        return screenHeight;
    }
    
    public static int getScreenWidth()
    {
    	int screenWidth = 0;
    	
    	if (PosterMainActivity.INSTANCE != null)
    	{
    	    // 获取屏幕实际大小(以像素为单位)
    	    DisplayMetrics metric = new DisplayMetrics();
    	    PosterMainActivity.INSTANCE.getWindowManager().getDefaultDisplay().getMetrics(metric);
    	    screenWidth = metric.widthPixels;
    	}
    	
        return screenWidth;
    }
    
    public static void addBitmapToMemoryCache(String key, Bitmap bitmap)
    {
        if (mImgMemoryCache != null)
        {
            mImgMemoryCache.put(key, bitmap);
        }
    }
    
    public static Bitmap getBitmapFromMemoryCache(String key)
    {
        if (mImgMemoryCache != null)
        {
            Bitmap bmp = mImgMemoryCache.get(key);
            if (bmp != null && !bmp.isRecycled())
            {
                return bmp;
            }
        }
        return null;
    }
    
    public static void clearMemoryCache()
    { 
        if (mImgMemoryCache != null)
        {
        	mImgMemoryCache.evictAll();
            System.gc();
        }
    }
    
    public static void addBitmapToDiskCache(String key, Bitmap bitmap)
    {
        if (mImgDiskCache == null)
        {
            return;
        }
        
        // if cache didn't have the bitmap, then save it.
        if (mImgDiskCache.get(key) == null)
        {
            mImgDiskCache.put(key, bitmap);
        }
    }
    
    public static Bitmap getBitmapFromDiskCache(String key)
    {
        if (mImgDiskCache == null)
        {
            return null;
        }
        
        return mImgDiskCache.get(key);
    }
    
    public static void clearDiskCache()
    {
        if (mImgDiskCache != null)
        {
            mImgDiskCache.clearCache();
        }
    }
    
    public static int resizeImage(Bitmap bitmap, String destPath, int width, int height)
    {
        int swidth = bitmap.getWidth();
        int sheight = bitmap.getHeight();
        float scaleWidht = (float) width / swidth;
        float scaleHeight = (float) height / sheight;
        Matrix matrix = new Matrix();
        matrix.setScale(scaleWidht, scaleHeight);
        Bitmap newbm = Bitmap.createBitmap(bitmap, 0, 0, swidth, sheight, matrix, true);
        File saveFile = new File(destPath);
        FileOutputStream fileOutputStream = null;
        
        try
        {
            saveFile.createNewFile();
            fileOutputStream = new FileOutputStream(saveFile);
            if (fileOutputStream != null)
            {
                // 把位图的压缩信息写入到一个指定的输出流中
                // 第一个参数format为压缩的格式
                // 第二个参数quality为图像压缩比的值,0-100.0 意味着小尺寸压缩,100意味着高质量压缩
                // 第三个参数stream为输出流
                newbm.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
            }
            fileOutputStream.flush();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return 1;
        }
        finally
        {
            if (fileOutputStream != null)
            {
                try
                {
                    fileOutputStream.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        
        return 0;
    }
    
    public Bitmap getDefaultScreenImg()
    {
        Bitmap dstImg = null;
        
        // 从Resource中获取 (默认的待机画面)
        if (getScreenWidth() < getScreenHeigth())
        {
        	// portrait
        	dstImg = BitmapFactory.decodeResource(getResources(), R.drawable.pdaiji);
        }
        else
        {
        	// landscape
        	dstImg = BitmapFactory.decodeResource(getResources(), R.drawable.daiji);
        }
        return dstImg;
    }

    public synchronized SysParam factoryRest()
    {
        SysParam sysParam = new SysParam();

        sysParam.netConn = new ConcurrentHashMap<String, String>();
        sysParam.netConn.put("mode", "DHCP");
        sysParam.netConn.put("ip", "0.0.0.0");
        
        sysParam.serverSet = new ConcurrentHashMap<String, String>();
        sysParam.serverSet.put("weburl", getConfiguration().getDefualtServerUrl() + WsClient.SERVICE_URL_SUFFIX);
        sysParam.serverSet.put("ftpip", "ftp.xuanchuanyun.com");
        sysParam.serverSet.put("ftpport", "21");
        sysParam.serverSet.put("ftpname", "ehualu");
        sysParam.serverSet.put("ftppasswd", "ehualu$888");
        sysParam.serverSet.put("ntpip", "server.xuanchuanyun.com");
        sysParam.serverSet.put("ntpport", "123");
        
        sysParam.sigOutSet = new ConcurrentHashMap<String, String>();
        sysParam.sigOutSet.put("mode", "3");
        sysParam.sigOutSet.put("value", "10");
        sysParam.sigOutSet.put("repratio", "100");
        
        sysParam.wifiSet = new ConcurrentHashMap<String, String>();
        sysParam.wifiSet.put("ssid", "");
        sysParam.wifiSet.put("wpapsk", "");
        sysParam.wifiSet.put("authmode", "");
        sysParam.wifiSet.put("encryptype", "");
        
        sysParam.onOffTime = new ConcurrentHashMap<String, String>();
        sysParam.onOffTime.put("group", "0");

        sysParam.setBit = 0;
        sysParam.getTaskPeriodtime = 30;
        sysParam.delFilePeriodtime = 30;
        sysParam.timeZonevalue = "-8";
        sysParam.passwdvalue = "";
        sysParam.syspasswdvalue = "";
        sysParam.brightnessvalue = 60;
        sysParam.volumevalue = 60;
        sysParam.hwVervalue = "1.0.0.0";
        sysParam.swVervalue = getVerName();
        sysParam.kernelvervalue = getKernelVersion();
        sysParam.cfevervalue = android.os.Build.VERSION.RELEASE;
        sysParam.certNumvalue = "";
        sysParam.termmodelvalue = "JWA-YS200";
        sysParam.termname = "悦视显示终端";
        sysParam.termGrpvalue = "无";
        sysParam.dispScalevalue = 2; /* 16:9 */
        
        return sysParam;
    }
    
    // //////////////////////////////////////////////////////////////////////////////////////
    
    /*
     * 获取节目存储的路径 选用外部最大的存储空间做为节目的存储介质 注：路径有可能实时变化，因为U盘和SD卡随时可能插拔
     */
    public static String getProgramPath()
    {
    	StringBuilder sb = new StringBuilder();
        sb.append(FileUtils.getLargestExtStorage());
        sb.append(File.separator);
        sb.append("dualpgm");
        String path = sb.toString();
        
        if(!FileUtils.isExist(path)){
            FileUtils.createDir(path);
        }
        
        //DbHelper.getInstance().setPgmPath(path);
        
        return path;
    }
    
    public static boolean existsPgmInUdisk(String path) {
    	if ((path == null)
    			|| (path.length() < 13)
    			|| !path.substring(5).startsWith(Contants.UDISK_NAME_PREFIX)) {
    		return false;
    	}
    	File udisk = new File(path);
    	if (udisk.getTotalSpace() > 0) {
    		File[] files = udisk.listFiles();
    		for (File file : files) {
    			if (file.isDirectory() && file.getName().endsWith(".pgm")) {
    				return true;
    			}
    		}
    	} else {
    		File[] files = udisk.listFiles();
    		if (files != null) {
    			for (File file : files) {
    				if (file.getTotalSpace() > 0) {
    					File[] subFiles = file.listFiles();
    					for (File subFile : subFiles) {
    		    			if (subFile.isDirectory() && subFile.getName().endsWith(".pgm")) {
    		    				return true;
    		    			}
    		    		}
    				}
    			}
    		}
    	}
    	return false;
    }
    
    public static String getGifImagePath(String subDirName)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(FileUtils.getExternalStorage());
        sb.append(File.separator);
        sb.append("Gif");
        if (subDirName != null)
        {
            sb.append(File.separator);
            sb.append(subDirName);
        }
        if (!FileUtils.isExist(sb.toString()))
        {
            FileUtils.createDir(sb.toString());
        }
        
        return sb.toString();
    }
    
    /*
     * 获取系统参数文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getAPKUpdateFullPath()
    {
        if (mAPKUpdateFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("dualApkUpdate");
            mAPKUpdateFullPath = sb.toString();
            
            if (!FileUtils.isExist(mAPKUpdateFullPath))
            {
                FileUtils.createDir(mAPKUpdateFullPath);
            }
        }
        
        return mAPKUpdateFullPath;
    }

    /*
     * 获取系统参数文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getStandbyScreenImgPath()
    {
        if (mStandbyScreenImgFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("dualBgImg");
            sb.append(File.separator);
            
            // 创建目录
            if (!FileUtils.isExist(sb.toString()))
            {
                FileUtils.createDir(sb.toString());
            }
            
            mStandbyScreenImgFullPath = sb.append("background.jpg").toString();
        }

        return mStandbyScreenImgFullPath;
    }
    
    /*
     * 获取开机画面系统参数文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getStartUpScreenImgPath()
    {
        if (mStartUpScreenImgFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("dualStarupImg");
            sb.append(File.separator);
            
            // 创建目录
            if (!FileUtils.isExist(sb.toString()))
            {
                FileUtils.createDir(sb.toString());
            }
            
            mStartUpScreenImgFullPath = sb.append("startup.jpg").toString();
        }
        return mStartUpScreenImgFullPath;
    }
    
    /*
     * 获取系统参数文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getScreenCaptureImgPath()
    {
        if (mCaptureScreenImgFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("dualCpImg");
            sb.append(File.separator);
            mCaptureScreenImgFullPath = sb.toString();
            
            // 创建目录
            if (!FileUtils.isExist(mCaptureScreenImgFullPath))
            {
                FileUtils.createDir(mCaptureScreenImgFullPath);
            }
        }
        
        return mCaptureScreenImgFullPath;
    }
    
    /*
     * 获取系统参数临时文件存储的路径 注：或有外部存储设备则优先选用外部存储 (外部存储-->私有空间)
     */
    public static String getTempFolderPath()
    {
        if (mTempFolderFullPath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getHardDiskPath());
            sb.append(File.separator);
            sb.append("dualTmp");
            sb.append(File.separator);
            mTempFolderFullPath = sb.toString();
            
            // 创建目录
            if (!FileUtils.isExist(mTempFolderFullPath))
            {
                FileUtils.createDir(mTempFolderFullPath);
            }
        }
        
        return mTempFolderFullPath;
    }

    /**
     * 获取日志文件存储路径
     * 
     * @param type
     *            0：播放日志 ;1：系统日志
     * @param date
     *            0:今天；1：昨天
     * @return 昨天的日志文件路径
     */
    public static String getLogFileFullPath(int type, int date)
    {
        if (mLogFilePath == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(FileUtils.getExternalStorage());
            sb.append(File.separator);
            sb.append("dualpgmlog");
            sb.append(File.separator);
            mLogFilePath = sb.toString();
        }
        
        // 创建目录
        if (!FileUtils.isExist(mLogFilePath))
        {
            FileUtils.createDir(mLogFilePath);
        }
        
        StringBuilder sb1 = new StringBuilder();
        sb1.append(mLogFilePath);
        
        if (type == 0)
        {
            sb1.append(PosterApplication.LOCAL_PLAY_LOGNAME);
        }
        else if (type == 1)
        {
            sb1.append(PosterApplication.LOCAL_SYSTEM_LOGNAME);
        }
        sb1.append(getEthMacStr()).append("_");
        
        if (date == 0)
        {
            sb1.append(PosterApplication.getCurrentDate());
        }
        else if (date == 1)
        {
            sb1.append(PosterApplication.getYesterdayDate());
        }
        sb1.append(".log");
        
        return sb1.toString();
    }

    public boolean isDaulScreenMode() {
        return Settings.System.getInt(getContentResolver(), Settings.System.DUAL_SCREEN_MODE, 0) != 0;
    }

    public boolean isShowInExtendDisplay() {
        return mShowInExtendDisplay;
    }

    public void setShowInExtendDisplay(boolean flag) {
        mShowInExtendDisplay = flag;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////

	public static String getCpuId() {
        if (TextUtils.isEmpty(mCpuId))
        {
            BufferedReader reader = null;
            String line = null;
            try
            {
                reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
                while ((line = reader.readLine()) != null)
                {
                    String[] subStr = line.split(":");
                    if (subStr[0].trim().equals("Serial"))
                    {
                        mCpuId = subStr[1].trim();
                        break;
                    }
                }
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            finally
            {
                if (reader != null)
                {
                    try
                    {
                        reader.close();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
		return mCpuId;
	}
    
    public static String getLocalIpAddress()
    {
        try
        {
            String strIpAddress = null;
            NetworkInterface intf = null;
            if ((intf = NetworkInterface.getByName("eth0")) != null && (strIpAddress = getIpv4Address(intf)) != null)
            {
                return strIpAddress;
            }
            else if ((intf = NetworkInterface.getByName("wlan0")) != null && (strIpAddress = getIpv4Address(intf)) != null)
            {
                return strIpAddress;
            }
        }
        catch (SocketException ex)
        {
            Logger.e("Get IpAddress has error, the msg is: " + ex.toString());
        }
        
        return "";
    }

    private String getMacFileName() {
    	if (mMacAddressFilePath == null)
    	{
		    StringBuilder sb = new StringBuilder();
		    sb.append(this.getFilesDir().getPath());
		    sb.append(File.separator);
		    sb.append("mac");
		    sb.append(File.separator);
		    
		    // 创建目录
		    if (!FileUtils.isExist(sb.toString())) {
		    	FileUtils.createDir(sb.toString());
	    	}
		    
		    sb.append("mac_address.txt");
	    	try {
		    	FileUtils.createFile(sb.toString());
	    	} catch (IOException e) {
			    // TODO Auto-generated catch block
			    e.printStackTrace();
		    }
		    mMacAddressFilePath = sb.toString();
    	}

		return mMacAddressFilePath;
	}
    
    private static void changeEthMacAddress(byte[] mac) {
    	if (mac == null) {
    		Logger.e("Mac is null.");
    		return;
    	} else if (mac.length == 0) {
    		Logger.e("The length of mac is 0.");
    		return;
    	}

    	mac[0] += 1;
    }

    public static void updateEthMacAddress(byte[] newMac){
    	mEthMac = newMac;
    	FileUtils.writeSDFileData(INSTANCE.getMacFileName(), newMac, false);
    }

    // 固定用网口的MAC地址做为与服务器通信的Device_ID
    public static synchronized byte[] getEthMacAddress()
    {
        if (mEthMac != null)
        {
            return mEthMac;
        }
        
        mEthMac = FileUtils.readSDFile(INSTANCE.getMacFileName());
        if (mEthMac == null||mEthMac.length==0)
        {
        	try
            {
                NetworkInterface intf = null;
                if ((intf = NetworkInterface.getByName("eth0")) != null)
                {
                	mEthMac = intf.getHardwareAddress();
                	changeEthMacAddress(mEthMac);
                	FileUtils.writeSDFileData(INSTANCE.getMacFileName(), mEthMac, true);
                }
            }
            catch (SocketException ex)
            {
                Logger.e("Get MacAddress has error, the msg is: " + ex.toString());
            }
        }
        return mEthMac;
    }
    
    public static String getEthFormatMac()
    {
        byte[] mac = getEthMacAddress();
        if (mac != null)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mac.length; i++)
            {
                sb.append(String.format("%02x", mac[i]));
                if (i < 5)
                {
                    sb.append(":");
                }
            }
            return sb.toString();
        }
        return "";
    }
    
    /**
     * 获取mac
     * 
     * @return 数字格式mac
     */
    public static String getEthMacStr()
    {
        return getEthFormatMac().replace(":", "");
    }
    
    private static String getIpv4Address(NetworkInterface netIF)
    {
        for (Enumeration<InetAddress> enumIpAddr = netIF.getInetAddresses(); enumIpAddr.hasMoreElements();)
        {
            InetAddress inetAddress = enumIpAddr.nextElement();
            if (!inetAddress.isLoopbackAddress() && (inetAddress instanceof Inet4Address))
            {
                return inetAddress.getHostAddress().toString();
            }
        }
        return null;
    }

    public boolean isNetworkConnected()
    {
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null)
        {
            NetworkInfo info = connectivity.getActiveNetworkInfo();
            if (info != null && info.isAvailable())
            {
                return info.isConnected();
            }
        }
        return false;
    }
    
    public boolean isNetReached(String strHostName)
    {
        boolean bRet = false;
        try
        {
            bRet = InetAddress.getByName(strHostName).isReachable(3000);
        }
        catch (UnknownHostException e)
        {
            bRet = false;
            Logger.w(e.toString());
        }
        catch (IOException e)
        {
            bRet = false;
            Logger.w(e.toString());
        }
        
        return bRet;
    }
    
    public boolean httpServerIsReady(String httpUri)
    {
    	HttpGet httpRequest = null;
        HttpResponse response = null;
        System.setProperty("http.keepAlive", "false");
        try
        {
            httpRequest = new HttpGet(httpUri);
            response = new DefaultHttpClient().execute(httpRequest);
        }
        catch (ClientProtocolException e)
        {
            response = null;
            Logger.w(e.toString());
        }
        catch (IOException e)
        {
            response = null;
            Logger.w(e.toString());
        }
        catch (Exception e)
        {
            response = null;
            Logger.w(e.toString());
        }
        finally
        {
        	if (httpRequest != null)
        	{
        		httpRequest.abort();
        	}
        }
        
        if (response != null)
        {
            int code = response.getStatusLine().getStatusCode();
            return (HttpStatus.SC_OK == code);
        }
        
        return false;
    }
    
    /*
     * Determinate the time download is forbidden
     */
    public static boolean isBeForbidTime()
    {
        ConcurrentHashMap<String, String>  offdlTime = SysParamManager.getInstance().getOffDlTimeParam();
        if (offdlTime != null)
        {
            int nweek = Calendar.getInstance(Locale.CHINA).get(Calendar.DAY_OF_WEEK) - 1;
            int oweekMask = (0x01 << nweek) & 0xFF; // 当天的星期数掩码
            int group = Integer.parseInt(offdlTime.get("group")); // 时间段的数目
            int sweek = 0;
            for (int i = 1; i < group + 1; i++)
            {
                sweek = Integer.parseInt(offdlTime.get("week" + i)); // 该组时间段属于的星期数
                if ((sweek & oweekMask) == oweekMask && 
                     offdlTime.get("on_time" + i) != null &&
                     beforeCurrentTime(new String(offdlTime.get("on_time" + i))) &&
                     offdlTime.get("off_time" + i) != null &&
                     afterCurrentTime(new String(offdlTime.get("off_time" + i))))
                {
                    return true;
                }
            }
        }
        return false;
    }
    
    public void setTimeZone(String tz) {
    	Pattern p = Pattern.compile("^(-?)(\\d{1,2})$");
    	Matcher m = p.matcher(tz);
    	
    	if (m.find()) {
    		StringBuilder sb = new StringBuilder();
    		sb.append("GMT");
    		char firstChar = tz.charAt(0);
    		if (firstChar == '-') {
    			sb.append("+").append(m.group(2));
    		} else if (firstChar != '0') {
    			sb.append("-").append(m.group(2));
    		}
    		mAlarmManager.setTimeZone(sb.toString());
    	}
    }
    
    public void setTime(String time) {
    	Pattern p = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})$");
    	Matcher m = p.matcher(time);
    	
    	if (m.find()) {
    	    String timeZone = SysParamManager.getInstance().getTimeZone();
    		String tzStr = (timeZone != null) ? timeZone : "-8";
    		int diffHour = -1;
    		char sign = tzStr.charAt(0);
    		if (sign != '-') {
    			sign = '+';
    			diffHour = Integer.parseInt(tzStr)*2;
    		} else {
    			sign = '-';
    			diffHour = Integer.parseInt(tzStr.substring(1))*2;
    		}
    		
    		Time t = new Time();
    		int year = Integer.parseInt(m.group(1));
    		int month = Integer.parseInt(m.group(2));
    		int day = Integer.parseInt(m.group(3));
    		int hour = Integer.parseInt(m.group(4));
    		int minute = Integer.parseInt(m.group(5));
    		int second = Integer.parseInt(m.group(6));
    		if (sign == '-') {
    			t.set(second, minute, hour-diffHour, day, month-1, year);
    		} else {
    			t.set(second, minute, hour+diffHour, day, month-1, year);
    		}
    		t.normalize(true);
    		
    		StringBuilder sb = new StringBuilder();
    		sb.append("date -s ").append(String.format("%d%02d%02d.%02d%02d%02d", t.year, t.month+1, t.monthDay,
                    t.hour, t.minute, t.second));
            RuntimeExec.getInstance().runRootCmd(sb.toString());
    	}
    }
    
    /*
     * 设置屏幕亮度
     */
    public static void setScreenBright(int bright)
    {
        Settings.System.putInt(PosterMainActivity.INSTANCE.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0);// 设为手动调节亮度
        Uri uri = android.provider.Settings.System.getUriFor("color_brightness");
        android.provider.Settings.System.putInt(PosterMainActivity.INSTANCE.getContentResolver(), "color_brightness", (int) (bright * 2.55));
        PosterMainActivity.INSTANCE.getContentResolver().notifyChange(uri, null);
        Logger.i("亮度设置成功，亮度大小为" + bright);
    }
    
    /*
     * 设置音量
     */
    public void setDeviceVol(int vol)
    {
        AudioManager mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float factor = (float) maxVolume / 100;
        float volume = vol * factor;
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) volume, AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_PLAY_SOUND);
        Logger.i("音量设置成功，音量大小为" + vol);
    }
    
    /*
     * 删除过期文件
     */
    @SuppressLint("DefaultLocale")
    public void deleteExpiredFiles()
    {
        Logger.i("开始清理播出文件");
        HashMap<String, String> normalFileList = ScreenManager.getInstance().getCurrentNormalFilelist();
        HashMap<String, String> urgentFileList = ScreenManager.getInstance().getCurrentEmergencyFilelist();
        HashMap<String, String> fileNameList = new HashMap<String, String>();
        if (FileUtils.getFileList(fileNameList, getProgramPath(), true) == true)
        {
            for (String filename : fileNameList.keySet())
            {
                Logger.i("文件系统中的文件名：" + filename);
                if (normalFileList.containsKey(filename))
                {
                    Logger.i("文件：" + filename + "，本地地址：" + fileNameList.get(filename));
                    Logger.i("文件：" + filename + "属于普通节目列表" + normalFileList.get(filename));
                    if (fileNameList.get(filename).toLowerCase().equals(normalFileList.get(filename).toLowerCase()))
                    {
                        continue;
                    }
                }
                else if (urgentFileList.containsKey(filename))
                {
                    Logger.i("文件：" + filename + "，本地地址：" + fileNameList.get(filename));
                    Logger.i("文件：" + filename + "属于紧急节目列表" + urgentFileList.get(filename));
                    if (fileNameList.get(filename).toLowerCase().equals(urgentFileList.get(filename).toLowerCase()))
                    {
                        continue;
                    }
                }
                
                if (!filename.endsWith(".xml"))
                {
                    File file = new File(fileNameList.get(filename));
                    if (FileUtils.delFile(file))
                        Logger.i("过期文件：" + filename + "已经被删除！");
                    else
                        Logger.i("过期文件：" + filename + "删除失败！");
                }
            }
        }
    }
    
    // /////////////////////////////////////////////////////////////////////////////////////////////////////
    
    public static int dip2px(Context context, int dpValue)
    {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
    
    public static int px2dip(Context context, int pxValue)
    {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }
    
    public static int stringHexToInt(String sring)
    { // 12 ->0x12
        if (sring == null || sring.length() <= 0)
        {
            return 0;
        }
        
        int resut = 0;
        String src = sring.substring(2);
        int len = src.length();
        for (int i = 1; i < (len + 1); i++)
        {
            if ((src.charAt(len - i) >= '0') && (src.charAt(len - i) <= '9'))
            {
                resut += (src.charAt(len - i) - 0x30) << (4 * (i - 1));
            }
            else if ((src.charAt(len - i) >= 'a') && (src.charAt(len - i) <= 'f'))
            {
                resut += (src.charAt(len - i) - 'a' + 0x0A) << (4 * (i - 1));
            }
            else if ((src.charAt(len - i) >= 'A') && (src.charAt(len - i) <= 'F'))
            {
                resut += (src.charAt(len - i) - 'A' + 0x0A) << (4 * (i - 1));
            }
        }
        
        return resut;
    }
    
    @SuppressLint("InlinedApi")
    public static void setSystemBarVisible(Context context, boolean visible)
    {
        if (context instanceof Activity)
        {
            int flag = ((Activity) context).getWindow().getDecorView().getSystemUiVisibility(); // 获取当前SystemUI显示状态
            int fullScreenFlag = 0x00000008; // platform的源码里面新增的常量SYSTEM_UI_FLAG_SHOW_FULLSCREEN.
            
            if (visible) // 显示系统栏
            {
                if ((flag & fullScreenFlag) != 0)
                {
                    ((Activity) context).getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                }
            }
            else
            {
                // 隐藏系统栏
                if ((flag & fullScreenFlag) == 0)
                {
                    ((Activity) context).getWindow().getDecorView().setSystemUiVisibility(flag | fullScreenFlag);
                }
            }
        }
    }
    
    public static void startApplication(Context context, String appPackageName)
    {
        Intent appStartIntent = context.getPackageManager().getLaunchIntentForPackage(appPackageName);
        if (null != appStartIntent)
        {
            context.startActivity(appStartIntent);
        }
        else
        {
            Logger.i(appPackageName + " application didn't found.");
        }
    }
    
    public Bitmap loadPicture(String picFileName, int nPicWidth, int nPicHeight)
    {
        if (!FileUtils.isExist(picFileName))
        {
            return null;
        }
        
        Bitmap srcBmp = null;
        
        try
        {
            // Create the Stream
            InputStream isImgBuff = new FileInputStream(picFileName);
            
            try
            {
                // Set the options for BitmapFactory
                if (isImgBuff != null)
                {
                    MediaInfoRef picInfo = new MediaInfoRef();
                    picInfo.filePath = picFileName;
                    picInfo.containerwidth = nPicWidth;
                    picInfo.containerheight = nPicHeight;
                    srcBmp = BitmapFactory.decodeStream(isImgBuff, null, PosterBaseView.setBitmapOption(picInfo));
                }
            }
            catch (java.lang.OutOfMemoryError e)
            {
                Logger.e("background picture is too big, out of memory!");
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

    public Bitmap combineScreenCap(Bitmap bitmap)
    {
        if (PosterMainActivity.INSTANCE != null) 
        {
        	return PosterMainActivity.INSTANCE.combineScreenCap(bitmap);
        }

        return bitmap;
    }

    public SysOnOffTimeInfo[] getSysOnOffTime() {
        SysOnOffTimeInfo[] info = null;
        ConcurrentHashMap<String, String>  onOffTime = SysParamManager.getInstance().getOnOffTimeParam();
        if (onOffTime != null)
        {
            int group = Integer.parseInt(onOffTime.get("group"));
            if (group != 0)
            {
                info = new SysOnOffTimeInfo[group];
                String[] timearray = null;
                for (int i = 0; i < group; i++)
                {
                    info[i] = new SysOnOffTimeInfo();
                    info[i].week = Integer.parseInt(onOffTime.get("week" + (i + 1)));
                    timearray = onOffTime.get("on_time" + (i + 1)).split(":");
                    info[i].onhour = Integer.parseInt(timearray[0]);
                    info[i].onminute = Integer.parseInt(timearray[1]);
                    info[i].onsecond = Integer.parseInt(timearray[2]);
                    timearray = onOffTime.get("off_time" + (i + 1)).split(":");
                    info[i].offhour = Integer.parseInt(timearray[0]);
                    info[i].offminute = Integer.parseInt(timearray[1]);
                    info[i].offsecond = Integer.parseInt(timearray[2]);
                }
            }
        }
    	    	
    	return info;
    }

    /**
     * 获取当前时间
     * 
     * @return yyyy-MM-dd HH:mm:ss
     */
    public static String getCurrentTime()
    {
        Time time = new Time();
        time.setToNow();
        StringBuilder sb = new StringBuilder();
        sb.append(time.year).append("-");
        sb.append(time.month + 1).append("-");
        sb.append(time.monthDay);
        sb.append(" ");
        sb.append(time.hour).append(":");
        sb.append((time.minute < 10) ? ("0" + time.minute) : time.minute).append(":");
        sb.append((time.second < 10) ? ("0" + time.second) : time.second);
        return sb.toString();
    }
    
    /**
     * 获取当前日期
     * 
     * @return yyyymmdd
     */
    public static String getCurrentDate()
    {
        Time time = new Time();
        time.setToNow();
        StringBuilder sb = new StringBuilder();
        sb.append(time.year);
        sb.append(((time.month + 1) < 10) ? ("0" + (time.month + 1)) : (time.month + 1));
        sb.append(time.monthDay < 10 ? ("0" + time.monthDay) : time.monthDay);
        return sb.toString();
    }
    
    /**
     * 获取昨天的日期
     * 
     * @return yyyymmdd
     */
    public static String getYesterdayDate()
    {
        Time time = new Time();
        time.set(System.currentTimeMillis() - 24 * 3600 * 1000);
        StringBuilder sb = new StringBuilder();
        sb.append(time.year);
        sb.append(((time.month + 1) < 10) ? ("0" + (time.month + 1)) : (time.month + 1));
        sb.append(time.monthDay < 10 ? ("0" + time.monthDay) : time.monthDay);
        return sb.toString();
    }

    public void initLanguage()
    {
        /*
        int languagesetnum = SysParamManager.getInstance().getOsdLang();
        Resources resources = getResources();// 获得res资源对象
        Configuration config = resources.getConfiguration();// 获得设置对象
        DisplayMetrics dm = resources.getDisplayMetrics();// 获得屏幕参数：主要是分辨率，像素等。
        
        switch (languagesetnum)
        {
        case 0:
            if (config.locale != Locale.SIMPLIFIED_CHINESE)
            {
                config.locale = Locale.SIMPLIFIED_CHINESE;
                resources.updateConfiguration(config, dm);
            }
            break;
        case 1:
            if (config.locale != Locale.US)
            {
                config.locale = Locale.US;
                resources.updateConfiguration(config, dm);
            }
            break;
        default:
            break;
        }
        */
    }
    
    public void cancelTimingDel()
    {
        if (mDelPeriodFileTimer != null)
        {
            mDelPeriodFileTimer.cancel();
            mDelPeriodFileTimer = null;
        }
    }
    
    public void startTimingDel()
    {
        cancelTimingDel();
        int deltime = SysParamManager.getInstance().getDelFilePeriodTime();
        if (deltime > 0)
        {
            mDelPeriodFileTimer = new Timer();
            TimerTask task = new TimerTask()
            {
                @Override
                public void run()
                {
                    FileUtils.deleteTimeOutFile();
                }
            };
            mDelPeriodFileTimer.schedule(task, 12000, 24 * 60 * 60 * 1000);
        }
    }
    
    public void cancelTimingUploadLog()
    {
        if (mUploadLogTimer != null)
        {
            mUploadLogTimer.cancel();
            mUploadLogTimer = null;
        }
    }
    
    public void startTimingUploadLog()
    {
        cancelTimingUploadLog();
        mUploadLogTimer = new Timer();
        TimerTask task = new TimerTask()
        {
            @Override
            public void run()
            {
                String plocalpath = getLogFileFullPath(0, 1);
                if (plocalpath == null || !FileUtils.isExist(plocalpath))
                {
                    WsClient.getInstance().postResultBack(XmlCmdInfoRef.CMD_PTL_CPEPLAYLOGFTPUP, 0, 1, "");
                }
                else
                {
                    // 启动FTP上传文件列表
                    FtpFileInfo upldFile = new FtpFileInfo();
                    upldFile.setRemotePath("/logs");
                    upldFile.setLocalPath(plocalpath);
                    List<FtpFileInfo> uploadlist = new ArrayList<FtpFileInfo>();
                    uploadlist.add(upldFile);
                    FtpHelper.getInstance().uploadFileList(uploadlist, new FtpOperationInterface()
                    {
                        @Override
                        public void started(String file, long size)
                        {
                        }
                        
                        @Override
                        public void aborted()
                        {
                        }
                        
                        @Override
                        public void progress(long length)
                        {
                        }
                        
                        @Override
                        public void completed()
                        {
                            String slogname = getLogFileFullPath(0, 1);
                            StringBuilder sbr = new StringBuilder();
                            sbr.append("<FILE>/logs/");
                            sbr.append(slogname);
                            sbr.append("</FILE><VERCODE>0</VERCODE><SIZE>");
                            sbr.append(FileUtils.getFileLength(slogname));
                            sbr.append("</SIZE><TYPE>2</TYPE>");
                            WsClient.getInstance().postResultBack(XmlCmdInfoRef.CMD_PTL_CPEPLAYLOGFTPUP, 0, 0, sbr.toString());
                        }
                        
                        @Override
                        public void failed()
                        {
                        }
                    });
                }
            }
        };
        mUploadLogTimer.schedule(task, 60000, 24 * 60 * 60 * 1000);
    }
    
	public void cancelTimerRunPowerMeter(){
		if (mGetElectricTimer != null)
        {
			mGetElectricTimer.cancel();
			mGetElectricTimer = null;
			ElectricManager.getInstance().stopGetElectric();
        }
	}
	
	public void startTimerRunPowerMeter(){
		cancelTimerRunPowerMeter();
		mGetElectricTimer=new Timer();
		mGetElectricTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				ElectricManager.getInstance().startGetElectric();
			}
		}, 2000, 24*60*60*1000);
	}
    
    /**
     * Returns true if the time represented by this Time object occurs before current time.
     * 
     * @param t
     *            that a given Time object to compare against (Format is "HH:MM:SS")
     * @return true if the given time is less than current time
     */
    public static boolean beforeCurrentTime(String t)
    {
        StringBuilder sb = new StringBuilder();
        Time time = new Time();
        time.setToNow();
        sb.append(time.year);
        sb.append(((time.month + 1) < 10) ? ("0" + (time.month + 1)) : (time.month + 1));
        sb.append((time.monthDay < 10) ? ("0" + time.monthDay) : time.monthDay);
        sb.append("T");
        sb.append(t.replace(":", ""));
        time.parse(sb.toString());
        
        return (time.toMillis(false) <= System.currentTimeMillis());
    }
    
    /**
     * Returns true if the time represented by this Time object occurs after current time.
     * 
     * @param t
     *            that a given Time object to compare against (Format is "HH:MM:SS")
     * @return true if the given time is greater than current time
     */
    public static boolean afterCurrentTime(String t)
    {
        StringBuilder sb = new StringBuilder();
        Time time = new Time();
        time.setToNow();
        sb.append(time.year);
        sb.append(((time.month + 1) < 10) ? ("0" + (time.month + 1)) : (time.month + 1));
        sb.append((time.monthDay < 10) ? ("0" + time.monthDay) : time.monthDay);
        sb.append("T");
        sb.append(t.replace(":", ""));
        time.parse(sb.toString());
        
        return (time.toMillis(false) >= System.currentTimeMillis());
    }
    
    public static int getCurrentSecondsInDay()
    {
    	Time t = new Time();
        t.setToNow();
        return (t.hour*3600)+(t.minute*60)+t.second;
    }
    
    public static int getSecondsInDayByTime(String t)
    {
    	String strTime[] = t.split(":");
    	if (strTime.length != 3)
    	{
    		return -1;
    	}
    	return (Integer.parseInt(strTime[0])*3600)+(Integer.parseInt(strTime[1])*60)+Integer.parseInt(strTime[2]);
    }
    
    /**
     * Compare two Time objects and return a negative number if t1 is less than t2, a positive number if t1 is greater
     * than t2, or 0 if they are equal.
     * 
     * @param t1
     *            first {@code Time} instance to compare (Format is "hh:mm:ss")
     * @param t2
     *            second {@code Time} instance to compare (Format is "hh:mm:ss")
     * @return 0:= >0：> <0:<
     */
    public static int compareTwoTime(String t1, String t2)
    {
        String strTime1[] = t1.split(":");
        String strTime2[] = t2.split(":");
        if (strTime1.length < 3 || strTime2.length < 3)
        {
            Logger.i("The given time format is invaild.");
            return -1;
        }
        Time currentTime = new Time();
        currentTime.setToNow();
        
        Time time1 = new Time();
        time1.set(Integer.parseInt(strTime1[2]), Integer.parseInt(strTime1[1]), Integer.parseInt(strTime1[0]), currentTime.monthDay, currentTime.month, currentTime.year);
        
        Time time2 = new Time();
        time2.set(Integer.parseInt(strTime2[2]), Integer.parseInt(strTime2[1]), Integer.parseInt(strTime2[0]), currentTime.monthDay, currentTime.month, currentTime.year);
        
        return Time.compare(time1, time2);
    }
    
    public static long getTimeMillis(String time)
    {
        StringBuilder sb = new StringBuilder();
        Time t = new Time();
        t.setToNow();
        sb.append(t.year);
        sb.append(((t.month + 1) < 10) ? ("0" + (t.month + 1)) : (t.month + 1));
        sb.append((t.monthDay < 10) ? ("0" + t.monthDay) : t.monthDay);
        sb.append("T");
        sb.append(time.replace(":", ""));
        t.parse(sb.toString());
        return t.toMillis(false);
    }
    
    public int getHwRotation() {
		Object hwRotation = ReflectionUtils.invokeStaticMethod(
				SYSPROP_HWROTATION_CLASS, SYSPROP_HWROTATION_GETMETHOD, new Object[] {
				SYSPROP_HWROTATION, SYSPROP_HWROTATION_DEFAULT}, new Class[] {String.class, int.class});
		if (hwRotation != null) {
			return ((Integer)hwRotation).intValue();
		}
		return -1;
	}
    
    //get the object of YSConfiguration.
    public YSConfiguration getConfiguration(){
        return mConfiguration;
    }
    
    public static boolean isServiceRunning(Context context, String srvName)
    {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningServiceInfo> list = activityManager.getRunningServices(100);
        for (int i = 0; i < list.size(); i++)
        {
            if (srvName.equalsIgnoreCase(list.get(i).service.getClassName()))
            {
                return true;
            }
        }
        return false;
    }
}
